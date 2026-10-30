package de.qspool.clementineremote.backend.downloader

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import de.qspool.clementineremote.SharedPreferencesKeys

/**
 * The networks songs download over, chosen under Download over in Settings. A Wi-Fi network
 * that's metered, such as another phone's hotspot, counts as mobile data, as Android reports it.
 */
enum class DownloadNetwork(val value: String) {
    WIFI_ONLY("wifi"),

    /** Wi-Fi and mobile data, but not mobile data while roaming. */
    MOBILE_DATA("mobile"),
    ANY("any");

    /**
     * Whether the phone is on a network this allows. VPNs are left out: they run over another
     * network, which is the one that counts.
     */
    fun allowsCurrentNetwork(context: Context): Boolean {
        if (this == ANY) return true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        @Suppress("DEPRECATION")
        return manager.allNetworks.any { network ->
            val capabilities = manager.getNetworkCapabilities(network)
            capabilities != null && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                allows(
                    unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                    roaming = roaming(manager, network, capabilities),
                )
        }
    }

    /** Whether this allows a network that's [unmetered], or not, and [roaming], or not. */
    fun allows(unmetered: Boolean, roaming: Boolean): Boolean = when (this) {
        WIFI_ONLY -> unmetered
        MOBILE_DATA -> !roaming
        ANY -> true
    }

    companion object {
        /**
         * The choice in [preferences]. Before there was one, a switch for Wi-Fi only, off by
         * default, so that decides until a choice is made.
         */
        @JvmStatic
        fun chosen(preferences: SharedPreferences): DownloadNetwork {
            val value = preferences.getString(SharedPreferencesKeys.SP_DOWNLOAD_NETWORK, null)
            return entries.firstOrNull { it.value == value }
                ?: if (preferences.getBoolean(SharedPreferencesKeys.SP_WIFI_ONLY, false)) WIFI_ONLY else ANY
        }

        private fun roaming(manager: ConnectivityManager, network: Network, capabilities: NetworkCapabilities): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            } else {
                @Suppress("DEPRECATION")
                manager.getNetworkInfo(network)?.isRoaming == true
            }
    }
}
