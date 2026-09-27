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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** The renderer turns Clementine's `RENDER_*` commands into playback, and reports back. */
@RunWith(RobolectricTestRunner::class)
class RendererTest {

    private val playback = FakePlayback()
    private val sent = mutableListOf<Message>()
    private lateinit var renderer: Renderer

    @Before
    fun setUp() {
        renderer = Renderer(playback) { sent += it.message }
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
}
