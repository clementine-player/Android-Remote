package de.qspool.clementineremote.backend.streaming

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * [Playback] with ExoPlayer, made when first needed. It takes audio focus like any music player,
 * and pauses when headphones are unplugged; the [Renderer] tells Clementine.
 */
@OptIn(UnstableApi::class)
class ExoPlayback(private val context: Context) : Playback, Player.Listener {

    override var listener: Playback.Listener? = null

    private var player: ExoPlayer? = null

    private fun player(): ExoPlayer = player ?: ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus= */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        // Keeps the CPU and Wi-Fi awake while streaming with the screen off.
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also {
            it.addListener(this)
            player = it
        }

    override val state: Playback.State
        get() {
            val player = player ?: return Playback.State.IDLE
            return when (player.playbackState) {
                Player.STATE_BUFFERING -> Playback.State.BUFFERING
                Player.STATE_READY ->
                    if (player.isPlaying) Playback.State.PLAYING else Playback.State.PAUSED
                else -> Playback.State.IDLE
            }
        }

    override val positionMs: Long
        get() = player?.currentPosition ?: 0

    override val bufferedPercent: Int
        get() = player?.bufferedPercentage ?: 0

    override fun load(url: String, startMs: Long, playing: Boolean) {
        player().run {
            setMediaItem(MediaItem.fromUri(url), startMs)
            playWhenReady = playing
            prepare()
        }
    }

    override fun queue(url: String?) {
        val player = player ?: return
        // Only what's playing stays; anything queued after it is replaced.
        val after = player.currentMediaItemIndex + 1
        if (player.mediaItemCount > after) {
            player.removeMediaItems(after, player.mediaItemCount)
        }
        if (url != null) {
            player.addMediaItem(MediaItem.fromUri(url))
        }
    }

    override fun play() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        player().volume = volume
    }

    override fun stop() {
        player?.run {
            stop()
            clearMediaItems()
        }
    }

    override fun release() {
        player?.release()
        player = null
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
            )
        ) {
            listener?.onStateChanged()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            listener?.onAdvanced()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            listener?.onEnded()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val transient = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        listener?.onError(error.message ?: error.errorCodeName, transient)
    }
}
