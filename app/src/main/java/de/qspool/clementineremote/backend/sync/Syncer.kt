package de.qspool.clementineremote.backend.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.ClementinePlayerConnection
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.downloader.DownloadNetwork
import de.qspool.clementineremote.backend.streaming.ThisRenderer
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A rule, with the songs of it on the phone, and how its last sync went. */
data class KeptRule(
    val rule: SyncRule,
    val songs: Int,
    val bytes: Long,
    /** Null until it syncs while the app runs. */
    val result: RuleResult?,
    /** How far the sync running has got with downloading its songs, if it has started. */
    val downloading: SyncProgress? = null,
)

/** [done] of the [total] songs a sync has to download. */
data class SyncProgress(val done: Int, val total: Int)

/**
 * Syncs the rules for the connected Clementine while the app is connected to it: once when it
 * connects, and when asked ([syncNow]), such as after a rule is made ([keep]) or removed
 * ([remove]), over the networks Download over allows. [SyncWorker] syncs in the background, over a
 * connection of its own ([syncInBackground]).
 */
object Syncer {

    /** Where syncing stands. */
    data class Status(
        val running: Boolean = false,
        /** The last sync's outcome for each rule, by rule id, until the next one. */
        val results: Map<Long, RuleResult> = emptyMap(),
        /** The last sync couldn't start: there's nowhere to save songs, say. */
        val unavailable: Boolean = false,
        /** The last sync didn't start, because Download over doesn't allow the phone's network. */
        val networkNotAllowed: Boolean = false,
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
    // Lock hierarchy: syncMutex (outer, held over a sync/cleanup pass) -> storeMutex (inner,
    // short-lived SQLite + _kept snapshot updates only; never held across network/MediaStore I/O).
    private val storeMutex = Mutex()
    private val syncMutex = Mutex()
    // Removed (ruleId, sourceId) pairs so an active sync stops that deleted rule without matching
    // a newly added rule that reused its SQLite rowid.
    private val cancelledRunRules =
        Collections.newSetFromMap(ConcurrentHashMap<Pair<Long, String>, Boolean>())
    // Servers awaiting removeUnwantedOutsideLock after rule removal once syncMutex is available.
    private val cleanupServers = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private var job: Job? = null
    private var following: Job? = null
    private var again = false
    private val context: Context get() = App.getApp()
    private var store: SyncStore? = null
    // The rules the sync running is downloading songs for, by rule id.
    private val progress = ConcurrentHashMap<Long, SyncProgress>()

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
                    storeMutex.withLock { readKept() }
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
        scope.launch { storeMutex.withLock { readKept() } }
    }

    private fun readKept() {
        val serverId = serverId()
        if (serverId.isEmpty()) return
        val store = store(context)
        val results = _status.value.results
        _kept.value = store.rules(serverId).map { rule ->
            val songs = store.ruleItems(rule.id)
            KeptRule(rule, songs.size, songs.sumOf { it.sizeBytes }, results[rule.id], progress[rule.id])
        }
    }

    /** The songs of [ruleId] on this phone, in its order. Reads the disk. */
    @JvmStatic
    @WorkerThread
    fun songs(ruleId: Long): List<KeptSong> {
        val covers = SyncFiles.coversDir(context)
        return store(context).ruleItems(ruleId).map { KeptSong.of(it, covers) }
    }

    /**
     * Keeps [rules] on the phone, for the Clementine connected, and syncs them. A rule for a
     * source already kept is left as it is.
     */
    @JvmStatic
    fun keep(rules: List<SyncRule>) {
        scope.launch {
            storeMutex.withLock {
                val store = store(context)
                for (rule in rules) {
                    val kept = store.rules(rule.serverId)
                    if (kept.none { it.sourceId == rule.sourceId }) {
                        store.addRule(
                            rule.copy(position = (kept.maxOfOrNull { it.position } ?: -1) + 1)
                        )
                    }
                }
                readKept()
            }
            syncNow()
        }
    }

    /** Stops keeping a rule, and deletes its songs that nothing else kept has. */
    @JvmStatic
    fun remove(ruleId: Long) {
        scope.launch {
            val serverId = storeMutex.withLock {
                val store = store(context)
                val rule = store.rule(ruleId) ?: return@withLock null
                // Mark rule stopped and enqueue server cleanup atomically with rule deletion; file
                // deletion runs after storeMutex releases and under syncMutex so an in-flight sync
                // aborts cleanly first and UI reads aren't stalled on MediaStore I/O.
                cancelledRunRules.add(ruleId to rule.sourceId)
                cleanupServers.add(rule.serverId)
                store.removeRule(ruleId)
                _status.update { it.copy(results = it.results - ruleId) }
                readKept()
                rule.serverId
            } ?: return@launch
            cleanupServers.add(serverId)
            drainPendingCleanupWhenIdle()
        }
    }

