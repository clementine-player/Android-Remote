package de.qspool.clementineremote.ui.search

import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
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
import kotlinx.coroutines.Job
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

    /** Highest search ID seen so far, used to retire older IDs when a new search starts. */
    private var maxSeenId = Int.MIN_VALUE

    /** Cutoff below which late responses from superseded searches are discarded. */
    private var retiredUpToId = Int.MIN_VALUE

    /** IDs whose GlobalSearchStarted query didn't match the active query (late prior search). */
    private val wrongQueryIds = HashSet<Int>()

    /** Monotonic counter invalidating in-flight IO updates from earlier searches. */
    private var searchGeneration = 0

    private var updateJob: Job? = null

    private var openJob: Job? = null

    private val listener = object : OnGlobalSearchResponseListener {
        override fun onStatusChanged(id: Int, status: GlobalSearchStatus) {
            onStatusChanged(id, "", status)
        }

        // Marshal connection-thread callbacks onto Main so search state mutates single-threaded.
        override fun onStatusChanged(id: Int, query: String, status: GlobalSearchStatus) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                handleStatusChanged(id, query, status)
            }
        }

        // Results show as they come; most searches find songs in the library quickly, and radio
        // streams slowly.
        override fun onResultsReceived(id: Int) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                handleResultsReceived(id)
            }
        }
    }

    private fun handleStatusChanged(id: Int, query: String, status: GlobalSearchStatus) {
        maxSeenId = maxOf(maxSeenId, id)
        if (id <= retiredUpToId) return
        // Blacklist searches whose server query reveals they belong to an earlier search() call.
        if (query.isNotEmpty() && query != _state.value.searchedFor) {
            wrongQueryIds.add(id)
            return
        }
        if (id in wrongQueryIds) return
        val current = searchId
        if (current != null && id != current) return
        if (status == GlobalSearchStatus.GlobalSearchStarted) {
            searchId = id
        } else if (status == GlobalSearchStatus.GlobalSearchFinished) {
            update(id, finished = true)
        }
    }

    private fun handleResultsReceived(id: Int) {
        maxSeenId = maxOf(maxSeenId, id)
        if (id <= retiredUpToId || id in wrongQueryIds) return
        val current = searchId
        if (current != null && id != current) return
        update(id, finished = false)
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
        // Retire all previously seen search IDs and cancel in-flight jobs so a late
        // GlobalSearchFinished from a superseded search cannot latch searchId or stop this one.
        searchGeneration++
        updateJob?.cancel()
        openJob?.cancel()
        retiredUpToId = maxOf(retiredUpToId, maxSeenId)
        wrongQueryIds.removeAll { it <= retiredUpToId }
        searchId = null
        _state.value = SearchState(searchedFor = text, searching = true)
        send(ClementineMessageFactory.buildGlobalSearch(text))
    }

    /** Search [id] has more results, or has [finished]: shows them in sections. */
    internal fun update(id: Int, finished: Boolean) {
        if (id <= retiredUpToId) {
            return
        }
        maxSeenId = maxOf(maxSeenId, id)
        searchId = id
        // Cancel older IO parses and verify generation/searchId to block out-of-order overwrites.
        val generation = searchGeneration
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            val query = _state.value.searchedFor.orEmpty()
            val sections = withContext(io) {
                val found = try {
                    candidates(id)
                } catch (_: SQLiteException) {
                    // GlobalSearchDatabaseHelper can reset/clear concurrently on a new search.
                    emptyList()
                }
                SearchSections.of(query, found)
            }
            if (searchId != id || searchGeneration != generation) {
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
        // Guard against double-tap duplicates and stale IO reads landing after seeAll/back/search.
        val expected = _state.value.results ?: return
        openJob?.cancel()
        openJob = viewModelScope.launch {
            val below = withContext(io) {
                try {
                    SongBrowser { newQuery(id) }.level(item)
                } catch (_: SQLiteException) {
                    null
                }
            } ?: return@launch
            _state.update {
                if (searchId == id && it.results === expected) it.copy(results = it.results.opened(below)) else it
            }
        }
    }

    /** Shows all of [section]. */
    fun seeAll(section: SearchSection) {
        openJob?.cancel()
        _state.update { it.copy(results = it.results?.seeAll(section)) }
    }

    /** Closes the page shown; false at the sections. */
    fun back(): Boolean {
        openJob?.cancel()
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
                try {
                    pages.map { page ->
                        if (page is SearchPage.Opened) SearchPage.Opened(SongBrowser { newQuery(id) }.level(page.level.opened)) else page
                    }
                } catch (_: SQLiteException) {
                    null
                }
            } ?: return@launch
            _state.update {
                if (searchId == id) it.copy(results = it.results?.copy(pages = reread)) else it
            }
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
                try {
                    SongBrowser { newQuery(id) }.songs(items).mapNotNull { it.url?.let(songFromUrl) }
                } catch (_: SQLiteException) {
                    emptyList()
                }
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
