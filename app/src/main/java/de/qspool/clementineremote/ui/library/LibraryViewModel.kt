package de.qspool.clementineremote.ui.library

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.AddAction
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementineLibraryDownloader
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import de.qspool.clementineremote.backend.database.SongSelectItem
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.elements.DownloaderResult
import de.qspool.clementineremote.backend.library.LibraryDatabaseHelper
import de.qspool.clementineremote.backend.library.LibraryQuery
import de.qspool.clementineremote.backend.library.LibrarySearchQuery
import de.qspool.clementineremote.backend.listener.OnLibraryDownloadListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.ui.browse.BrowseLevel
import de.qspool.clementineremote.ui.browse.ItemKind
import de.qspool.clementineremote.ui.browse.SongBrowser
import de.qspool.clementineremote.ui.search.SearchCandidate
import de.qspool.clementineremote.ui.search.SearchResults
import de.qspool.clementineremote.ui.search.SearchSection
import de.qspool.clementineremote.ui.search.SearchSections
import de.qspool.clementineremote.ui.search.librarySearchCandidates
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList

/** Where the library is. */
sealed interface LibraryStatus {
    /** Not on this phone yet. */
    data object Missing : LibraryStatus

    /**
     * Syncing from Clementine: bytes so far of the total, when known. The library already on
     * the phone, if any, can be browsed meanwhile.
     */
    data class Syncing(val bytes: Long, val total: Int) : LibraryStatus

    /** Received, and being indexed for searching. */
    data object Optimizing : LibraryStatus

    data object Ready : LibraryStatus
}

data class LibraryState(
    val status: LibraryStatus = LibraryStatus.Missing,
    /** The levels opened, from the top down; the last is shown. */
    val levels: List<BrowseLevel> = emptyList(),
    /** What's searched for; empty when not searching. */
    val filter: String = "",
    /** While searching, what was found, and the pages opened from it. */
    val search: SearchResults? = null,
) {
    val shown: BrowseLevel? get() = levels.lastOrNull()
}

/**
 * The library Clementine sends, browsed level by level as its grouping setting says (artist,
 * album, song by default). Searching it shows what matched in sections, as the Search screen
 * shows Clementine's results. The database is queried off the main thread. The library itself is
 * synced from Clementine when connected, and again on request.
 */
