package de.qspool.clementineremote.backend.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.qspool.clementineremote.App
import de.qspool.clementineremote.BuildConfig
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.downloader.DownloadNetwork
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ServerFeature
import java.util.concurrent.TimeUnit

/**
 * Updates what's kept on the phone in the background, with Update kept music in the background
 * on: [SyncWorker] runs every 6 hours or so, when Android sees fit for work that can wait. That's
 * while the phone is charging, idle, with battery and storage to spare, and on a network Download
 * over allows, so mostly overnight on home Wi-Fi. It syncs the rules for the last Clementine that
 * synced, at its last address.
 */
object BackgroundSync {

    private const val WORK = "background-sync"
    private const val INTERVAL_HOURS = 6L

    /** The settings that change what's scheduled. */
    private val SETTINGS = setOf(
        SharedPreferencesKeys.SP_BACKGROUND_SYNC, SharedPreferencesKeys.SP_DOWNLOAD_NETWORK,
        SharedPreferencesKeys.SP_WIFI_ONLY,
    )

    // Held here: SharedPreferences keeps only a weak reference to its listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in SETTINGS) schedule(App.getApp())
    }

    /** The last Clementine that synced, where it was. */
    data class LastClementine(val host: String, val port: Int, val authCode: Int, val serverId: String)

    /** Whether background sync is on, in [preferences]. */
    @JvmStatic
    fun enabled(preferences: SharedPreferences): Boolean =
        BuildConfig.TESTING_FEATURES && preferences.getBoolean(SharedPreferencesKeys.SP_BACKGROUND_SYNC, true)

    /** Schedules background sync, and again whenever its settings change. Called once, at startup. */
    @JvmStatic
    fun start(context: Context) {
        if (!BuildConfig.TESTING_FEATURES) return
        schedule(context)
        App.getPreferences().registerOnSharedPreferenceChangeListener(listener)
    }

    private fun schedule(context: Context) {
        val preferences = App.getPreferences()
        val work = WorkManager.getInstance(context)
        if (!enabled(preferences)) {
            work.cancelUniqueWork(WORK)
            return
        }
        work.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints(DownloadNetwork.chosen(preferences)))
                .build(),
        )
    }

    /** When Android may run a background sync, given the networks Download over allows. */
    fun constraints(network: DownloadNetwork): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            when (network) {
                DownloadNetwork.WIFI_ONLY -> NetworkType.UNMETERED
                DownloadNetwork.MOBILE_DATA -> NetworkType.NOT_ROAMING
                DownloadNetwork.ANY -> NetworkType.CONNECTED
            }
        )
        .setRequiresCharging(true)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .setRequiresDeviceIdle(true)
        .build()

    /** The last Clementine that synced, from [preferences], or null if none has. */
    fun lastClementine(preferences: SharedPreferences): LastClementine? {
        val host = preferences.getString(SharedPreferencesKeys.SP_KEY_IP, null)?.takeIf { it.isNotBlank() } ?: return null
        val serverId = preferences.getString(SharedPreferencesKeys.SP_SYNC_SERVER_ID, null)?.takeIf { it.isNotEmpty() }
            ?: return null
        val port = preferences.getString(SharedPreferencesKeys.SP_KEY_PORT, null)?.toIntOrNull() ?: Clementine.DefaultPort
        return LastClementine(host, port, preferences.getInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, 0), serverId)
    }

    /** Whether any of [rules] would sync: those updated each time, and those yet to get their songs. */
    fun hasRulesToSync(rules: List<SyncRule>): Boolean =
        rules.any { it.refreshPolicy != RefreshPolicy.NEVER || it.lastSyncedAt == 0L }

    /**
     * Whether the Clementine that answered, saying [info], is the one the rules are for
     * ([serverId]) and syncs. Another one at the same address is left alone.
     */
    fun isTheOneToSync(info: ResponseClementineInfo, serverId: String): Boolean =
        info.serverId == serverId && ServerFeature.SERVER_FEATURE_SYNC in info.featuresList
}
