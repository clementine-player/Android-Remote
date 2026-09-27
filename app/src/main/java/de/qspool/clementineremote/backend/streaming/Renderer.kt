package de.qspool.clementineremote.backend.streaming

import android.content.Context
import android.os.Handler
import android.os.Looper
import de.qspool.clementineremote.backend.ClementinePlayerConnection
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.LoadStartState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RenderItem
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererError
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererErrorScope
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererStatus
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererTrackEnded
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SeekMethod

/**
 * This phone as Clementine's audio output: plays the items Clementine sends (`RENDER_*`) with
 * [playback], and reports back what it's doing, so Clementine, and every remote showing it,
 * follows. Clementine stays in charge: it decides what plays, and when to skip or stop.
 *
 * Messages come from the connection's thread ([onMessage]); everything else runs on the main
 * thread.
 */
class Renderer(
    private val playback: Playback,
    private val send: (ClementineMessage) -> Unit,
) : Playback.Listener {

    private val handler = Handler(Looper.getMainLooper())

    /** What's playing, as Clementine described it; null when idle. */
    private var current: RenderItem? = null

    /** Queued to follow [current]. */
    private var next: RenderItem? = null

    /**
     * Where the url playing starts within [current]: an item seeked by loading a new url
     * ([SeekMethod.SEEK_METHOD_NEW_URL]) starts where the seek went.
     */
    private var offsetMs = 0L

    /** Whether Clementine wants [current] playing, rather than paused. */
    private var playing = false

    private var reported: RendererState? = null

    private val statusTick = object : Runnable {
        override fun run() {
            sendStatus()
            handler.postDelayed(this, STATUS_INTERVAL_MS)
        }
    }

    init {
        playback.listener = this
    }

    /** A message from Clementine, on any thread. */
    fun onMessage(message: Message) {
        if (message.type in HANDLED) {
            handler.post { handle(message) }
        }
    }

    /** Stops playing, when the connection to Clementine has gone. */
    fun release() {
        handler.post {
            handler.removeCallbacks(statusTick)
            current = null
            next = null
            playback.listener = null
            playback.release()
        }
    }

    private fun handle(message: Message) {
        when (message.type) {
            MsgType.RENDER_LOAD -> {
                val load = message.requestRenderLoad
                playing = load.startState == LoadStartState.LOAD_START_STATE_PLAYING
                start(load.item, load.startMs)
            }
            MsgType.RENDER_PRELOAD -> {
                next = message.requestRenderPreload.item
                if (current != null) playback.queue(next?.url)
            }
            MsgType.RENDER_PLAY -> if (current != null) {
                playing = true
                playback.play()
            }
            MsgType.RENDER_PAUSE -> if (current != null) {
                playing = false
                playback.pause()
            }
            MsgType.RENDER_STOP -> {
                current = null
                next = null
                offsetMs = 0
                playing = false
                playback.stop()
                onStateChanged()
            }
            MsgType.RENDER_SEEK -> seek(message.requestRenderSeek.itemId,
                message.requestRenderSeek.positionMs, message.requestRenderSeek.url)
            MsgType.RENDER_SET_VOLUME ->
                playback.setVolume(message.requestRenderVolume.volume.coerceIn(0, 100) / 100f)
            else -> {}
        }
    }

    /** Plays [item] from [startMs], replacing what's playing and queued. */
    private fun start(item: RenderItem, startMs: Long) {
        current = item
        next = null
        reported = null
        when (item.seekMethod) {
            // The url already starts there.
            SeekMethod.SEEK_METHOD_NEW_URL -> {
                offsetMs = startMs
                playback.load(item.url, 0, playing)
            }
            SeekMethod.SEEK_METHOD_BYTE_RANGE -> {
                offsetMs = 0
                playback.load(item.url, startMs, playing)
            }
            // Live, such as radio: it plays from wherever it is now.
            else -> {
                offsetMs = 0
                playback.load(item.url, 0, playing)
            }
        }
        onStateChanged()
    }

    private fun seek(itemId: Int, positionMs: Long, url: String) {
        val item = current ?: return
        // A seek meant for an item that has since changed.
        if (itemId != item.itemId) return
        when {
            url.isNotEmpty() -> {
                offsetMs = positionMs
                playback.load(url, 0, playing)
                playback.queue(next?.url)
            }
            item.seekMethod == SeekMethod.SEEK_METHOD_BYTE_RANGE -> {
                offsetMs = 0
                playback.seekTo(positionMs)
            }
            else -> return
        }
        sendStatus()
    }

    override fun onStateChanged() {
        val state = rendererState()
        if (state == RendererState.RENDERER_STATE_PLAYING ||
            state == RendererState.RENDERER_STATE_BUFFERING
        ) {
            if (reported != RendererState.RENDERER_STATE_PLAYING &&
                reported != RendererState.RENDERER_STATE_BUFFERING
            ) {
                handler.removeCallbacks(statusTick)
                handler.postDelayed(statusTick, STATUS_INTERVAL_MS)
            }
        } else {
            handler.removeCallbacks(statusTick)
        }
        if (state != reported) {
            sendStatus()
        }
    }

    override fun onAdvanced() {
        val ended = current ?: return
        current = next
        next = null
        offsetMs = 0
        // Clementine hears of the switch first, so the status that follows is about the item it
        // now considers current.
        sendTrackEnded(ended.itemId)
        reported = null
        onStateChanged()
    }

    override fun onEnded() {
        val ended = current ?: return
        current = null
        offsetMs = 0
        playing = false
        sendTrackEnded(ended.itemId)
        onStateChanged()
    }

    override fun onError(message: String, transient: Boolean) {
        val item = current ?: return
        val scope = if (transient) {
            RendererErrorScope.RENDERER_ERROR_SCOPE_TRANSIENT
        } else {
            RendererErrorScope.RENDERER_ERROR_SCOPE_ITEM
        }
        send(ClementineMessage(ClementineMessage.getMessageBuilder(MsgType.RENDERER_ERROR)
            .setRendererError(RendererError.newBuilder()
                .setItemId(item.itemId)
                .setMessage(message)
                .setScope(scope))))
    }

    private fun rendererState(): RendererState {
        if (current == null) return RendererState.RENDERER_STATE_IDLE
        return when (playback.state) {
            Playback.State.IDLE -> RendererState.RENDERER_STATE_LOADING
            Playback.State.BUFFERING ->
                if (playing) RendererState.RENDERER_STATE_BUFFERING else RendererState.RENDERER_STATE_PAUSED
            Playback.State.PLAYING -> RendererState.RENDERER_STATE_PLAYING
            // Includes playback paused by Android, such as for a call, which Clementine then shows.
            Playback.State.PAUSED -> RendererState.RENDERER_STATE_PAUSED
        }
    }

    private fun sendStatus() {
        val item = current
        val state = rendererState()
        reported = state
        var position = if (item == null) 0 else offsetMs + playback.positionMs
        if (item != null && item.lengthMs > 0) position = position.coerceAtMost(item.lengthMs)
        send(ClementineMessage(ClementineMessage.getMessageBuilder(MsgType.RENDERER_STATUS)
            .setRendererStatus(RendererStatus.newBuilder()
                .setItemId(item?.itemId ?: 0)
                .setState(state)
                .setPositionMs(position)
                .setBufferedPercent(if (item == null) 0 else playback.bufferedPercent))))
    }

    private fun sendTrackEnded(itemId: Int) {
        send(ClementineMessage(ClementineMessage.getMessageBuilder(MsgType.RENDERER_TRACK_ENDED)
            .setRendererTrackEnded(RendererTrackEnded.newBuilder().setItemId(itemId))))
    }

    companion object {
        /**
         * Plays what Clementine sends over [connection], for as long as it's open. Clementine
         * only sends it when this phone registered as a renderer ([ThisRenderer]).
         */
        @JvmStatic
        fun attach(context: Context, connection: ClementinePlayerConnection) {
            val renderer = Renderer(ExoPlayback(context.applicationContext), RemoteRepository::send)
            connection.addPlayerConnectionListener(object : PlayerConnectionListener {
                override fun onConnectionStatusChanged(status: ConnectionStatus) {
                    if (status == ConnectionStatus.DISCONNECTED) {
                        renderer.release()
                    }
                }

                override fun onClementineMessageReceived(message: ClementineMessage) {
                    if (!message.isErrorMessage) {
                        renderer.onMessage(message.message)
                    }
                }
            })
        }

        /** How often to report the position while playing, as Clementine expects. */
        private const val STATUS_INTERVAL_MS = 1000L

        private val HANDLED = setOf(
            MsgType.RENDER_LOAD, MsgType.RENDER_PRELOAD, MsgType.RENDER_PLAY,
            MsgType.RENDER_PAUSE, MsgType.RENDER_STOP, MsgType.RENDER_SEEK,
            MsgType.RENDER_SET_VOLUME,
        )
    }
}
