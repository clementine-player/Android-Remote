package de.qspool.clementineremote.backend.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification
import de.qspool.clementineremote.ui.MainActivity

/**
 * While songs download to keep on this phone, a notification says which of the things kept is
 * syncing, and how many of its songs have downloaded, as its row in On this phone does. Tapping it
 * opens On this phone. It goes once the sync ends.
 */
object SyncNotification {

    /** Not the downloads' (129312 and 129313), nor the media notification's. */
    private const val NOTIFICATION_ID = 129314

    /** [title]'s songs are downloading: [done] of [total]. */
    fun show(context: Context, title: String, done: Int, total: Int) {
        // Without the permission to post notifications, Android drops them.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, App.notificationChannel)
            .setSmallIcon(R.drawable.notification)
            // In the header, where the songs go, as the phone player's notification says.
            .setSubText(context.getString(R.string.sync_on_this_phone))
            .setContentTitle(title)
            .setContentText(context.resources.getQuantityString(R.plurals.sync_downloading, total, done, total))
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(onThisPhone(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    /** The sync has ended. */
    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** Opens the On this phone tab, as the downloads' notification does. */
    private fun onThisPhone(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ClementineMediaSessionNotification.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        return PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
