package de.qspool.clementineremote.backend.sync

import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSyncManifest
import de.qspool.clementineremote.backend.streaming.Renderer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL

/**
 * The connected Clementine, for [SyncEngine]: manifests are asked for over the remote's
 * connection ([send], answered on [manifests]), and songs are fetched from its HTTP server, on
 * the same address and port ([server]).
 */
class ClementineSyncConnection(
    override val serverId: String,
    private val send: (ClementineMessage) -> Unit,
    private val manifests: Flow<ResponseSyncManifest>,
    private val server: () -> InetSocketAddress?,
    private val timeoutMs: Long = MANIFEST_TIMEOUT_MS,
) : SyncConnection {

    override fun manifest(request: RequestSyncManifest): ResponseSyncManifest = runBlocking {
        // Listening before asking, so the answer can't come first.
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            manifests.first { it.sourceId == request.sourceId }
        }
        send(
            ClementineMessage(
                ClementineMessage.getMessageBuilder(MsgType.REQUEST_SYNC_MANIFEST)
                    .setRequestSyncManifest(request)
            )
        )
        try {
            withTimeout(timeoutMs) { answer.await() }
        } catch (e: TimeoutCancellationException) {
            answer.cancel()
            throw IOException("Clementine didn't answer for ${request.sourceId}")
        }
    }

    override fun open(path: String): InputStream {
        val url = Renderer.resolve(path, server()) ?: throw IOException("Not connected")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("Clementine answered $code for $path")
        }
        return connection.inputStream
    }

    private companion object {
        /** Resolving a big artist or playlist takes Clementine a moment. */
        const val MANIFEST_TIMEOUT_MS = 60_000L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
