package de.qspool.clementineremote.ui.downloads

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.BackgroundTask
import de.qspool.clementineremote.backend.downloader.ClementineSongDownloader
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.downloader.StoredSong
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
    /** The songs on this phone, downloaded now or before, by folder and file name. */
    val onPhone: List<DownloadedSong> = emptyList(),
    /** Whether the songs on this phone have been read, so there may really be none. */
    val loaded: Boolean = true,
)

/**
 * The downloads to this phone, as the download manager has them, read four times a second, and
 * the songs saved on it, read again each time a download finishes.
 */
class DownloadsViewModel(
    private val downloads: () -> List<ClementineSongDownloader> = { DownloadManager.getInstance(App.getApp()).allDownloaders },
    /** The songs where downloads are saved, whenever they were downloaded. */
    private val storedSongs: () -> List<StoredSong> = { DownloadManager.getInstance(App.getApp()).storedSongs },
    /** Free space where downloads go, in bytes, or a negative number if it can't be told. */
    private val freeSpace: () -> Long = { DownloadManager.getInstance(App.getApp()).freeSpace },
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
        var onPhone = emptyList<DownloadedSong>()
        // The finished downloads when the songs were last read; null until they are.
        var finishedWhenRead: Set<Int>? = null
        while (true) {
            val all = downloads().map(::download)
            val finished = all.filterNot { it.running }.map { it.id }.toSet()
            if (finished != finishedWhenRead) {
                onPhone = readOnPhone()
                finishedWhenRead = finished
            }
            emit(snapshot(all, onPhone))
            delay(REFRESH_MILLIS)
        }
    }.flowOn(Dispatchers.IO) // Free space and the songs saved are read from the disk.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsState(loaded = false))

    private fun snapshot(all: List<Download>, onPhone: List<DownloadedSong>): DownloadsState {
        return DownloadsState(
            running = all.filter { it.running },
            finished = all.filterNot { it.running },
            freeSpace = freeSpace().let { if (it >= 0) Utilities.humanReadableBytes(it, true) else "" },
            wifiOnly = App.getPreferences().getBoolean(SharedPreferencesKeys.SP_WIFI_ONLY, false),
            onPhone = onPhone,
        )
    }

    /**
     * The songs saved, by folder and file name: with the folder settings' defaults, by artist
     * and album, then track number. Songs without tags are named after their file and folder.
     */
    private fun readOnPhone(): List<DownloadedSong> = storedSongs()
        .sortedWith(
            compareBy<StoredSong, String>(String.CASE_INSENSITIVE_ORDER) { it.relativeDir }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.fileName },
        )
        .map { song ->
            DownloadedSong(
                title = song.title ?: song.fileName.substringBeforeLast('.'),
                artist = song.artist ?: song.relativeDir.trimEnd('/'),
                uri = song.uri,
            )
        }

    private fun download(downloader: ClementineSongDownloader): Download {
        val manager = DownloadManager.getInstance(App.getApp())
        val running = downloader.status != BackgroundTask.Status.FINISHED
        var size = Utilities.humanReadableBytes(downloader.totalDownloaded.toLong(), true) + " / " +
            Utilities.humanReadableBytes(downloader.totalFileSize.toLong(), true)
        if (downloader.status == BackgroundTask.Status.RUNNING) {
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
        return if (downloader.status == BackgroundTask.Status.RUNNING) {
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
