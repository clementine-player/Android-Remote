package de.qspool.clementineremote.backend.sync

import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSyncManifest
import de.qspool.clementineremote.backend.streaming.Renderer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
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
    private val isConnected: () -> Boolean = { true },
    private val disconnected: Flow<Boolean> = emptyFlow(),
) : SyncConnection {

    override fun manifest(request: RequestSyncManifest): ResponseSyncManifest = runBlocking {
        // Fail fast when already offline instead of blocking MANIFEST_TIMEOUT_MS per rule.
        if (!isConnected()) {
            throw IOException("Not connected")
        }
        // Listening before asking, so the answer can't come first.
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            manifests.first { it.sourceId == request.sourceId }
        }
        // UNDISPATCHED subscribes before send(); awaitCancellation() keeps an empty/completed
        // disconnect flow (such as the default emptyFlow()) from winning select().
        val disconnect = async(start = CoroutineStart.UNDISPATCHED) {
            if (disconnected.firstOrNull { it } != true) awaitCancellation()
        }
        send(
            ClementineMessage(
                ClementineMessage.getMessageBuilder(MsgType.REQUEST_SYNC_MANIFEST)
                    .setRequestSyncManifest(request)
            )
        )
        try {
            withTimeout(timeoutMs) {
                // Race manifest arrival against connection loss so disconnects abort immediately.
                select {
                    answer.onAwait {
                        disconnect.cancel()
                        it
                    }
                    disconnect.onAwait {
                        answer.cancel()
                        throw IOException("Disconnected waiting for ${request.sourceId}")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            answer.cancel()
            disconnect.cancel()
            throw IOException("Clementine didn't answer for ${request.sourceId}")
        }
    }

    override fun open(path: String): InputStream = open(path, offset = 0L, ifRange = null).stream

    override fun open(path: String, offset: Long, ifRange: String?): OpenedSyncStream {
        val url = Renderer.resolve(path, server()) ?: throw IOException("Not connected")
        val connection = openHttp(url, offset, ifRange)
        val code = connection.responseCode
        if (offset > 0 && code == 416) {
            // Range Not Satisfiable (file shrunk on server): retry from byte 0.
            connection.disconnect()
            val retry = openHttp(url, offset = 0L, ifRange = null)
            val retryCode = retry.responseCode
            if (retryCode != HttpURLConnection.HTTP_OK) {
                retry.disconnect()
                throw IOException("Clementine answered $retryCode for $path")
            }
            return OpenedSyncStream(retry.inputStream, partial = false, etag = retry.getHeaderField("ETag"))
        }
        if (code == HttpURLConnection.HTTP_PARTIAL && offset > 0) {
            // Confirm the server resumed at our exact offset before appending to a .part file.
            val contentRange = connection.getHeaderField("Content-Range")
            if (contentRange != null && contentRange.startsWith("bytes $offset-")) {
                return OpenedSyncStream(
                    connection.inputStream,
                    partial = true,
                    etag = connection.getHeaderField("ETag"),
                )
            }
            // Unexpected Content-Range: retry cleanly from 0 rather than appending corrupt bytes.
            connection.disconnect()
            val retry = openHttp(url, offset = 0L, ifRange = null)
            val retryCode = retry.responseCode
            if (retryCode != HttpURLConnection.HTTP_OK) {
                retry.disconnect()
                throw IOException("Clementine answered $retryCode for $path")
            }
            return OpenedSyncStream(retry.inputStream, partial = false, etag = retry.getHeaderField("ETag"))
        }
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("Clementine answered $code for $path")
        }
        return OpenedSyncStream(
            connection.inputStream,
            partial = false,
            etag = connection.getHeaderField("ETag"),
        )
    }

    private fun openHttp(url: String, offset: Long, ifRange: String?): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=$offset-")
            if (!ifRange.isNullOrBlank()) {
                connection.setRequestProperty("If-Range", ifRange)
            }
        }
        return connection
    }

    private companion object {
        /** Resolving a big artist or playlist takes Clementine a moment. */
        const val MANIFEST_TIMEOUT_MS = 60_000L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
