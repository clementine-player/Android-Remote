package de.qspool.clementineremote.backend.sync

import com.sun.net.httpserver.HttpServer
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSyncManifest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress

/** Asking the connected Clementine for manifests, and fetching songs from it. */
@RunWith(RobolectricTestRunner::class)
class ClementineSyncConnectionTest {

    private val manifests = MutableSharedFlow<ResponseSyncManifest>(
        extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val sent = mutableListOf<ClementineMessage>()
    private var server: HttpServer? = null

    @After
    fun tearDown() {
        server?.stop(0)
    }

    private fun connection(
        answer: (RequestSyncManifest) -> List<ResponseSyncManifest>,
        address: InetSocketAddress? = null,
    ) = ClementineSyncConnection(
        "abc",
        send = { message ->
            sent += message
            // Clementine answers on the connection's thread, as soon as it's asked.
            answer(message.message.requestSyncManifest).forEach { manifests.tryEmit(it) }
        },
        manifests = manifests,
        server = { address },
        timeoutMs = 200,
    )

    private fun manifest(sourceId: String) = ResponseSyncManifest.newBuilder().setSourceId(sourceId).build()

    @Test
    fun asksForAManifestAndWaitsForItsAnswer() {
        val request = RequestSyncManifest.newBuilder().setSourceId("playlist/1").build()
        // Another rule's answer, from an earlier request, comes first.
        val answer = connection({ listOf(manifest("playlist/9"), manifest(it.sourceId)) }).manifest(request)

        assertEquals("playlist/1", answer.sourceId)
        assertEquals(MsgType.REQUEST_SYNC_MANIFEST, sent.single().messageType)
        assertEquals(request, sent.single().message.requestSyncManifest)
    }

    @Test
    fun givesUpWhenClementineDoesntAnswer() {
        assertThrows(IOException::class.java) {
            connection({ emptyList() }).manifest(RequestSyncManifest.newBuilder().setSourceId("playlist/1").build())
        }
    }

    @Test
    fun fetchesPathsFromClementinesAddress() {
        val http = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        http.createContext("/d/") { exchange ->
            val body = exchange.requestURI.path.toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        http.createContext("/gone") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        http.start()
        server = http
        val conn = connection({ emptyList() }, InetSocketAddress(InetAddress.getLoopbackAddress(), http.address.port))

        assertEquals("/d/token/1", conn.open("/d/token/1").use { String(it.readBytes()) })
        assertThrows(IOException::class.java) { conn.open("/gone") }
    }

    @Test
    fun cantFetchWhileDisconnected() {
        assertThrows(IOException::class.java) { connection({ emptyList() }).open("/d/token/1") }
    }
}