    private suspend fun drainPendingCleanupWhenIdle() {
        // Use tryLock() so remove() never blocks behind an active sync (which already drains
        // cleanupServers before unlocking); looping catches removals queued right before unlock.
        while (cleanupServers.isNotEmpty() && syncMutex.tryLock()) {
            try {
                drainPendingCleanupLocked()
            } finally {
                syncMutex.unlock()
            }
        }
    }

    private suspend fun drainPendingCleanupLocked() {
        val servers = cleanupServers.toList()
        if (servers.isEmpty()) return
        removeUnwantedOutsideLock(
            store = store(context),
            storeMutex = storeMutex,
            serverIds = servers,
            delete = { uri -> context.contentResolver.delete(uri, null, null) },
            onServerSnapshotted = { cleanupServers.remove(it) },
            onFinishedLocked = ::readKept,
        )
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
        again = false
        job = scope.launch {
            var currentConn: ClementinePlayerConnection? = conn
            while (true) {
                syncMutex.withLock {
                    // Re-read syncing state and active connection each pass in case a reconnect happened.
                    val syncing = RemoteRepository.syncing.value
                    val activeConn = currentConn ?: App.ClementineConnection
                    if (syncing.available && activeConn != null) {
                        runOnce(
                            ClementineSyncConnection(
                                syncing.serverId,
                                RemoteRepository::send,
                                RemoteRepository.syncManifests,
                                activeConn::getServerAddress,
                                isConnected = {
                                    val status = RemoteRepository.connection.value
                                    status == ConnectionStatus.CONNECTED || status == ConnectionStatus.RECONNECTED
                                },
                                disconnected = RemoteRepository.connection.map {
                                    it == ConnectionStatus.DISCONNECTED || it == ConnectionStatus.RECONNECTING
                                },
                            )
                        )
                    }
                    drainPendingCleanupLocked()
                }
                drainPendingCleanupWhenIdle()
                // Check again and clear job under the Syncer lock so a concurrent syncNow() cannot
                // set again = true after we decide to exit and lose its wakeup.
                synchronized(this@Syncer) {
                    if (!again) {
                        job = null
                        return@launch
                    }
                    again = false
                    currentConn = App.ClementineConnection
                }
            }
        }
    }

    /**
     * Syncs over [connection], a background sync's own connection, unless a sync is running
     * already, and returns whether it did. Once [stopped] says so, it downloads nothing more. A
     * sync asked for meanwhile, such as by the app connecting, runs after it.
     */
    suspend fun syncInBackground(connection: SyncConnection, stopped: () -> Boolean): Boolean {
        val started = synchronized(this) {
            if (job?.isActive == true) return false
            again = false
            scope.launch {
                syncMutex.withLock {
                    runOnce(connection, stopped)
                    drainPendingCleanupLocked()
                }
                drainPendingCleanupWhenIdle()
                synchronized(this@Syncer) {
                    job = null
                }
            }.also { job = it }
        }
        started.join()
        // If syncNow() arrived while the background sync held job, start the deferred sync now.
        if (synchronized(this) { again }) syncNow()
        return true
    }

    private suspend fun runOnce(connection: SyncConnection, stopped: () -> Boolean = { false }) {
        if (!DownloadNetwork.chosen(App.getPreferences()).allowsCurrentNetwork(context)) {
            _status.update { it.copy(networkNotAllowed = true) }
            return
        }
        val storage = DownloadManager.getInstance(context).createStorage()
        if (storage == null) {
            _status.update { it.copy(unavailable = true) }
            return
        }
        _status.update { it.copy(running = true, unavailable = false, networkNotAllowed = false) }
        val engine = SyncEngine(
            store(context), storage, SyncFiles.coversDir(context),
            delete = { uri: Uri -> context.contentResolver.delete(uri, null, null) },
            supportedFormats = ThisRenderer.capabilities(context).formatsList,
            onProgress = { ruleId, done, total ->
                val now = SyncProgress(done, total)
                progress[ruleId] = now
                var title: String? = null
                // MutableStateFlow.update may retry on contention: reset title per attempt and post
                // the notification only once after the update succeeds.
                _kept.update { current ->
                    title = null
                    current.map {
                        if (it.rule.id == ruleId) {
                            title = it.rule.title
                            it.copy(downloading = now)
                        } else {
                            it
                        }
                    }
                }
                title?.let { SyncNotification.show(context, it, done, total) }
            },
            stopped = stopped,
            isRuleStopped = { id, sourceId -> cancelledRunRules.contains(id to sourceId) },
        )
        try {
            val result = engine.sync(connection)
            _status.update { it.copy(running = false, results = result.rules) }
        } catch (e: RuntimeException) {
            // The store or storage failing: try again next time rather than stop the app.
            Log.w(TAG, "Sync failed", e)
            _status.update { it.copy(running = false, unavailable = true) }
        } finally {
            cancelledRunRules.clear()
            progress.clear()
            SyncNotification.hide(context)
            // Refresh _kept under storeMutex so UI reflects downloaded counts and cleared progress.
            storeMutex.withLock { readKept() }
        }
    }

    private const val TAG = "Syncer"
}
