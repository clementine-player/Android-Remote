package de.qspool.clementineremote.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.listener.OnPlaylistReceivedListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.backend.player.MyPlaylist
import de.qspool.clementineremote.backend.player.MySong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.LinkedList

/** One of Clementine's playlists, to pick. */
data class PlaylistTab(val id: Int, val name: String)

/** The queue screen's state: Clementine's playlists, and the songs of the one shown. */
data class QueueState(
    val playlists: List<PlaylistTab> = emptyList(),
    /** The playlist shown; null when Clementine has none. */
    val shown: PlaylistTab? = null,
    /** The shown playlist's songs, filtered. */
    val songs: List<MySong> = emptyList(),
    /** All of the shown playlist's songs and their length in seconds, filtered or not. */
    val songCount: Int = 0,
    val lengthSeconds: Int = 0,
    /** The index of the song playing, when it's in the shown playlist. */
    val playingIndex: Int? = null,
    /** While playlists download: how many of how many are in. */
    val loading: Pair<Int, Int>? = null,
    val filter: String = "",
)

/**
 * Clementine's playlists, as the queue screen shows them. It follows Clementine's playlist
 * manager, and sends the screen's commands to Clementine.
 */
class QueueViewModel(
    private val send: (ClementineMessage) -> Unit = RemoteRepository::send,
) : ViewModel() {

    private val playlists = App.Clementine.playlistManager

    /** Bumped whenever the playlist manager says something changed. */
    private val changes = MutableStateFlow(0)

    private val shownId = MutableStateFlow<Int?>(null)

    private val filter = MutableStateFlow("")

    private val loading = MutableStateFlow<Pair<Int, Int>?>(null)

    private val listener = object : OnPlaylistReceivedListener {
        override fun onPlaylistReceived(p: MyPlaylist) = changed()

        override fun onPlaylistSongsReceived(p: MyPlaylist) {
            loading.value = loading.value?.let { (done, total) -> (done + 1).coerceAtMost(total) to total }
            changed()
        }

        override fun onAllRequestedPlaylistSongsReceived() {
            loading.value = null
            changed()
        }

        override fun onAllPlaylistsReceived() {
            changed()
            load()
        }
    }

    init {
        playlists.addOnPlaylistReceivedListener(listener)
    }

    override fun onCleared() {
        playlists.removeOnPlaylistReceivedListener(listener)
    }

    private fun changed() {
        changes.value++
    }

    val state: StateFlow<QueueState> =
        combine(changes, shownId, filter, loading, RemoteRepository.nowPlaying) { _, shown, filter, loading, nowPlaying ->
            snapshot(shown, filter, loading, nowPlaying.song)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueState())

    private fun snapshot(shownId: Int?, filter: String, loading: Pair<Int, Int>?, playing: MySong?): QueueState {
        val all = playlists.allPlaylists
        val shown = shownPlaylist(shownId)
        // A copy: Clementine's messages change the playlist on the connection's thread.
        val songs = shown?.playlistSongs?.toList().orEmpty()
        val filtered = if (filter.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(filter, ignoreCase = true) ||
                    it.artist.contains(filter, ignoreCase = true) ||
                    it.album.contains(filter, ignoreCase = true)
            }
        }
        val playingHere = playing != null && shown != null && shown.id == playlists.activePlaylistId
        return QueueState(
            playlists = all.map { PlaylistTab(it.id, it.name) },
            shown = shown?.let { PlaylistTab(it.id, it.name) },
            songs = filtered,
            songCount = songs.size,
            lengthSeconds = songs.sumOf { it.length.coerceAtLeast(0) },
            playingIndex = if (playingHere) playing?.index else null,
            loading = loading,
            filter = filter,
        )
    }

    /** Asks Clementine for the songs of the playlists not yet downloaded. */
    fun load() {
        if (App.ClementineConnection?.isConnected != true || loading.value != null) {
            return
        }
        val requests = playlists.requestAllPlaylistSongs()
        if (requests > 0) {
            loading.value = 0 to requests
        }
        changed()
    }

    fun show(playlist: PlaylistTab) {
        shownId.value = playlist.id
    }

    fun setFilter(text: String) {
        filter.value = text
    }

    /** The playlist picked, else the one playing, else the first. */
    private fun shownPlaylist(id: Int? = shownId.value): MyPlaylist? {
        val all = playlists.allPlaylists
        return all.firstOrNull { it.id == id } ?: playlists.activePlaylist ?: all.firstOrNull()
    }

    private fun shownPlaylistId(): Int? = shownPlaylist()?.id

    /** Plays a song of the shown playlist, which becomes the one playing. */
    fun play(song: MySong) {
        val id = shownPlaylistId() ?: return
        send(ClementineMessageFactory.buildRequestChangeSong(song.index, id))
        playlists.setActivePlaylist(id)
        changed()
    }

    fun download(songs: Collection<MySong>) {
        val urls = songs.mapNotNull { it.url }
        if (urls.isNotEmpty()) {
            DownloadManager.getInstance().addJob(
                ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.Urls, LinkedList(urls)))
        }
    }

    fun remove(songs: Collection<MySong>) {
        val id = shownPlaylistId() ?: return
        send(ClementineMessageFactory.buildRemoveMultipleSongsFromPlaylist(id, LinkedList(songs)))
    }

    fun downloadPlaylist() {
        val id = shownPlaylistId() ?: return
        DownloadManager.getInstance().addJob(
            ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.APlaylist, id))
    }

    fun clearPlaylist() {
        val id = shownPlaylistId() ?: return
        playlists.clearPlaylist(id)
        changed()
    }

    fun closePlaylist() {
        val id = shownPlaylistId() ?: return
        send(ClementineMessageFactory.buildClosePlaylist(id))
    }
}
