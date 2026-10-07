package de.qspool.clementineremote.ui.search

import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.AddAction
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import de.qspool.clementineremote.backend.database.SongSelectItem
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchDatabaseHelper
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchManager
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchQuery
import de.qspool.clementineremote.backend.listener.OnGlobalSearchResponseListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.GlobalSearchStatus
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata
import de.qspool.clementineremote.ui.browse.SongBrowser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchState(
    /** What was searched for last; null before the first search. */
    val searchedFor: String? = null,
    /** Clementine is still searching. */
    val searching: Boolean = false,
    /** The results so far, and the pages opened from them; null before any arrive. */
    val results: SearchResults? = null,
)

/**
 * Clementine's global search: in its library and the internet services it's set up for. The
 * results arrive in a database, and show in sections by what matched (see [SearchSections]) as
 * they come. An artist or album of them opens to its albums or songs.
 */
class SearchViewModel(
    private val send: (ClementineMessage) -> Unit = RemoteRepository::send,
    private val newQuery: (Int) -> DynamicSongQuery = { id -> GlobalSearchQuery(App.getApp(), id) },
    /** The songs search [id] found so far. */
    private val candidates: (Int) -> List<SearchCandidate> = { id ->
        GlobalSearchDatabaseHelper(App.getApp()).readableDatabase.use { globalSearchCandidates(it, id) }
    },
    private val songFromUrl: (String) -> SongMetadata? = { url ->
        GlobalSearchManager.getInstance().request?.getSongFromUrl(url)
    },
    /** A search provider's icon, by its name. */
    val icon: (String) -> Bitmap? = { name ->
        GlobalSearchManager.getInstance().globalSearchProviderIconStore.getProviderIcon(name)
    },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val listenToClementine: Boolean = true,
    /** Whether Clementine can queue songs to play next ([AddAction.PLAY_NEXT]). */
    val canPlayNext: StateFlow<Boolean> = RemoteRepository.canEnqueueNext,
    /** Empties a playlist, by its id. */
    private val clearPlaylist: (Int) -> Unit = { App.Clementine.playlistManager.clearPlaylist(it) },
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())

    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _added = Channel<Int>(Channel.BUFFERED)

    /** How many songs were added to the playlist, to tell the user. */
    val added: Flow<Int> = _added.receiveAsFlow()

    /** The search whose results are shown. */
    private var searchId: Int? = null

    private val listener = object : OnGlobalSearchResponseListener {
        override fun onStatusChanged(id: Int, status: GlobalSearchStatus) {
            if (status == GlobalSearchStatus.GlobalSearchFinished) {
                update(id, finished = true)
            }
        }

        // Results show as they come; most searches find songs in the library quickly, and radio
        // streams slowly.
        override fun onResultsReceived(id: Int) = update(id, finished = false)
    }

    /** The sorting setting changes the order of an opened artist or album. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SharedPreferencesKeys.SP_LIBRARY_SORTING) {
            reopen()
        }
    }

    init {
        if (listenToClementine) {
            GlobalSearchManager.getInstance().addOnGlobalSearchResponseListerner(listener)
        }
        App.getPreferences().registerOnSharedPreferenceChangeListener(settingsListener)
    }

    override fun onCleared() {
        if (listenToClementine) {
            GlobalSearchManager.getInstance().removeOnGlobalSearchResponseListerner(listener)
        }
        App.getPreferences().unregisterOnSharedPreferenceChangeListener(settingsListener)
    }

    /** Asks Clementine to search for [query]. */
    fun search(query: String) {
        val text = query.trim()
        if (text.isEmpty()) {
            return
        }
        searchId = null
        _state.value = SearchState(searchedFor = text, searching = true)
        send(ClementineMessageFactory.buildGlobalSearch(text))
    }

    /** Search [id] has more results, or has [finished]: shows them in sections. */
    internal fun update(id: Int, finished: Boolean) {
        searchId = id
        viewModelScope.launch {
            val query = _state.value.searchedFor.orEmpty()
            val sections = withContext(io) { SearchSections.of(query, candidates(id)) }
            if (searchId != id) {
                return@launch
            }
            _state.update {
                it.copy(
                    searching = it.searching && !finished,
                    results = it.results?.copy(sections = sections) ?: SearchResults(sections),
                )
            }
        }
    }

    /**
     * Opens a result: the level below an artist or album, or for a song, adds it to the playlist,
     * and plays it if Clementine isn't playing, as double-clicking a song in Clementine does.
     */
    fun open(item: SongSelectItem) {
        if (item.level == SONG_LEVEL) {
            addToPlaylist(listOf(item), AddAction.PLAY_IF_STOPPED)
            return
        }
        val id = searchId ?: return
        viewModelScope.launch {
            val below = withContext(io) { SongBrowser { newQuery(id) }.level(item) }
            _state.update { it.copy(results = it.results?.opened(below)) }
        }
    }

    /** Shows all of [section]. */
    fun seeAll(section: SearchSection) {
        _state.update { it.copy(results = it.results?.seeAll(section)) }
    }

    /** Closes the page shown; false at the sections. */
    fun back(): Boolean {
        val results = _state.value.results?.back() ?: return false
        _state.update { it.copy(results = results) }
        return true
    }

    /** Reads the opened artists and albums again, sorted anew. */
    private fun reopen() {
        val id = searchId ?: return
        viewModelScope.launch {
            val pages = _state.value.results?.pages ?: return@launch
            val reread = withContext(io) {
                pages.map { page ->
                    if (page is SearchPage.Opened) SearchPage.Opened(SongBrowser { newQuery(id) }.level(page.level.opened)) else page
                }
            }
            _state.update { it.copy(results = it.results?.copy(pages = reread)) }
        }
    }

    /**
     * Adds the songs of [items] (songs, or whatever groups them) to the playlist playing, doing
     * [action]. A new playlist is named after the first of them.
     */
    fun addToPlaylist(items: List<SongSelectItem>, action: AddAction = AddAction.APPEND) {
        val id = searchId ?: return
        viewModelScope.launch {
            val songs = withContext(io) {
                SongBrowser { newQuery(id) }.songs(items).mapNotNull { it.url?.let(songFromUrl) }
            }
            if (songs.isEmpty()) {
                return@launch
            }
            val playlist = App.Clementine.playlistManager.activePlaylistId
            if (action == AddAction.REPLACE) {
                clearPlaylist(playlist)
            }
            send(action.insert(playlist, App.Clementine.state == Clementine.State.PLAY, items.first().listTitle) {
                addAllSongs(songs)
            })
            _added.trySend(songs.size)
        }
    }

    private companion object {
        /** Results are browsed by where they came from, album artist, album and title. */
        const val SONG_LEVEL = 3
    }
}
