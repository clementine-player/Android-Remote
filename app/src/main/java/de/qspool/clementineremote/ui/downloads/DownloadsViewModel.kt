package de.qspool.clementineremote.ui.downloads

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.AsyncTask
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.downloader.ClementineSongDownloader
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.library.LibraryDatabaseHelper
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.backend.player.MyPlaylist
import de.qspool.clementineremote.utils.Utilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList

/** A song downloaded, to play. */
data class DownloadedSong(val title: String, val artist: String, val uri: Uri)

/** A download, running or done. */
data class Download(
    val id: Int,
    val item: DownloadItem,
    val title: String,
    val subtitle: String,
    /** From 0 to 100. */
    val progress: Int,
    /** Downloaded of the total, and the speed while running. */
    val size: String,
    val running: Boolean,
    val songs: List<DownloadedSong>,
)

data class DownloadsState(
    val running: List<Download> = emptyList(),
    val finished: List<Download> = emptyList(),
    /** Free space where downloads go. */
    val freeSpace: String = "",
    /** Downloads only run on Wi-Fi. */
    val wifiOnly: Boolean = false,
)

/** The downloads to this phone, as the download manager has them, read four times a second. */
class DownloadsViewModel(
    private val downloads: () -> List<ClementineSongDownloader> = { DownloadManager.getInstance(App.getApp()).allDownloaders },
    private val freeSpace: () -> Long = { Utilities.getFreeSpaceExternal().toLong() },
    /** The library synced from Clementine, if there is one; closed after use. */
    private val library: () -> SQLiteDatabase? = ::openLibrary,
    private val playlists: () -> List<MyPlaylist> = { App.Clementine.playlistManager.allPlaylists },
    private val startDownload: (ClementineMessage) -> Unit = { DownloadManager.getInstance().addJob(it) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _suggestions = MutableStateFlow(Suggestions())

    /** What to download, for when nothing has been: see [DownloadSuggestions]. */
    val suggestions: StateFlow<Suggestions> = _suggestions.asStateFlow()

    val state: StateFlow<DownloadsState> = flow {
        while (true) {
            emit(snapshot())
            delay(REFRESH_MILLIS)
        }
    }.flowOn(Dispatchers.IO) // Free space is read from the disk.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsState())

    private fun snapshot(): DownloadsState {
        val all = downloads().map(::download)
        return DownloadsState(
            running = all.filter { it.running },
            finished = all.filterNot { it.running },
            freeSpace = Utilities.humanReadableBytes(freeSpace(), true),
            wifiOnly = App.getPreferences().getBoolean(SharedPreferencesKeys.SP_WIFI_ONLY, false),
        )
    }

    private fun download(downloader: ClementineSongDownloader): Download {
        val manager = DownloadManager.getInstance(App.getApp())
        val running = downloader.status != AsyncTask.Status.FINISHED
        var size = Utilities.humanReadableBytes(downloader.totalDownloaded.toLong(), true) + " / " +
            Utilities.humanReadableBytes(downloader.totalFileSize.toLong(), true)
        if (downloader.status == AsyncTask.Status.RUNNING) {
            size += " (" + Utilities.humanReadableBytes(downloader.downloadSpeedPerSecond.toLong(), true) + "/s)"
        }
        return Download(
            id = downloader.id,
            item = downloader.item,
            title = manager.getTitleForItem(downloader),
            subtitle = manager.getSubtitleForItem(downloader),
            progress = downloader.downloadStatus.progress.toInt(),
            size = size,
            running = running,
            songs = downloader.downloadedSongs.map { DownloadedSong(it.song.title, it.song.artist, it.uri) },
        )
    }

    /** Stops a running download, or forgets a finished one. Returns whether it was running. */
    fun cancel(id: Int): Boolean {
        val downloader = downloads().firstOrNull { it.id == id } ?: return false
        return if (downloader.status == AsyncTask.Status.RUNNING) {
            downloader.cancel(false)
            true
        } else {
            DownloadManager.getInstance(App.getApp()).removeDownloader(id)
            false
        }
    }

    /** Reads the suggestions again: the library or the playlists may have changed. */
    fun loadSuggestions() {
        val playlists = DownloadSuggestions.playlists(playlists())
        viewModelScope.launch {
            val (albums, mostPlayed) = withContext(io) {
                library()?.use { DownloadSuggestions.albums(it) } ?: (emptyList<AlbumSuggestion>() to false)
            }
            _suggestions.value = Suggestions(albums, mostPlayed, playlists)
        }
    }

    fun download(album: AlbumSuggestion) {
        viewModelScope.launch {
            val urls = withContext(io) { library()?.use { DownloadSuggestions.urls(it, album) }.orEmpty() }
            if (urls.isNotEmpty()) {
                startDownload(ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.Urls, LinkedList(urls)))
            }
        }
    }

    fun download(playlist: PlaylistSuggestion) {
        startDownload(ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.APlaylist, playlist.id))
    }

    private companion object {
        const val REFRESH_MILLIS = 250L

        fun openLibrary(): SQLiteDatabase? {
            val file = LibraryDatabaseHelper().libraryDb
            return if (file.exists()) {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            } else {
                null
            }
        }
    }
}
