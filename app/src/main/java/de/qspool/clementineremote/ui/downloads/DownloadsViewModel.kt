package de.qspool.clementineremote.ui.downloads

import android.net.Uri
import android.os.AsyncTask
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.downloader.ClementineSongDownloader
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.utils.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

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
) : ViewModel() {

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

    private companion object {
        const val REFRESH_MILLIS = 250L
    }
}
