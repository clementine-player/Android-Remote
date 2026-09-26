package de.qspool.clementineremote.ui.library

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.ClementineLibraryDownloader
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import de.qspool.clementineremote.backend.database.SongSelectItem
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.elements.DownloaderResult
import de.qspool.clementineremote.backend.library.LibraryDatabaseHelper
import de.qspool.clementineremote.backend.library.LibraryQuery
import de.qspool.clementineremote.backend.listener.OnLibraryDownloadListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
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

/** Where the library is. */
sealed interface LibraryStatus {
    /** Not on this phone yet. */
    data object Missing : LibraryStatus

    /** Downloading from Clementine: bytes so far of the total, when known. */
    data class Downloading(val bytes: Long, val total: Int) : LibraryStatus

    /** Downloaded, and being indexed for searching. */
    data object Optimizing : LibraryStatus

    data object Ready : LibraryStatus
}

data class LibraryState(
    val status: LibraryStatus = LibraryStatus.Missing,
    /** The levels opened, from the top down; the last is shown. */
    val levels: List<BrowseLevel> = emptyList(),
    val filter: String = "",
) {
    val shown: BrowseLevel? get() = levels.lastOrNull()
}

/**
 * The library Clementine sends, browsed level by level as its grouping setting says (artist,
 * album, song by default). The database is queried off the main thread; the library itself is
 * downloaded from Clementine on request.
 */
class LibraryViewModel(
    private val send: (ClementineMessage) -> Unit = RemoteRepository::send,
    private val newQuery: () -> DynamicSongQuery = { LibraryQuery(App.getApp()) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Whether this Clementine's library is on the phone, removing another Clementine's. */
    private val libraryExists: () -> Boolean = {
        LibraryDatabaseHelper().run {
            removeDatabaseIfFromOtherClementine()
            databaseExists()
        }
    },
) : ViewModel() {

    private val browser = SongBrowser(newQuery)

    private val _state = MutableStateFlow(LibraryState())

    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _messages = Channel<Message>(Channel.BUFFERED)

    /** Things to tell the user once, such as songs added to the playlist. */
    val messages: Flow<Message> = _messages.receiveAsFlow()

    /** A message for the user. */
    sealed interface Message {
        /** Some songs were added to the playlist. */
        data class Added(val count: Int) : Message

        /** The library couldn't be downloaded, for [reason]. */
        data class DownloadFailed(@StringRes val reason: Int) : Message
    }

    private var downloader: ClementineLibraryDownloader? = null

    private val downloadListener = object : OnLibraryDownloadListener {
        override fun OnProgressUpdate(progress: Long, total: Int) {
            _state.update { it.copy(status = LibraryStatus.Downloading(progress, total)) }
        }

        override fun OnOptimizeLibrary() {
            _state.update { it.copy(status = LibraryStatus.Optimizing) }
        }

        override fun OnLibraryDownloadFinished(result: DownloaderResult) {
            downloader = null
            if (result.result == DownloaderResult.DownloadResult.SUCCESSFUL) {
                reload()
            } else {
                _state.update { it.copy(status = LibraryStatus.Missing) }
                _messages.trySend(Message.DownloadFailed(result.messageStringId))
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
    }

    override fun onCleared() {
        App.getPreferences().unregisterOnSharedPreferenceChangeListener(settingsListener)
        downloader?.removeOnLibraryDownloadListener(downloadListener)
    }

    /** Shows the top level again, from the database on this phone if there is one. */
    fun reload() {
        viewModelScope.launch {
            val top = withContext(io) {
                if (libraryExists()) level(null, _state.value.filter) else null
            }
            _state.update {
                it.copy(
                    status = if (top == null) LibraryStatus.Missing else LibraryStatus.Ready,
                    levels = listOfNotNull(top),
                )
            }
        }
    }

    /** Downloads the library from Clementine, replacing the one on this phone. */
    fun download() {
        if (downloader != null) {
            return
        }
        _state.update { it.copy(status = LibraryStatus.Downloading(0, 0), levels = emptyList()) }
        downloader = ClementineLibraryDownloader(App.getApp()).apply {
            addOnLibraryDownloadListener(downloadListener)
            startDownload(ClementineMessage.getMessage(MsgType.GET_LIBRARY))
        }
    }

    /** Opens an item: the level below it, or for a song, adds it to the playlist. */
    fun open(item: SongSelectItem) {
        val shown = _state.value.shown ?: return
        if (shown.kind == ItemKind.SONG) {
            addToPlaylist(listOf(item))
            return
        }
        viewModelScope.launch {
            val below = withContext(io) { level(item, _state.value.filter) }
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

    /** Shows only items matching [text], at the level shown. */
    fun setFilter(text: String) {
        _state.update { it.copy(filter = text) }
        val shown = _state.value.shown ?: return
        viewModelScope.launch {
            val filtered = withContext(io) { level(shown.opened, text) }
            _state.update { it.copy(levels = it.levels.dropLast(1) + filtered) }
        }
    }

    /** Adds the songs of [items] (songs, or whatever groups them) to the playlist playing. */
    fun addToPlaylist(items: List<SongSelectItem>) {
        viewModelScope.launch {
            val urls = withContext(io) { songUrls(items) }
            if (urls.isEmpty()) {
                return@launch
            }
            send(ClementineMessageFactory.buildInsertUrl(
                App.Clementine.playlistManager.activePlaylistId, LinkedList(urls)))
            _messages.trySend(Message.Added(urls.size))
        }
    }

    /** Downloads the songs of [items] to this phone. */
    fun downloadSongs(items: List<SongSelectItem>) {
        viewModelScope.launch {
            val urls = withContext(io) { songUrls(items) }
            if (urls.isNotEmpty()) {
                DownloadManager.getInstance().addJob(
                    ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.Urls, LinkedList(urls)))
            }
        }
    }

    private fun level(opened: SongSelectItem?, filter: String) = browser.level(opened, filter)

    private fun songUrls(items: List<SongSelectItem>) = browser.songs(items).mapNotNull { it.url }
}
