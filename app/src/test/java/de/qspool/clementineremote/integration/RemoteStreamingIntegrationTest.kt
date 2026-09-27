package de.qspool.clementineremote.integration

import android.os.Looper
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.AudioFormat
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.OutputState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererCapabilities
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererFeature
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SeekMethod
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ServerFeature
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.StreamMode
import de.qspool.clementineremote.backend.streaming.Playback
import de.qspool.clementineremote.backend.streaming.Renderer
import de.qspool.clementineremote.testing.FakePlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app's renderer as a real Clementine's audio output (remote streaming): Clementine sends it
 * what to play and follows what it reports. The audio is fetched but not played.
 *
 * Needs a Clementine started with remote streaming (clementine-it with STREAMING=1); skipped
 * otherwise, like the other integration tests without -Pclementine.host.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteStreamingIntegrationTest {

    companion object {
        private const val RENDERER_ID = "integration-test-renderer"

        @BeforeClass
        @JvmStatic
        fun requireClementine() {
            assumeTrue("clementine.host not set", ClementineSession.HOST != null)
        }
    }

    private val playback = FakePlayback()

    @Before
    fun resetState() {
        App.Clementine = Clementine()
        App.ClementineConnection = null
    }

    private val capabilities = RendererCapabilities.newBuilder()
        .setRendererId(RENDERER_ID)
        .setDisplayName("Integration test")
        .addFormats(AudioFormat.newBuilder().setMimeType("audio/ogg; codecs=vorbis"))
        .addFeatures(RendererFeature.RENDERER_FEATURE_HTTP_RANGE)
        .build()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Messages are handed to the renderer as they arrive; this lets it act on them. */
    private fun ClementineSession.next(type: MsgType, matcher: (Message) -> Boolean = { true }): Message =
        await(type) { matcher(it) }.also { idle() }

    @Test
    fun playsOnThisPhone() {
        ClementineSession.connect(ClementineSession.AUTH_CODE, false, capabilities).use { session ->
            val info = session.await(MsgType.INFO)
            assumeTrue(
                "Clementine doesn't offer remote streaming",
                ServerFeature.SERVER_FEATURE_RENDERING in info.responseClementineInfo.featuresList,
            )
            session.await(MsgType.FIRST_DATA_SENT_COMPLETE)
            val renderer = Renderer(playback) { session.send(it) }
            session.listener = ClementineSession.Listener {
                renderer.onMessage(it)
                RemoteRepository.onMessage(ClementineMessage(it))
            }

            // Clementine lists this renderer, and plays on it when asked.
            session.send(ClementineMessageFactory.buildSetOutput(RENDERER_ID))
            session.next(MsgType.OUTPUTS) { message ->
                message.responseOutputs.outputsList.any {
                    it.outputId == RENDERER_ID && it.state == OutputState.OUTPUT_STATE_ACTIVE
                }
            }
            assertEquals(RENDERER_ID, RemoteRepository.outputs.value.active?.id)

            val playlistId = App.Clementine.playlistManager.activePlaylistId
            session.send(ClementineMessageFactory.buildRequestChangeSong(0, playlistId))
            val first = session.next(MsgType.RENDER_LOAD).requestRenderLoad.item
            assertEquals(StreamMode.STREAM_MODE_DIRECT, first.mode)
            assertEquals(SeekMethod.SEEK_METHOD_BYTE_RANGE, first.seekMethod)
            assertEquals(first.url, playback.url)

            // Clementine serves the file itself, with Range support.
            val http = URL(first.url).openConnection() as HttpURLConnection
            http.setRequestProperty("Range", "bytes=0-3")
            assertEquals(206, http.responseCode)
            assertEquals("OggS", http.inputStream.use { String(it.readBytes(), Charsets.US_ASCII) })

            // Clementine shows what the renderer reports.
            playback.become(Playback.State.PLAYING)
            idle()
            session.send(MsgType.PAUSE)
            session.next(MsgType.RENDER_PAUSE)
            assertEquals(Playback.State.PAUSED, playback.state)
            session.next(MsgType.PAUSE)
            session.send(MsgType.PLAY)
            session.next(MsgType.RENDER_PLAY)
            assertEquals(Playback.State.PLAYING, playback.state)

            // When the track ends, Clementine moves on to the next, gaplessly if it had queued it.
            playback.finish()
            idle()
            session.next(MsgType.CURRENT_METAINFO) {
                it.responseCurrentMetadata.songMetadata.title == "Track 02"
            }
            assertNotEquals(first.url, playback.url)

            // Back to Clementine's own computer.
            session.send(ClementineMessageFactory.buildSetOutput(RemoteRepository.LOCAL_OUTPUT))
            session.next(MsgType.RENDER_STOP)
            assertEquals(null, playback.url)
            session.send(MsgType.STOP)
        }
    }
}
