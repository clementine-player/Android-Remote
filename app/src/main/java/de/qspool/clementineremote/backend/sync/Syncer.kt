package de.qspool.clementineremote.backend.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.ClementinePlayerConnection
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.streaming.ThisRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Syncs the rules for the connected Clementine while the app is connected to it: once when it
 * connects, and when asked ([syncNow]), such as after a rule is made or removed. Background sync
 * comes later.
 */
object Syncer {

    /** Where syncing stands. */
    data class Status(
        val running: Boolean = false,
        /** The last sync's outcome for each rule, by rule id, until the next one. */
        val results: Map<Long, RuleResult> = emptyMap(),
        /** The last sync couldn't start: there's nowhere to save songs, say. */
        val unavailable: Boolean = false,
    )

    private val _status = MutableStateFlow(Status())

    @JvmStatic
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var again = false
    private lateinit var context: Context
    private var store: SyncStore? = null

    /** The store for rules and synced songs, opened once. */
    @JvmStatic
    @Synchronized
    fun store(context: Context): SyncStore =
        store ?: SyncStore(context.applicationContext).also { store = it }

    /** Follows the connection the service opens, and syncs when Clementine can. */
    @JvmStatic
    fun attach(context: Context, connection: ClementinePlayerConnection) {
        this.context = context.applicationContext
        RemoteRepository.syncing
            .map { it.available }
            .distinctUntilChanged()
            .onEach { if (it) syncNow(connection) }
            .launchIn(scope)
    }

    /** Syncs now, or once more after the sync that's running. */
    @JvmStatic
    @Synchronized
    fun syncNow(connection: ClementinePlayerConnection? = null) {
        if (job?.isActive == true) {
            again = true
            return
        }
        val conn = connection ?: App.ClementineConnection ?: return
        job = scope.launch {
            do {
                synchronized(this@Syncer) { again = false }
                runOnce(conn)
            } while (synchronized(this@Syncer) { again })
        }
    }

    private fun runOnce(connection: ClementinePlayerConnection) {
        val syncing = RemoteRepository.syncing.value
        if (!syncing.available) return
        val storage = DownloadManager.getInstance(context).createStorage()
        if (storage == null) {
            _status.value = _status.value.copy(unavailable = true)
            return
        }
        _status.value = _status.value.copy(running = true, unavailable = false)
        val engine = SyncEngine(
            store(context), storage,
            delete = { uri: Uri -> context.contentResolver.delete(uri, null, null) },
            supportedFormats = ThisRenderer.capabilities(context).formatsList,
        )
        try {
            val result = engine.sync(
                ClementineSyncConnection(
                    syncing.serverId, RemoteRepository::send, RemoteRepository.syncManifests,
                    connection::getServerAddress,
                )
            )
            _status.value = _status.value.copy(running = false, results = result.rules)
        } catch (e: RuntimeException) {
            // The store or storage failing: try again next time rather than stop the app.
            Log.w(TAG, "Sync failed", e)
            _status.value = _status.value.copy(running = false, unavailable = true)
        }
    }

    private const val TAG = "Syncer"
}
