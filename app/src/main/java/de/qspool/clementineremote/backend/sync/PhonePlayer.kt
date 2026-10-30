package de.qspool.clementineremote.backend.sync

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays songs kept on this phone, through [PhonePlaybackService], and says which is playing.
 * Starting to play here pauses Clementine, so only one plays. Called on the main thread.
 */
object PhonePlayer {

    private var controller: ListenableFuture<MediaController>? = null

    private val _playing = MutableStateFlow<String?>(null)

    /** The item key of the song playing on this phone, or paused there; null when nothing is. */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _playing.value = if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                null
            } else {
                player.currentMediaItem?.mediaId
            }
        }
    }

    /** Plays [songs] on this phone from the one at [start], shuffled if [shuffle]. */
    fun play(context: Context, songs: List<KeptSong>, start: Int, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        if (App.Clementine.state == Clementine.State.PLAY) {
            RemoteRepository.send(ClementineMessage.getMessage(MsgType.PAUSE))
        }
        withController(context) {
            it.shuffleModeEnabled = shuffle
            it.setMediaItems(songs.map(::mediaItem), if (shuffle) songs.indices.random() else start.coerceIn(songs.indices), 0)
            it.prepare()
            it.play()
        }
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
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}