class LibraryViewModel(
    private val send: (ClementineMessage) -> Unit = RemoteRepository::send,
    private val newQuery: () -> DynamicSongQuery = { LibraryQuery(App.getApp()) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Starts syncing the library from Clementine, telling [OnLibraryDownloadListener] how it goes. */
    private val startSync: (OnLibraryDownloadListener) -> ClementineLibraryDownloader? = { listener ->
        ClementineLibraryDownloader(App.getApp()).apply {
            addOnLibraryDownloadListener(listener)
            startDownload(ClementineMessage.getMessage(MsgType.GET_LIBRARY))
        }
    },
    /** Whether connected to Clementine, so there's a library to sync. */
    private val connected: () -> Boolean = { App.ClementineConnection?.isConnected == true },
    /** Whether this Clementine's library is on the phone, removing another Clementine's. */
    private val libraryExists: () -> Boolean = {
        LibraryDatabaseHelper().run {
            removeDatabaseIfFromOtherClementine()
            databaseExists()
        }
    },
    /** Browses the library as its search results open: album artist, album, song. */
    newSearchQuery: () -> DynamicSongQuery = { LibrarySearchQuery(App.getApp()) },
    /** The songs of the library matching a search. */
    private val searchLibrary: (String) -> List<SearchCandidate> = { text ->
        LibraryDatabaseHelper().openDatabase(SQLiteDatabase.OPEN_READONLY).use { librarySearchCandidates(it, text) }
    },
    /** How long typing must pause before searching, in milliseconds. */
    private val searchDelay: Long = 150,
    /** Whether Clementine can queue songs to play next ([AddAction.PLAY_NEXT]). */
    val canPlayNext: StateFlow<Boolean> = RemoteRepository.canEnqueueNext,
    /** Empties a playlist, by its id. */
    private val clearPlaylist: (Int) -> Unit = { App.Clementine.playlistManager.clearPlaylist(it) },
) : ViewModel() {

    private val browser = SongBrowser(newQuery)

    private val searchBrowser = SongBrowser(newSearchQuery)

    private var searching: Job? = null

    private val _state = MutableStateFlow(LibraryState())

    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _messages = Channel<Message>(Channel.BUFFERED)

    /** Things to tell the user once, such as songs added to the playlist. */
    val messages: Flow<Message> = _messages.receiveAsFlow()

    /** A message for the user. */
    sealed interface Message {
        /** Some songs were added to the playlist. */
        data class Added(val count: Int) : Message

        /** The library couldn't be synced, for [reason]. */
        data class SyncFailed(@StringRes val reason: Int) : Message
    }

    private var syncing = false

    private var downloader: ClementineLibraryDownloader? = null

    private val syncListener = object : OnLibraryDownloadListener {
        override fun OnProgressUpdate(progress: Long, total: Int) {
            _state.update { it.copy(status = LibraryStatus.Syncing(progress, total)) }
        }

        override fun OnOptimizeLibrary() {
            _state.update { it.copy(status = LibraryStatus.Optimizing) }
        }

        override fun OnLibraryDownloadFinished(result: DownloaderResult) {
            syncing = false
            downloader = null
            val succeeded = result.result == DownloaderResult.DownloadResult.SUCCESSFUL
            // A failed sync leaves the library that was on the phone, if there was one.
            refresh()
            if (!succeeded) {
                _messages.trySend(Message.SyncFailed(result.messageStringId))
            }
        }
    }

    /** The grouping and sorting settings change what the levels are. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SharedPreferencesKeys.SP_LIBRARY_GROUPING || key == SharedPreferencesKeys.SP_LIBRARY_SORTING) {
            reload()
        }
    }

    init {
        App.getPreferences().registerOnSharedPreferenceChangeListener(settingsListener)
        reload()
        // The library is cheap to fetch, so it's kept up to date without being asked.
        if (connected()) {
            sync()
        }
    }

    override fun onCleared() {
        App.getPreferences().unregisterOnSharedPreferenceChangeListener(settingsListener)
        downloader?.removeOnLibraryDownloadListener(syncListener)
    }

    /** Shows the top level again, from the database on this phone if there is one. */
    fun reload() {
        viewModelScope.launch {
            val top = withContext(io) {
                if (libraryExists()) browser.level(null) else null
            }
            _state.update {
                it.copy(
                    status = when {
                        // Still syncing: its progress stays shown.
                        syncing -> it.status
                        top == null -> LibraryStatus.Missing
                        else -> LibraryStatus.Ready
                    },
                    levels = listOfNotNull(top),
                )
            }
        }
    }

    /**
     * Syncs the library from Clementine, replacing the one on this phone once it's complete; until
     * then, the one on the phone can still be browsed.
     */
    fun sync() {
        if (syncing) {
            return
        }
        syncing = true
        _state.update { it.copy(status = LibraryStatus.Syncing(0, 0)) }
        downloader = startSync(syncListener)
    }

    /**
     * Shows the library on the phone again, after a sync: the levels open stay open, re-read
     * from the new library, or the top level shows if they're gone.
     */
    private fun refresh() {
        viewModelScope.launch {
            while (true) {
                val opened = _state.value.levels.map { it.opened }
                val levels = withContext(io) {
                    if (!libraryExists()) {
                        emptyList()
                    } else {
                        opened.map { browser.level(it) }.takeIf { it.isNotEmpty() && it.last().items.isNotEmpty() }
                            ?: listOf(browser.level(null))
                    }
                }
                // A level was opened or closed while reading: read what's shown now instead,
                // rather than undo it.
                if (_state.value.levels.map { it.opened } != opened) {
                    continue
                }
                _state.update {
                    it.copy(status = if (levels.isEmpty()) LibraryStatus.Missing else LibraryStatus.Ready, levels = levels)
                }
                // What's searched for is searched for again, in the new library.
                search(_state.value.filter, wait = false)
                return@launch
            }
        }
    }

    /**
     * Opens an item: the level below it, or for a song, adds it to the playlist, and plays it if
     * Clementine isn't playing, as double-clicking a song in Clementine does.
     */
    fun open(item: SongSelectItem) {
        val shown = _state.value.shown ?: return
        if (shown.kind == ItemKind.SONG) {
            addToPlaylist(listOf(item), AddAction.PLAY_IF_STOPPED)
            return
        }
        viewModelScope.launch {
            val below = withContext(io) { browser.level(item) }
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

    /**
     * Searches the whole library for [text], as it's typed, showing what matched in sections; an
     * empty [text] shows the library again.
     */
    fun setFilter(text: String) {
        _state.update { it.copy(filter = text) }
        search(text, wait = true)
    }

    private fun search(text: String, wait: Boolean) {
        searching?.cancel()
        if (text.isBlank()) {
            _state.update { it.copy(search = null) }
            return
        }
        searching = viewModelScope.launch {
            if (wait && searchDelay > 0) {
                delay(searchDelay)
            }
            val sections = withContext(io) {
                // No library, or a search the index can't take, finds nothing.
                val found = runCatching { if (libraryExists()) searchLibrary(text) else emptyList() }
                SearchSections.of(text, found.getOrDefault(emptyList()))
            }
            _state.update { it.copy(search = SearchResults(sections)) }
        }
    }

    /**
     * Opens a search result: the level below an artist or album, or for a song, adds it to the
     * playlist, and plays it if Clementine isn't playing, as [open] does.
     */
    fun openResult(item: SongSelectItem) {
        if (item.level == SEARCH_SONG_LEVEL) {
            addResults(listOf(item), AddAction.PLAY_IF_STOPPED)
            return
        }
        viewModelScope.launch {
            val below = withContext(io) { searchBrowser.level(item) }
            _state.update { it.copy(search = it.search?.opened(below)) }
        }
    }

    /** Shows all of a [section] of the search results. */
    fun seeAll(section: SearchSection) {
        _state.update { it.copy(search = it.search?.seeAll(section)) }
    }

    /** Closes the search results' page shown; false at their sections. */
    fun backInResults(): Boolean {
        val search = _state.value.search?.back() ?: return false
        _state.update { it.copy(search = search) }
        return true
    }

    /** Adds the songs of search results [items] to the playlist playing, as [addToPlaylist] does. */
    fun addResults(items: List<SongSelectItem>, action: AddAction = AddAction.APPEND) =
        add(items, action) { searchBrowser.songs(items).mapNotNull { it.url } }

    /** Downloads the songs of search results [items] to this phone. */
    fun downloadResults(items: List<SongSelectItem>) = download { searchBrowser.songs(items).mapNotNull { it.url } }

    /**
     * Adds the songs of [items] (songs, or whatever groups them) to the playlist playing, doing
     * [action]. A new playlist is named after the first of them.
     */
    fun addToPlaylist(items: List<SongSelectItem>, action: AddAction = AddAction.APPEND) =
        add(items, action) { songUrls(items) }

    private fun add(items: List<SongSelectItem>, action: AddAction, urls: () -> List<String>) {
        viewModelScope.launch {
            val urls = withContext(io) { urls() }
            if (urls.isEmpty()) {
                return@launch
            }
            val playlist = App.Clementine.playlistManager.activePlaylistId
            if (action == AddAction.REPLACE) {
                clearPlaylist(playlist)
            }
            send(action.insert(playlist, App.Clementine.state == Clementine.State.PLAY, items.first().listTitle) {
                addAllUrls(urls)
            })
            _messages.trySend(Message.Added(urls.size))
        }
    }

    /** Downloads the songs of [items] to this phone. */
    fun downloadSongs(items: List<SongSelectItem>) = download { songUrls(items) }

    private fun download(urls: () -> List<String>) {
        viewModelScope.launch {
            val urls = withContext(io) { urls() }
            if (urls.isNotEmpty()) {
                DownloadManager.getInstance().addJob(
                    ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.Urls, LinkedList(urls)))
            }
        }
    }

    private fun songUrls(items: List<SongSelectItem>) = browser.songs(items).mapNotNull { it.url }

    private companion object {
        /** Search results are browsed by album artist, album and title. */
        const val SEARCH_SONG_LEVEL = 2
    }
}
