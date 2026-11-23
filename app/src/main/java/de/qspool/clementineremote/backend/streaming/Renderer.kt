package de.qspool.clementineremote.backend.streaming

import android.content.Context
import android.os.Handler
import android.os.Looper
import de.qspool.clementineremote.backend.ClementinePlayerConnection
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
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
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException

/**
 * This phone as Clementine's audio output: plays the items Clementine sends (`RENDER_*`) with
 * [playback], and reports back what it's doing, so Clementine, and every remote showing it,
 * follows. Clementine stays in charge: it decides what plays, and when to skip or stop.
 *
 * Messages come from the connection's thread ([onMessage]); everything else runs on the main
 * thread. [server] is where this phone connected to Clementine, which serves the tracks on the
 * same host and port.
 */
class Renderer(
    private val playback: Playback,
    private val server: () -> InetSocketAddress?,
    private val send: (ClementineMessage) -> Unit,
) : Playback.Listener {

    /** Told, on the main thread, when the phone starts and stops having something to play. */
    fun interface ActiveListener {
        fun onActiveChanged(active: Boolean)
    }

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

    var activeListener: ActiveListener? = null

    /** Whether [activeListener] was last told there's something to play. */
    private var active = false

    private var transientFailureItemId: Int? = null
    private var transientFailureCount = 0

    @Volatile
    private var released = false

    private val statusTick = object : Runnable {
        override fun run() {
            if (released) return
            sendStatus()
            handler.postDelayed(this, STATUS_INTERVAL_MS)
        }
    }

    init {
        playback.listener = this
    }

    /** A message from Clementine, on any thread. */
    fun onMessage(message: Message) {
        if (!released && message.type in HANDLED) {
            handler.post { if (!released) handle(message) }
        }
    }

    /** Stops active streaming across a reconnect while keeping this listener attached. */
    fun reset() {
        if (released) return
        handler.post {
            // Desktop destroys its RemoteEngine on TCP drop, so stop the dead
            // HTTP stream and clear item state while keeping playback reusable.
            if (released) return@post
            handler.removeCallbacks(statusTick)
            current = null
            next = null
            offsetMs = 0
            playing = false
            reported = null
            transientFailureItemId = null
            transientFailureCount = 0
            playback.stop()
            setActive(false)
        }
    }

    /** Stops playing, when the connection to Clementine has gone. */
    fun release() {
        // Mark released and drop queued handler work before posting stop,
        // running ExoPlayer.release() on the main looper thread it requires.
        released = true
        handler.removeCallbacksAndMessages(null)
        val stop = Runnable {
            current = null
            next = null
            offsetMs = 0
            playing = false
            reported = null
            transientFailureItemId = null
            transientFailureCount = 0
            playback.listener = null
            playback.release()
            setActive(false)
        }
        if (Looper.myLooper() == handler.looper) {
            stop.run()
        } else {
            handler.post(stop)
        }
    }

    private fun handle(message: Message) {
        if (released) return
        when (message.type) {
            MsgType.RENDER_LOAD -> {
                val load = message.requestRenderLoad
                playing = load.startState == LoadStartState.LOAD_START_STATE_PLAYING
                start(load.item, load.startMs)
            }
            MsgType.RENDER_PRELOAD -> {
                val preload = message.requestRenderPreload
                // Desktop sends an empty preload to clear the queued track;
                // drop it here so resolve() is not asked to resolve "".
                next = if (preload.hasItem() && preload.item.url.isNotEmpty()) preload.item else null
                if (current != null) playback.queue(next?.let { resolve(it.url, server()) })
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
        val url = resolve(item.url, server())
        if (url == null) {
            // Stop previous audio and report item failure before clearing
            // current so onStateChanged() updates active=false.
            playback.stop()
            onError("Not a URL: ${item.url}", transient = false)
            current = null
            playing = false
            onStateChanged()
            return
        }
        when (item.seekMethod) {
            // The url already starts there.
            SeekMethod.SEEK_METHOD_NEW_URL -> {
                offsetMs = startMs
                playback.load(url, 0, playing)
            }
            SeekMethod.SEEK_METHOD_BYTE_RANGE -> {
                offsetMs = 0
                playback.load(url, startMs, playing)
            }
            // Live, such as radio: it plays from wherever it is now.
            else -> {
                offsetMs = 0
                playback.load(url, 0, playing)
            }
        }
        onStateChanged()
    }

    private fun seek(itemId: Int, positionMs: Long, url: String) {
        val item = current ?: return
        // A seek meant for an item that has since changed.
        if (itemId != item.itemId) return
        val target = if (url.isEmpty()) null else resolve(url, server())
        when {
            target != null -> {
                offsetMs = positionMs
                playback.load(target, 0, playing)
                playback.queue(next?.let { resolve(it.url, server()) })
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
        if (released) return
        val state = rendererState()
        if (state == RendererState.RENDERER_STATE_PLAYING) {
            // Reset transient retry budget once playback recovers.
            transientFailureItemId = null
            transientFailureCount = 0
        }
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
        setActive(current != null)
    }

    private fun setActive(active: Boolean) {
        if (active == this.active) return
        this.active = active
        activeListener?.onActiveChanged(active)
    }

    override fun onAdvanced() {
        if (released) return
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
        if (released) return
        val ended = current ?: return
        current = null
        offsetMs = 0
        playing = false
        sendTrackEnded(ended.itemId)
        onStateChanged()
    }

    override fun onError(message: String, transient: Boolean) {
        if (released) return
        val item = current ?: return
        // Cap consecutive transient retries per item and escalate to ITEM scope
        // to avoid an infinite RENDER_LOAD <-> RENDERER_ERROR reload storm.
        val effectiveTransient = if (!transient) {
            false
        } else {
            if (transientFailureItemId != item.itemId) {
                transientFailureItemId = item.itemId
                transientFailureCount = 0
            }
            transientFailureCount++
            transientFailureCount < MAX_CONSECUTIVE_TRANSIENT_ERRORS
        }
        val scope = if (effectiveTransient) {
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
        fun attach(context: Context, connection: ClementinePlayerConnection, activeListener: ActiveListener): Renderer {
            val renderer = Renderer(
                ExoPlayback(context.applicationContext),
                connection::getServerAddress,
                // Send via the bound connection rather than RemoteRepository so
                // a dying Renderer cannot emit onto a newer connection.
                { msg ->
                    if (connection.isConnected) {
                        connection.mHandler?.sendMessage(android.os.Message.obtain().apply { obj = msg })
                    }
                },
            )
            renderer.activeListener = activeListener
            connection.addPlayerConnectionListener(object : PlayerConnectionListener {
                override fun onConnectionStatusChanged(status: ConnectionStatus) {
                    when (status) {
                        ConnectionStatus.RECONNECTING -> renderer.reset()
                        ConnectionStatus.DISCONNECTED -> renderer.release()
                        else -> {}
                    }
                }

                override fun onClementineMessageReceived(message: ClementineMessage) {
                    if (!message.isErrorMessage) {
                        renderer.onMessage(message.message)
                    }
                }
            })
            return renderer
        }

        /**
         * Where to fetch [url], as Clementine sent it: a URL with a scheme from exactly there, and
         * anything else, usually a path, relative to [server]'s host and port. Through NAT or a
         * port forward, Clementine doesn't know the address this phone reached it at, so it sends
         * paths. Null when there's nowhere to fetch it from.
         */
        @JvmStatic
        fun resolve(url: String, server: InetSocketAddress?): String? {
            // Empty relative URL would resolve to the server root; reject it.
            if (url.isBlank()) return null
            val uri = try {
                URI(url)
            } catch (e: URISyntaxException) {
                return null
            }
            if (uri.isAbsolute) return url
            if (server == null) return null
            val host = server.address?.hostAddress ?: server.hostString
            return try {
                URI("http", null, host, server.port, "/", null, null).resolve(uri).toString()
            } catch (e: URISyntaxException) {
                null
            }
        }

        /** How often to report the position while playing, as Clementine expects. */
        private const val STATUS_INTERVAL_MS = 1000L

        private const val MAX_CONSECUTIVE_TRANSIENT_ERRORS = 3

        private val HANDLED = setOf(
            MsgType.RENDER_LOAD, MsgType.RENDER_PRELOAD, MsgType.RENDER_PLAY,
            MsgType.RENDER_PAUSE, MsgType.RENDER_STOP, MsgType.RENDER_SEEK,
            MsgType.RENDER_SET_VOLUME,
        )
    }
}
