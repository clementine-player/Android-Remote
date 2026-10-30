package de.qspool.clementineremote.backend.sync

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.protobuf.ByteString
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.player.MySong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the app plays from: Clementine, or songs kept on this phone. */
enum class PlayFrom { CLEMENTINE, PHONE }

/**
 * Plays songs kept on this phone, through [PhonePlaybackService], says what's playing, and
 * controls it for the app's player. Starting to play here pauses Clementine, so only one plays.
 * Called on the main thread.
 */
object PhonePlayer {

    private var controller: ListenableFuture<MediaController>? = null

    private val _playing = MutableStateFlow<String?>(null)

    /** The item key of the song playing on this phone, or paused there; null when nothing is. */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /**
     * What plays on this phone, or is paused there, as the app's player shows it; null when
     * nothing is. Its volume isn't used: the phone's own is.
     */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** The song [nowPlaying] has, kept while it plays so the player sees the same song. */
    private var song: MySong? = null

    /** The songs being played, by item key, for what the player shows of them. */
    private var songs: Map<String, KeptSong> = emptyMap()

    private val _playFrom = MutableStateFlow(PlayFrom.CLEMENTINE)

    /**
     * Where the app plays from: this phone from when it starts playing here until Play from
     * switches back to Clementine, Clementine starts playing, or it stops here.
     */
    val playFrom: StateFlow<PlayFrom> = _playFrom.asStateFlow()

    private val activeListeners = mutableListOf<Consumer<Boolean>>()

    /** Whether the app plays from this phone, with something playing (or paused) here. */
    private var active = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _playing.value = if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                null
            } else {
                player.currentMediaItem?.mediaId
            }
            _nowPlaying.value = _playing.value?.let { nowPlaying(player, it) }
            if (_playing.value == null) {
                _playFrom.value = PlayFrom.CLEMENTINE
            }
            updateActive()
        }
    }

    /**
     * Tells [listener], on the main thread, when the app starts or stops playing from this phone,
     * with something playing (or paused) here.
     */
    fun addActiveListener(listener: Consumer<Boolean>) {
        activeListeners += listener
    }

    private fun updateActive() {
        val active = _playFrom.value == PlayFrom.PHONE && _playing.value != null
        if (active != this.active) {
            this.active = active
            activeListeners.forEach { it.accept(active) }
        }
    }

    /**
     * Switches where the app plays from, pausing the one it leaves; the other stays paused until
     * it's played. This phone only when something is playing (or paused) here.
     */
    fun playFrom(context: Context, place: PlayFrom) {
        if (place == _playFrom.value || (place == PlayFrom.PHONE && _playing.value == null)) return
        when (place) {
            PlayFrom.PHONE -> pauseClementine()
            PlayFrom.CLEMENTINE -> withController(context) { it.pause() }
        }
        _playFrom.value = place
        updateActive()
    }

    private fun pauseClementine() {
        if (App.Clementine.state == Clementine.State.PLAY) {
            RemoteRepository.send(ClementineMessage.getMessage(MsgType.PAUSE))
        }
    }

    /** Plays [songs] on this phone from the one at [start], shuffled if [shuffle]. */
    fun play(context: Context, songs: List<KeptSong>, start: Int, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        pauseClementine()
        _playFrom.value = PlayFrom.PHONE
        updateActive()
        this.songs = songs.associateBy { it.itemKey }
        withController(context) {
            it.shuffleModeEnabled = shuffle
            it.setMediaItems(songs.map(::mediaItem), if (shuffle) songs.indices.random() else start.coerceIn(songs.indices), 0)
            it.prepare()
            it.play()
        }
    }

    /** Stops playing on this phone, and forgets what was playing. */
    fun stop(context: Context) {
        withController(context) {
            it.stop()
            it.clearMediaItems()
        }
    }

    fun playPause(context: Context) = withController(context) {
        if (it.isPlaying) it.pause() else it.play()
    }

    fun next(context: Context) = withController(context) { it.seekToNext() }

    fun previous(context: Context) = withController(context) { it.seekToPrevious() }

    fun seekTo(context: Context, seconds: Int) = withController(context) { it.seekTo(seconds * 1000L) }

    /** Turns shuffling on or off. */
    fun toggleShuffle(context: Context) = withController(context) {
        it.shuffleModeEnabled = !it.shuffleModeEnabled
    }

    /** Moves to the next repeat mode: off, then all the songs, then the one playing. */
    fun cycleRepeat(context: Context) = withController(context) {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun nowPlaying(player: Player, itemKey: String): NowPlaying {
        // What the song was kept as, with what that doesn't say from the file's tags, which come
        // in once the file is read; and the cover in the file, if it has one.
        val metadata = player.mediaMetadata
        val kept = songs[itemKey]
        val title = metadata.title?.toString() ?: kept?.title.orEmpty()
        val artist = metadata.artist?.toString() ?: kept?.artist.orEmpty()
        val album = metadata.albumTitle?.toString() ?: kept?.album.orEmpty()
        val art = metadata.artworkData
        val known = song?.takeIf {
            it.filename == itemKey && it.title == title && it.artist == artist && it.album == album &&
                (art == null || it.artData != null)
        }
        // A new song when what's shown of it changes, so the player shows the change.
        val song = known ?: MySong().apply {
            filename = itemKey
            // A file on the phone, not a stream.
            isLocal = true
            this.title = title
            this.artist = artist
            this.album = album
            if (art != null) {
                setArt(ByteString.copyFrom(art))
            }
        }
        this.song = song
        if (player.duration != C.TIME_UNSET) {
            val seconds = (player.duration / 1000).toInt()
            song.length = seconds
            song.prettyLength = "%d:%02d".format(seconds / 60, seconds % 60)
        }
        val playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        return NowPlaying(
            song = song,
            state = if (playing) Clementine.State.PLAY else Clementine.State.PAUSE,
            positionSeconds = (player.currentPosition / 1000).toInt(),
            volume = 0,
            shuffle = if (player.shuffleModeEnabled) Clementine.ShuffleMode.ALL else Clementine.ShuffleMode.OFF,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> Clementine.RepeatMode.PLAYLIST
                Player.REPEAT_MODE_ONE -> Clementine.RepeatMode.TRACK
                else -> Clementine.RepeatMode.OFF
            },
        )
    }

    private fun withController(context: Context, use: (MediaController) -> Unit) {
        // The controller is used on the main thread, where it was made.
        val main = ContextCompat.getMainExecutor(context)
        val future = controller ?: MediaController.Builder(
            context.applicationContext,
            SessionToken(context.applicationContext, ComponentName(context.applicationContext, PhonePlaybackService::class.java)),
        ).buildAsync().also { future ->
            controller = future
            future.addListener({
                runCatching { future.get() }.onSuccess { it.addListener(listener) }.onFailure { controller = null }
            }, main)
        }
        future.addListener({
            runCatching { future.get() }.onSuccess {
                if (it.isConnected) {
                    use(it)
                } else {
                    // The service stopped since, such as when the app was swiped away paused.
                    it.release()
                    controller = null
                    withController(context, use)
                }
            }
        }, main)
    }

    private fun mediaItem(song: KeptSong): MediaItem = MediaItem.Builder()
        .setMediaId(song.itemKey)
        .setUri(song.uri)
        // The URI doesn't reach the service, which plays what the request says.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(song.uri).build())
        .setMediaMetadata(
            // Only what's known: what isn't comes from the file's tags, which these would hide.
            MediaMetadata.Builder()
                .setTitle(song.title.ifEmpty { null })
                .setArtist(song.artist.ifEmpty { null })
                .setAlbumTitle(song.album.ifEmpty { null })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}
