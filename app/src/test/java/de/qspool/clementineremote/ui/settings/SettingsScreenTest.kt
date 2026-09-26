package de.qspool.clementineremote.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.downloader.MediaStoreDownloadStorage
import de.qspool.clementineremote.ui.theme.ClementineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The settings read and write the app's preferences under their old keys, with the old
 * defaults, dependencies and checks.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val preferences get() = App.getPreferences()

    private val opened = mutableListOf<String>()

    private val actions = object : SettingsActions {
        override fun onBack() {
            opened += "back"
        }

        override fun onChooseDownloadDir() {
            opened += "dir"
        }

        override fun onOpenUrl(url: String) {
            opened += url
        }
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                SettingsScreen(rememberPreferenceStore(preferences), actions) { "/music" }
            }
        }
    }

    private fun row(key: String) = compose.onNodeWithTag(key).performScrollTo()

    @Test
    fun switchesShowTheirDefaultsAndWriteTheirKeys() {
        row(SharedPreferencesKeys.SP_KEY_USE_VOLUMEKEYS).assertIsOn().performClick().assertIsOff()
        assertFalse(preferences.getBoolean(SharedPreferencesKeys.SP_KEY_USE_VOLUMEKEYS, true))
        row(SharedPreferencesKeys.SP_KEEP_SCREEN_ON).assertIsOn()
        row(SharedPreferencesKeys.SP_WAKE_LOCK).assertIsOff()
    }

    @Test
    fun callVolumeFollowsLowerVolume() {
        row(SharedPreferencesKeys.SP_CALL_VOLUME).assertIsEnabled()
        row(SharedPreferencesKeys.SP_LOWER_VOLUME).performClick()
        row(SharedPreferencesKeys.SP_CALL_VOLUME).assertIsNotEnabled()
    }

    @Test
    fun albumFoldersNeedArtistFolders() {
        row(SharedPreferencesKeys.SP_DOWNLOAD_PLAYLIST_CRT_ALBUM_DIR).assertIsEnabled()
        row(SharedPreferencesKeys.SP_DOWNLOAD_PLAYLIST_CRT_ARTIST_DIR).performClick()
        row(SharedPreferencesKeys.SP_DOWNLOAD_PLAYLIST_CRT_ALBUM_DIR).assertIsNotEnabled()
    }

    @Test
    fun choiceDialogStoresTheValueAndShowsItsLabel() {
        row(SharedPreferencesKeys.SP_VOLUME_INC).performClick()
        compose.onNodeWithTag("${SharedPreferencesKeys.SP_VOLUME_INC}_5").performScrollTo().performClick()
        assertEquals("5", preferences.getString(SharedPreferencesKeys.SP_VOLUME_INC, null))
        compose.onNode(hasText("5%", substring = true)).assertExists()

        row(SharedPreferencesKeys.SP_CALL_VOLUME).performClick()
        compose.onNodeWithTag("${SharedPreferencesKeys.SP_CALL_VOLUME}_-1").performScrollTo().performClick()
        assertEquals("-1", preferences.getString(SharedPreferencesKeys.SP_CALL_VOLUME, null))

        row(SharedPreferencesKeys.SP_LIBRARY_GROUPING).performClick()
        compose.onNodeWithTag("${SharedPreferencesKeys.SP_LIBRARY_GROUPING}_album").performScrollTo().performClick()
        compose.onNode(hasText("The library is grouped by Album.")).assertExists()
    }

    @Test
    fun portMustBeOneClementineCanListenOn() {
        row(SharedPreferencesKeys.SP_KEY_PORT).performClick()
        compose.onNodeWithTag("portField").performTextReplacement("80")
        compose.onNodeWithTag("btnPortOk").assertIsNotEnabled()
        compose.onNodeWithTag("portField").performTextReplacement("5600")
        compose.onNodeWithTag("btnPortOk").performClick()
        assertEquals("5600", preferences.getString(SharedPreferencesKeys.SP_KEY_PORT, null))
        compose.onNodeWithText("Current Port: 5600").assertExists()
    }

    @Test
    fun downloadsGoToTheMusicCollection() {
        row(SharedPreferencesKeys.SP_DOWNLOAD_DIR).assertIsNotEnabled()
        compose.onNodeWithText(MediaStoreDownloadStorage.BASE_DIR).assertExists()
    }

    @Test
    fun linksAndDialogs() {
        row("pref_version").performClick()
        row("pref_clementine_website").performClick()
        assertEquals(
            listOf("https://github.com/clementine-player/Android-Remote", "https://www.clementine-player.org/"),
            opened,
        )

        row("pref_key_about").performClick()
        compose.onNodeWithTag("aboutDialog").assertExists()
        compose.onNodeWithText("Close").performClick()

        row("pref_key_license").performClick()
        compose.onNodeWithTag("licenseDialog").assertExists()
        compose.onNodeWithText("Close").performClick()

        row("pref_key_opensource").performClick()
        compose.onNodeWithText("JmDNS").assertExists()
        compose.onNodeWithText("Close").performClick()
    }

    @Test
    fun parsesTheOpenSourceLicenses() {
        val licenses = parseLicenses(
            "<h3><a href=\"https://a.example/\">A</a></h3>\n<pre>\nLicence A\n</pre>" +
                "<h3><a href=\"https://b.example/\">B</a></h3><pre>Licence B</pre>",
        )
        assertEquals(
            listOf(
                OpenSourceLicense("A", "https://a.example/", "Licence A"),
                OpenSourceLicense("B", "https://b.example/", "Licence B"),
            ),
            licenses,
        )
    }
}
