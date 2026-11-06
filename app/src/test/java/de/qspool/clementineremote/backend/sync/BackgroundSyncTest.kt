package de.qspool.clementineremote.backend.sync

import android.content.Context
import androidx.core.content.edit
import androidx.work.NetworkType
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.downloader.DownloadNetwork
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ServerFeature
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncSourceKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** When a background sync may run, which Clementine it syncs with, and its own connection to it. */
@RunWith(RobolectricTestRunner::class)
class BackgroundSyncTest {

    private val preferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("background-sync-test", Context.MODE_PRIVATE)
    private var server: ServerSocket? = null

    @After
    fun tearDown() {
        server?.close()
    }

    @Test
    fun runsOnlyWhenThePhoneIsFitForWorkThatCanWait() {
        val wifi = BackgroundSync.constraints(DownloadNetwork.WIFI_ONLY)
        assertEquals(NetworkType.UNMETERED, wifi.requiredNetworkType)
        assertTrue(wifi.requiresCharging())
        assertTrue(wifi.requiresBatteryNotLow())
        assertTrue(wifi.requiresStorageNotLow())
        assertTrue(wifi.requiresDeviceIdle())

        // The networks follow Download over.
        assertEquals(NetworkType.NOT_ROAMING, BackgroundSync.constraints(DownloadNetwork.MOBILE_DATA).requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, BackgroundSync.constraints(DownloadNetwork.ANY).requiredNetworkType)
    }

    @Test
    fun syncsWithTheLastClementineThatSynced() {
        assertNull(BackgroundSync.lastClementine(preferences))

        preferences.edit {
            putString(SharedPreferencesKeys.SP_KEY_IP, "192.168.1.20")
            putInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, 12345)
        }
        // Connected, but never to a Clementine that syncs.
        assertNull(BackgroundSync.lastClementine(preferences))

        preferences.edit { putString(SharedPreferencesKeys.SP_SYNC_SERVER_ID, SERVER) }
        assertEquals(
            BackgroundSync.LastClementine("192.168.1.20", 5500, 12345, SERVER),
            BackgroundSync.lastClementine(preferences),
        )
        preferences.edit { putString(SharedPreferencesKeys.SP_KEY_PORT, "5600") }
        assertEquals(5600, BackgroundSync.lastClementine(preferences)!!.port)
    }

    @Test
    fun syncsOnlyRulesThatWouldSync() {
        fun rule(policy: RefreshPolicy, lastSyncedAt: Long) = SyncRule(
            serverId = SERVER, sourceId = "playlist/1", title = "Mix", kind = SyncSourceKind.SYNC_SOURCE_KIND_PLAYLIST,
            refreshPolicy = policy, lastSyncedAt = lastSyncedAt,
        )
        assertFalse(BackgroundSync.hasRulesToSync(emptyList()))
        // Kept as it is, with its songs.
        assertFalse(BackgroundSync.hasRulesToSync(listOf(rule(RefreshPolicy.NEVER, 1))))
        // Kept as it is, still to get its songs.
        assertTrue(BackgroundSync.hasRulesToSync(listOf(rule(RefreshPolicy.NEVER, 0))))
        assertTrue(BackgroundSync.hasRulesToSync(listOf(rule(RefreshPolicy.ON_CONNECT, 1))))
    }

    @Test
    fun syncsOnlyWithTheClementineTheRulesAreFor() {
        fun info(serverId: String, sync: Boolean) = ResponseClementineInfo.newBuilder()
            .setServerId(serverId)
            .apply { if (sync) addFeatures(ServerFeature.SERVER_FEATURE_SYNC) }
            .build()
        assertTrue(BackgroundSync.isTheOneToSync(info(SERVER, sync = true), SERVER))
        assertFalse(BackgroundSync.isTheOneToSync(info("another", sync = true), SERVER))
        // Started without media sync.
        assertFalse(BackgroundSync.isTheOneToSync(info(SERVER, sync = false), SERVER))
    }

    /** A Clementine on [server] that answers a connect with [answer], and a manifest request with an empty manifest. */
    private fun clementine(answer: Message.Builder) {
        val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        server = socket
        thread(isDaemon = true) {
            try {
                serve(socket, answer)
            } catch (e: IOException) {
                // The app hung up.
            }
        }
    }

    private fun serve(socket: ServerSocket, answer: Message.Builder) {
        socket.accept().use { client ->
            val input = DataInputStream(client.getInputStream())
            val output = DataOutputStream(client.getOutputStream())
            fun send(message: Message.Builder) {
                val bytes = message.setVersion(Message.getDefaultInstance().version).build().toByteArray()
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
            }
            while (true) {
                val bytes = ByteArray(input.readInt())
                input.readFully(bytes)
                val message = Message.parseFrom(bytes)
                when (message.type) {
                    MsgType.CONNECT -> send(answer)
                    MsgType.REQUEST_SYNC_MANIFEST -> send(
                        Message.newBuilder().setType(MsgType.SYNC_MANIFEST).setResponseSyncManifest(
                            ResponseSyncManifest.newBuilder().setSourceId(message.requestSyncManifest.sourceId)
                        )
                    )
                    MsgType.DISCONNECT -> return
                    else -> {}
                }
            }
        }
    }

    @Test
    fun connectsOnItsOwnAndAsksForManifests() {
        clementine(
            Message.newBuilder().setType(MsgType.INFO).setResponseClementineInfo(
                ResponseClementineInfo.newBuilder().setServerId(SERVER).addFeatures(ServerFeature.SERVER_FEATURE_SYNC)
            )
        )

        val connection = BackgroundConnection.open("127.0.0.1", server!!.localPort, 12345, timeoutMs = 5000)!!
        connection.use {
            assertEquals(SERVER, it.info.serverId)
            val sync = it.syncConnection()
            assertEquals(SERVER, sync.serverId)
            val manifest = sync.manifest(RequestSyncManifest.newBuilder().setSourceId("playlist/1").build())
            assertEquals("playlist/1", manifest.sourceId)
        }
    }

    @Test
    fun givesUpWhenClementineRefusesTheCode() {
        clementine(
            Message.newBuilder().setType(MsgType.DISCONNECT).setResponseDisconnect(
                ResponseDisconnect.newBuilder().setReasonDisconnect(ReasonDisconnect.Wrong_Auth_Code)
            )
        )
        assertNull(BackgroundConnection.open("127.0.0.1", server!!.localPort, 1, timeoutMs = 5000))
    }

    @Test
    fun givesUpWhenNothingIsThere() {
        val free = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        assertNull(BackgroundConnection.open("127.0.0.1", free, 12345, timeoutMs = 5000))
    }

    private companion object {
        const val SERVER = "4f1c2d6e-8a0b-4c8e-9b7a-2e3f5d6c7b8a"
    }
}
