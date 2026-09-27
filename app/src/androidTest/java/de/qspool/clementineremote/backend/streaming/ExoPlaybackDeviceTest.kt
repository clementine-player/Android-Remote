package de.qspool.clementineremote.backend.streaming

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * ExoPlayer plays what Clementine would stream: WAV over plain HTTP from the local network,
 * reporting its state, and moving on to a queued item by itself.
 */
@RunWith(AndroidJUnit4::class)
class ExoPlaybackDeviceTest {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var server: ServerSocket
    private lateinit var playback: ExoPlayback

    /** Answers every request with half a second of silence as WAV, as Clementine serves a file. */
    @Before
    fun startServer() {
        val wav = silence(millis = 500)
        server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use {
                        val reader = it.getInputStream().bufferedReader(Charsets.US_ASCII)
                        while (reader.readLine()?.isNotEmpty() == true) {
                            // The request itself doesn't matter.
                        }
                        val out = it.getOutputStream()
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\n" +
                            "Content-Length: ${wav.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        out.write(wav)
                        out.flush()
                    }
                }
            }
        }
        main.post { playback = ExoPlayback(InstrumentationRegistry.getInstrumentation().targetContext) }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After
    fun stop() {
        main.post { playback.release() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        server.close()
    }

    private fun url(item: Int) = "http://127.0.0.1:${server.localPort}/s/token/$item"

    @Test
    fun playsOverHttpAndMovesOnToTheQueuedItem() {
        val playing = CountDownLatch(1)
        val advanced = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val errors = mutableListOf<String>()
        main.post {
            playback.listener = object : Playback.Listener {
                override fun onStateChanged() {
                    if (playback.state == Playback.State.PLAYING) playing.countDown()
                }

                override fun onAdvanced() = advanced.countDown()

                override fun onEnded() = ended.countDown()

                override fun onError(message: String, transient: Boolean) {
                    errors += message
                }
            }
            playback.load(url(1), 0, playing = true)
            playback.queue(url(2))
        }

        assertTrue("Never played; errors: $errors", playing.await(10, TimeUnit.SECONDS))
        assertTrue("Didn't move on to the queued item; errors: $errors", advanced.await(10, TimeUnit.SECONDS))
        assertTrue("Didn't end; errors: $errors", ended.await(10, TimeUnit.SECONDS))
        assertEquals(emptyList<String>(), errors)
    }

    /** A mono 16-bit 44.1 kHz WAV file of silence. */
    private fun silence(millis: Int): ByteArray {
        val rate = 44_100
        val data = rate * 2 * millis / 1000
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data)
        }
        return ByteArrayOutputStream().apply {
            write(header.array())
            write(ByteArray(data))
        }.toByteArray()
    }
}
