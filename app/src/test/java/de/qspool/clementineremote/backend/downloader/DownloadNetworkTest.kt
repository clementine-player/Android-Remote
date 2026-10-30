package de.qspool.clementineremote.backend.downloader

import androidx.core.content.edit
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
