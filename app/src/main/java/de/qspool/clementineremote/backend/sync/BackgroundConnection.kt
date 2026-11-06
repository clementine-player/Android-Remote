package de.qspool.clementineremote.backend.sync

import de.qspool.clementineremote.backend.ClementineSimpleConnection
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSyncManifest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import kotlin.concurrent.thread

/**
 * A connection of a background sync's own to a Clementine, while the app isn't connected: it
 * asks for manifests over it and nothing more, and leaves the app's connection, its player and
 * its notifications alone. [info] is what Clementine said it is when it connected.
 */
internal class BackgroundConnection private constructor(
    private val connection: Connection,
    val info: ResponseClementineInfo,
    private val manifests: MutableSharedFlow<ResponseSyncManifest>,
) : Closeable {

    /** For [SyncEngine]: songs come from Clementine's HTTP server, on the same address. */
    fun syncConnection(): SyncConnection = ClementineSyncConnection(
        info.serverId, { connection.sendRequest(it) }, manifests, connection::getServerAddress,
    )

    override fun close() {
        connection.disconnect(ClementineMessage.getMessage(MsgType.DISCONNECT))
        connection.close()
    }

    /** A connection that can be closed from here, even when saying goodbye fails. */
    private class Connection : ClementineSimpleConnection() {
        @Synchronized
        fun close() {
            // Without streams, it never connected.
            if (mOut != null && !mSocket.isClosed) closeSocket()
        }
    }

    companion object {
        /** How long a read waits before checking the connection is still open. */
        private const val READ_TIMEOUT_MS = 3000

        /**
         * Connects to Clementine at [host] and [port] with [authCode], and waits up to
         * [timeoutMs] for it to say what it is. Null when it isn't there, refuses the code or
         * doesn't answer.
         */
        fun open(host: String, port: Int, authCode: Int, timeoutMs: Long = 15_000): BackgroundConnection? {
            val connection = Connection()
            // Not a downloader: Clementine tells only other remotes what it is.
            val connect = ClementineMessageFactory.buildConnectMessage(host, port, authCode, false, false)
            if (!connection.createConnection(connect)) {
                connection.close()
                return null
            }
            val manifests = MutableSharedFlow<ResponseSyncManifest>(
                extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
            val info = CompletableDeferred<ResponseClementineInfo?>()
            thread(name = "BackgroundConnection", isDaemon = true) {
                while (connection.isConnected) {
                    val message = connection.getProtoc(READ_TIMEOUT_MS)
                    if (message.isErrorMessage) {
                        if (message.errorMessage == ErrorMessage.TIMEOUT) continue
                        break
                    }
                    when (message.messageType) {
                        MsgType.INFO -> info.complete(message.message.responseClementineInfo)
                        MsgType.SYNC_MANIFEST -> manifests.tryEmit(message.message.responseSyncManifest)
                        // Such as for a wrong code.
                        MsgType.DISCONNECT -> break
                        else -> {}
                    }
                }
                connection.close()
                info.complete(null)
            }
            val answer = runBlocking { withTimeoutOrNull(timeoutMs) { info.await() } }
            if (answer == null) {
                connection.close()
                return null
            }
            return BackgroundConnection(connection, answer, manifests)
        }
    }
}
