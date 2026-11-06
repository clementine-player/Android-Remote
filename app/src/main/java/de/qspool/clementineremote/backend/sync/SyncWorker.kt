package de.qspool.clementineremote.backend.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.downloader.DownloadNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A background sync ([BackgroundSync]): connects to the last Clementine that synced, syncs its
 * rules and disconnects. It always succeeds, so WorkManager waits for the next time rather than
 * retrying: Clementine not being on is the usual case, not a failure. When Android stops it,
 * after 10 minutes at most, what has downloaded stays, and the next run carries on.
 */
class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        sync()
        Result.success()
    }

    private suspend fun sync() {
        val preferences = App.getPreferences()
        if (!BackgroundSync.enabled(preferences)) return
        // The app is connected, and its connection syncs.
        if (App.ClementineConnection?.isConnected == true) {
            Syncer.syncNow()
            return
        }
        val last = BackgroundSync.lastClementine(preferences) ?: return
        if (!BackgroundSync.hasRulesToSync(Syncer.store(applicationContext).rules(last.serverId))) return
        if (!DownloadNetwork.chosen(preferences).allowsCurrentNetwork(applicationContext)) return

        val connection = BackgroundConnection.open(last.host, last.port, last.authCode)
        if (connection == null) {
            Log.i(TAG, "Clementine isn't reachable at ${last.host}:${last.port}")
            return
        }
        connection.use {
            if (!BackgroundSync.isTheOneToSync(it.info, last.serverId)) {
                Log.i(TAG, "Another Clementine, or one that doesn't sync, is at ${last.host}")
                return
            }
            Syncer.syncInBackground(it.syncConnection(), stopped = { isStopped })
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}
