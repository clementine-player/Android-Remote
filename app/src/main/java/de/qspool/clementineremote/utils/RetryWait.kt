package de.qspool.clementineremote.utils

import android.content.res.Resources
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect

/** How long Clementine said to wait before it checks an auth code from this phone again. */
object RetryWait {
    /**
     * The wait in [disconnect], for people: in seconds under a minute, and otherwise rounded up
     * to whole minutes or hours, so it's never over before it's said. "10 seconds", "3 minutes",
     * "1 hour", or "a while" if Clementine didn't say.
     */
    @JvmStatic
    fun describe(resources: Resources, disconnect: ResponseDisconnect): String {
        if (!disconnect.hasRetryAfterSeconds() || disconnect.retryAfterSeconds <= 0) {
            return resources.getString(R.string.wait_a_while)
        }
        val seconds = disconnect.retryAfterSeconds
        if (seconds < 60) {
            return resources.getQuantityString(R.plurals.wait_seconds, seconds, seconds)
        }
        val minutes = (seconds + 59) / 60
        if (minutes <= 60) {
            return resources.getQuantityString(R.plurals.wait_minutes, minutes, minutes)
        }
        val hours = (minutes + 59) / 60
        return resources.getQuantityString(R.plurals.wait_hours, hours, hours)
    }
}
