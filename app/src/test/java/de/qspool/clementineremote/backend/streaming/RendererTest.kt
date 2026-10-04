package de.qspool.clementineremote.backend.streaming

import android.os.Looper
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.testing.FakePlayback
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.LoadStartState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RenderItem
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererErrorScope
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestRenderLoad
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestRenderPreload
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestRenderSeek
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestRenderVolume
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SeekMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration

/** The renderer turns Clementine's `RENDER_*` commands into playback, and reports back. */
@RunWith(RobolectricTestRunner::class)
class RendererTest {

    private val playback = FakePlayback()
    private val sent = mutableListOf<Message>()
    private val active = mutableListOf<Boolean>()
    private lateinit var renderer: Renderer

    /** Where the phone connected to Clementine. */
    private var server: InetSocketAddress? = address("192.0.2.5", 5500)

    private fun address(ip: String, port: Int) = InetSocketAddress(InetAddress.getByName(ip), port)

    @Before
    fun setUp() {
        renderer = Renderer(playback, { server }) { sent += it.message }
        renderer.activeListener = Renderer.ActiveListener { active += it }
    }

    private fun receive(message: Message.Builder) {
        renderer.onMessage(message.build())
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun item(id: Int, seek: SeekMethod = SeekMethod.SEEK_METHOD_BYTE_RANGE, length: Long = 200_000) =
        RenderItem.newBuilder()
            .setItemId(id)
            .setUrl("http://clementine:5500/s/token/$id")
            .setSeekMethod(seek)
            .setLengthMs(length)

    private fun load(item: RenderItem.Builder, startMs: Long = 0, playing: Boolean = true) =
        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_LOAD).setRequestRenderLoad(
            RequestRenderLoad.newBuilder()
                .setItem(item)
                .setStartMs(startMs)
                .setStartState(
                    if (playing) LoadStartState.LOAD_START_STATE_PLAYING else LoadStartState.LOAD_START_STATE_PAUSED,
                ),
        ))

    private fun statuses() = sent.filter { it.type == MsgType.RENDERER_STATUS }.map { it.rendererStatus }

    @Test
    fun loadsAndReportsPlaying() {
        load(item(7), startMs = 30_000)

        assertEquals("http://clementine:5500/s/token/7", playback.url)
        assertEquals(30_000, playback.positionMs)
        assertEquals(RendererState.RENDERER_STATE_BUFFERING, statuses().last().state)

        playback.become(Playback.State.PLAYING)
        val status = statuses().last()
        assertEquals(7, status.itemId)
        assertEquals(RendererState.RENDERER_STATE_PLAYING, status.state)
        assertEquals(30_000, status.positionMs)
    }

    @Test
    fun reportsThePositionEverySecondWhilePlaying() {
        load(item(1))
        playback.become(Playback.State.PLAYING)
        val before = statuses().size

        playback.positionMs = 2_500
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_100))

        assertEquals(before + 2, statuses().size)
        assertEquals(2_500, statuses().last().positionMs)
    }

    @Test
    fun seeksANewUrlItemByLoadingTheUrlGiven() {
        load(item(3, SeekMethod.SEEK_METHOD_NEW_URL))
        playback.become(Playback.State.PLAYING)

        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_SEEK).setRequestRenderSeek(
            RequestRenderSeek.newBuilder().setItemId(3).setPositionMs(60_000).setUrl("http://clementine:5500/s/token/3?t=60000"),
        ))

        assertEquals("http://clementine:5500/s/token/3?t=60000", playback.url)
        // The new url starts at 60 s, so its start is reported as 60 s.
        assertEquals(60_000, statuses().last().positionMs)
        playback.positionMs = 1_000
        playback.become(Playback.State.PLAYING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_100))
        assertEquals(61_000, statuses().last().positionMs)
    }

    @Test
    fun ignoresASeekForAnotherItem() {
        load(item(3))
        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_SEEK).setRequestRenderSeek(
            RequestRenderSeek.newBuilder().setItemId(2).setPositionMs(60_000),
        ))

        assertEquals(0, playback.positionMs)
    }

    @Test
    fun movesOnToThePreloadedItemAndSaysTheLastOneEnded() {
        load(item(1))
        playback.become(Playback.State.PLAYING)
        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_PRELOAD).setRequestRenderPreload(
            RequestRenderPreload.newBuilder().setItem(item(2)),
        ))
        assertEquals("http://clementine:5500/s/token/2", playback.queued)

        sent.clear()
        playback.finish()

        assertEquals(MsgType.RENDERER_TRACK_ENDED, sent.first().type)
        assertEquals(1, sent.first().rendererTrackEnded.itemId)
        assertEquals(2, statuses().last().itemId)
    }

    @Test
    fun endsAndGoesIdleWithNothingQueued() {
        load(item(1))
        playback.become(Playback.State.PLAYING)
        sent.clear()

        playback.finish()

        assertEquals(1, sent.single { it.type == MsgType.RENDERER_TRACK_ENDED }.rendererTrackEnded.itemId)
        assertEquals(RendererState.RENDERER_STATE_IDLE, statuses().last().state)
    }

    @Test
    fun saysWhileItHasSomethingToPlay() {
        load(item(1))
        playback.become(Playback.State.PLAYING)
        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_PAUSE))
        assertEquals(listOf(true), active)

        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_STOP))
        assertEquals(listOf(true, false), active)

        load(item(2))
        renderer.release()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(true, false, true, false), active)
    }

    @Test
    fun reportsErrorsWithTheirScope() {
        load(item(4))

        playback.listener!!.onError("Source error", transient = true)
        playback.listener!!.onError("Decoder failed", transient = false)

        val errors = sent.filter { it.type == MsgType.RENDERER_ERROR }.map { it.rendererError }
        assertEquals(listOf(4, 4), errors.map { it.itemId })
        assertEquals(
            listOf(RendererErrorScope.RENDERER_ERROR_SCOPE_TRANSIENT, RendererErrorScope.RENDERER_ERROR_SCOPE_ITEM),
            errors.map { it.scope },
        )
    }

    @Test
    fun pausesPlaysStopsAndSetsTheVolume() {
        load(item(1))
        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_PAUSE))
        assertEquals(RendererState.RENDERER_STATE_PAUSED, statuses().last().state)

        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_PLAY))
        assertEquals(RendererState.RENDERER_STATE_PLAYING, statuses().last().state)

        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_SET_VOLUME)
            .setRequestRenderVolume(RequestRenderVolume.newBuilder().setVolume(40)))
        assertEquals(0.4f, playback.level)

        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_STOP))
        assertNull(playback.url)
        assertEquals(RendererState.RENDERER_STATE_IDLE, statuses().last().state)
        assertEquals(0, statuses().last().itemId)
    }

    @Test
    fun ignoresOtherMessages() {
        receive(ClementineMessage.getMessageBuilder(MsgType.PLAY))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun fetchesAPathFromWhereItConnected() {
        load(item(3, SeekMethod.SEEK_METHOD_NEW_URL).setUrl("/s/token/3"))
        assertEquals("http://192.0.2.5:5500/s/token/3", playback.url)

        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_SEEK).setRequestRenderSeek(
            RequestRenderSeek.newBuilder().setItemId(3).setPositionMs(60_000).setUrl("/s/token/3?t=60000"),
        ))
        assertEquals("http://192.0.2.5:5500/s/token/3?t=60000", playback.url)
    }

    @Test
    fun queuesAPathFromWhereItConnected() {
        load(item(1))
        receive(ClementineMessage.getMessageBuilder(MsgType.RENDER_PRELOAD).setRequestRenderPreload(
            RequestRenderPreload.newBuilder().setItem(item(2).setUrl("/s/token/2")),
        ))
        assertEquals("http://192.0.2.5:5500/s/token/2", playback.queued)
    }

    @Test
    fun resolvesUrlsAsClementineMeansThem() {
        val server = address("203.0.113.7", 5500)
        // A full URL is fetched from exactly there.
        assertEquals("http://radio.example/stream", Renderer.resolve("http://radio.example/stream", server))
        assertEquals("http://203.0.113.7:5500/s/t/1?t=5", Renderer.resolve("/s/t/1?t=5", server))
        assertEquals("http://[2001:db8:0:0:0:0:0:1]:443/s/t/1",
            Renderer.resolve("/s/t/1", address("2001:db8::1", 443)))
        assertEquals("http://clementine.example.org:5500/s/t/1",
            Renderer.resolve("/s/t/1", InetSocketAddress.createUnresolved("clementine.example.org", 5500)))
        // Not connected anywhere: nowhere to fetch a path from.
        assertNull(Renderer.resolve("/s/t/1", null))
        assertNull(Renderer.resolve("not a url", server))
    }

    @Test
    fun reportsAPathItCantPlaceAsAnError() {
        server = null
        load(item(4).setUrl("/s/token/4"))

        assertNull(playback.url)
        val error = sent.last { it.type == MsgType.RENDERER_ERROR }.rendererError
        assertEquals(4, error.itemId)
        assertEquals(RendererErrorScope.RENDERER_ERROR_SCOPE_ITEM, error.scope)
        assertFalse(statuses().any { it.state == RendererState.RENDERER_STATE_PLAYING })
    }
}
