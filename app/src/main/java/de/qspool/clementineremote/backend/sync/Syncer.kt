package de.qspool.clementineremote.backend.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** A rule, with the songs of it on the phone, and how its last sync went. */
data class KeptRule(
    val rule: SyncRule,
    val songs: Int,
    val bytes: Long,
    /** Null until it syncs while the app runs. */
    val result: RuleResult?,
)

/**
 * Syncs the rules for the connected Clementine while the app is connected to it: once when it
 * connects, and when asked ([syncNow]), such as after a rule is made ([keep]) or removed
 * ([remove]). Background sync comes later.
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

    private val _kept = MutableStateFlow<List<KeptRule>>(emptyList())

    /**
     * The rules for the Clementine connected, or the last one, in their order: read again when
     * one is made or removed, and after each sync.
     */
    @JvmStatic
    val kept: StateFlow<List<KeptRule>> = _kept.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var following: Job? = null
    private var again = false
    private val context: Context get() = App.getApp()
    private var store: SyncStore? = null

    /** The store for rules and synced songs, opened once. */
    @JvmStatic
    @Synchronized
    fun store(context: Context): SyncStore =
        store ?: SyncStore(context.applicationContext).also { store = it }

    /** Follows the connection the service opens, and syncs when Clementine can. */
    @JvmStatic
    fun attach(connection: ClementinePlayerConnection) {
        // Each connection replaces the last.
        following?.cancel()
        // Syncs when Clementine becomes able to, not each time something else about it changes.
        var wasAvailable = false
        following = RemoteRepository.syncing
            .onEach {
                if (it.supported) {
                    App.getPreferences().edit { putString(SharedPreferencesKeys.SP_SYNC_SERVER_ID, it.serverId) }
                    readKept()
                }
                if (it.available && !wasAvailable) syncNow(connection)
                wasAvailable = it.available
            }
            .launchIn(scope)
    }

    /** The Clementine the rules shown are for: the one connected, or the last one that synced. */
    private fun serverId(): String =
        RemoteRepository.syncing.value.serverId.ifEmpty {
            App.getPreferences().getString(SharedPreferencesKeys.SP_SYNC_SERVER_ID, null).orEmpty()
        }

    /** Reads the rules again, for [kept], such as when they're first shown. */
    @JvmStatic
    fun refresh() {
        scope.launch { readKept() }
    }

    private fun readKept() {
        val serverId = serverId()
        if (serverId.isEmpty()) return
        val store = store(context)
        val results = _status.value.results
        _kept.value = store.rules(serverId).map { rule ->
            val songs = store.ruleItems(rule.id)
            KeptRule(rule, songs.size, songs.sumOf { it.sizeBytes }, results[rule.id])
        }
    }

    /**
     * Keeps [rules] on the phone, for the Clementine connected, and syncs them. A rule for a
     * source already kept is left as it is.
     */
    @JvmStatic
    fun keep(rules: List<SyncRule>) {
        scope.launch {
            val store = store(context)
            for (rule in rules) {
                val kept = store.rules(rule.serverId)
                if (kept.none { it.sourceId == rule.sourceId }) {
                    store.addRule(rule.copy(position = (kept.maxOfOrNull { it.position } ?: -1) + 1))
                }
            }
            readKept()
            syncNow()
        }
    }

    /** Stops keeping a rule, and deletes its songs that nothing else kept has. */
    @JvmStatic
    fun remove(ruleId: Long) {
        scope.launch {
            val store = store(context)
            val rule = store.rule(ruleId) ?: return@launch
            store.removeRule(ruleId)
            removeUnwanted(store, rule.serverId) { uri: Uri -> context.contentResolver.delete(uri, null, null) }
            _status.value = _status.value.copy(results = _status.value.results - ruleId)
            readKept()
        }
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
        readKept()
    }

    private const val TAG = "Syncer"
}
