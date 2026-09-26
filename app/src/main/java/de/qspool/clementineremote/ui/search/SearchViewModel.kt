package de.qspool.clementineremote.ui.search

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import de.qspool.clementineremote.backend.database.SongSelectItem
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchManager
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchQuery
import de.qspool.clementineremote.backend.listener.OnGlobalSearchResponseListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.GlobalSearchStatus
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata
import de.qspool.clementineremote.ui.browse.BrowseLevel
import de.qspool.clementineremote.ui.browse.ItemKind
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
import java.util.LinkedList

data class SearchState(
    /** What was searched for last; null before the first search. */
    val searchedFor: String? = null,
    /** Clementine is still searching. */
    val searching: Boolean = false,
    /** The levels of the results opened, from the top down; the last is shown. */
    val levels: List<BrowseLevel> = emptyList(),
) {
    val shown: BrowseLevel? get() = levels.lastOrNull()
}

/**
 * Clementine's global search: in its library and the internet services it's set up for. The
 * results arrive in a database, browsed level by level (source, artist, album, song, as the
 * library grouping setting says).
 */
class SearchViewModel(
    private val send: (ClementineMessage) -> Unit = RemoteRepository::send,
    private val newQuery: (Int) -> DynamicSongQuery = { id -> GlobalSearchQuery(App.getApp(), id) },
    private val songFromUrl: (String) -> SongMetadata? = { url ->
        GlobalSearchManager.getInstance().request?.getSongFromUrl(url)
    },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val listenToClementine: Boolean = true,
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
                finished(id)
            }
        }

        override fun onResultsReceived(id: Int) {}
    }

    /** The grouping and sorting settings change what the levels are. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val id = searchId
        if (id != null && (key == SharedPreferencesKeys.SP_LIBRARY_GROUPING || key == SharedPreferencesKeys.SP_LIBRARY_SORTING)) {
            finished(id)
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
        _state.value = SearchState(searchedFor = text, searching = true)
        send(ClementineMessageFactory.buildGlobalSearch(text))
    }

    /** Clementine has finished search [id]: shows its top level. */
    internal fun finished(id: Int) {
        searchId = id
        viewModelScope.launch {
            val top = withContext(io) { SongBrowser { newQuery(id) }.level(null) }
            _state.update { it.copy(searching = false, levels = listOf(top)) }
        }
    }

    /** Opens an item: the level below it, or for a song, adds it to the playlist. */
    fun open(item: SongSelectItem) {
        val shown = _state.value.shown ?: return
        if (shown.kind == ItemKind.SONG) {
            addToPlaylist(listOf(item))
            return
        }
        val id = searchId ?: return
        viewModelScope.launch {
            val below = withContext(io) { SongBrowser { newQuery(id) }.level(item) }
            _state.update { it.copy(levels = it.levels + below) }
        }
    }

    /** Goes up a level; false at the top. */
    fun back(): Boolean {
        if (_state.value.levels.size <= 1) {
            return false
        }
        _state.update { it.copy(levels = it.levels.dropLast(1)) }
        return true
    }

    /** Adds the songs of [items] (songs, or whatever groups them) to the playlist playing. */
    fun addToPlaylist(items: List<SongSelectItem>) {
        val id = searchId ?: return
        viewModelScope.launch {
            val songs = withContext(io) {
                SongBrowser { newQuery(id) }.songs(items).mapNotNull { it.url?.let(songFromUrl) }
            }
            if (songs.isEmpty()) {
                return@launch
            }
            send(ClementineMessageFactory.buildInsertSongs(
                App.Clementine.playlistManager.activePlaylistId, LinkedList(songs)))
            _added.trySend(songs.size)
        }
    }
}
