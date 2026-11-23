package de.qspool.clementineremote.backend.downloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/** Download over: which networks each choice allows, and the choice read from the settings. */
@RunWith(RobolectricTestRunner::class)
class DownloadNetworkTest {

    private val preferences get() = App.getPreferences()

    @Before
    fun clear() {
        preferences.edit(commit = true) { clear() }
    }

    @Test
    fun wifiOnlyAllowsOnlyNetworksThatArentMetered() {
        assertTrue(DownloadNetwork.WIFI_ONLY.allows(unmetered = true, roaming = false))
        assertFalse(DownloadNetwork.WIFI_ONLY.allows(unmetered = false, roaming = false))
        assertFalse(DownloadNetwork.WIFI_ONLY.allows(unmetered = false, roaming = true))
    }

    @Test
    fun mobileDataAllowsAnyNetworkButRoaming() {
        assertTrue(DownloadNetwork.MOBILE_DATA.allows(unmetered = true, roaming = false))
        assertTrue(DownloadNetwork.MOBILE_DATA.allows(unmetered = false, roaming = false))
        assertFalse(DownloadNetwork.MOBILE_DATA.allows(unmetered = false, roaming = true))
    }

    @Test
    fun anyNetworkAllowsRoaming() {
        assertTrue(DownloadNetwork.ANY.allows(unmetered = false, roaming = true))
    }

    @Test
    fun theOldWifiOnlySwitchDecidesUntilAChoiceIsMade() {
        assertEquals(DownloadNetwork.ANY, DownloadNetwork.chosen(preferences))

        preferences.edit(commit = true) { putBoolean(SharedPreferencesKeys.SP_WIFI_ONLY, true) }
        assertEquals(DownloadNetwork.WIFI_ONLY, DownloadNetwork.chosen(preferences))

        preferences.edit(commit = true) {
            putString(SharedPreferencesKeys.SP_DOWNLOAD_NETWORK, DownloadNetwork.MOBILE_DATA.value)
        }
        assertEquals(DownloadNetwork.MOBILE_DATA, DownloadNetwork.chosen(preferences))
    }

    @Test
    fun anUnknownChoiceFallsBackToTheOldSwitch() {
        preferences.edit(commit = true) {
            putBoolean(SharedPreferencesKeys.SP_WIFI_ONLY, true)
            putString(SharedPreferencesKeys.SP_DOWNLOAD_NETWORK, "satellite")
        }

        assertEquals(DownloadNetwork.WIFI_ONLY, DownloadNetwork.chosen(preferences))
    }

    @Suppress("DEPRECATION")
    @Test
    fun wifiOnlyRequiresInternetCapabilityAndSkipsVpnToAllowUnmeteredWifi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(manager).clearAllNetworks()

        // An IMS-like unmetered network without NET_CAPABILITY_INTERNET is rejected.
        val ims = ShadowNetwork.newInstance(101)
        val imsInfo = ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED,
        )
        shadowOf(manager).addNetwork(ims, imsInfo)
        val imsCaps = ShadowNetworkCapabilities.newInstance()
        shadowOf(imsCaps).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        shadowOf(imsCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
        shadowOf(imsCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        shadowOf(manager).setNetworkCapabilities(ims, imsCaps)

        assertFalse(DownloadNetwork.WIFI_ONLY.allowsCurrentNetwork(context))

        // Add an unmetered internet-capable Wi-Fi network; a VPN being active still allows Wi-Fi.
        val wifi = ShadowNetwork.newInstance(102)
        shadowOf(manager).addNetwork(wifi, imsInfo)
        val wifiCaps = ShadowNetworkCapabilities.newInstance()
        shadowOf(wifiCaps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(wifiCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(wifiCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        shadowOf(manager).setNetworkCapabilities(wifi, wifiCaps)

        val vpn = ShadowNetwork.newInstance(103)
        shadowOf(manager).addNetwork(vpn, imsInfo)
        val vpnCaps = ShadowNetworkCapabilities.newInstance()
        shadowOf(vpnCaps).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        shadowOf(vpnCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(vpn, vpnCaps)

        assertTrue(DownloadNetwork.WIFI_ONLY.allowsCurrentNetwork(context))
    }
}
