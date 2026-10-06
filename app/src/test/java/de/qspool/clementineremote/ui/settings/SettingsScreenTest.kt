package de.qspool.clementineremote.ui.settings

import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.downloader.DownloadVolume
import de.qspool.clementineremote.backend.downloader.MediaStoreDownloadStorage
import de.qspool.clementineremote.ui.theme.ClementineTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.File

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

    /** The storage volumes offered. */
    private val volumes = mutableStateOf(emptyList<DownloadVolume>())

    private val phone = DownloadVolume(MediaStore.VOLUME_EXTERNAL_PRIMARY, "Internal shared storage")

    private val sdCard = DownloadVolume("1234-abcd", "SanDisk SD card")

    private val actions = object : SettingsActions {
        override fun onBack() {
            opened += "back"
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
                // Folders are read in line, so the dialog's lists are there once it's idle.
                CompositionLocalProvider(LocalFolderDispatcher provides Dispatchers.Unconfined) {
                    SettingsScreen(rememberPreferenceStore(preferences), actions, volumes.value) { "/music" }
                }
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
    fun withOneVolumeThereIsNoChoiceOfWhereDownloadsGo() {
        compose.onNodeWithTag(SharedPreferencesKeys.SP_DOWNLOAD_VOLUME).assertDoesNotExist()
    }

    @Test
    fun downloadsCanGoToAnSdCard() {
        volumes.value = listOf(phone, sdCard)
        row(SharedPreferencesKeys.SP_DOWNLOAD_VOLUME)
        // The primary volume unless another is picked.
        compose.onNodeWithText("Internal shared storage").assertExists()

        row(SharedPreferencesKeys.SP_DOWNLOAD_VOLUME).performClick()
        compose.onNodeWithTag("${SharedPreferencesKeys.SP_DOWNLOAD_VOLUME}_1234-abcd").performClick()
        assertEquals("1234-abcd", preferences.getString(SharedPreferencesKeys.SP_DOWNLOAD_VOLUME, null))
        compose.onNodeWithText("SanDisk SD card").assertExists()
    }

    @Test
    fun aVolumeTakenOutCanBeChangedFrom() {
        preferences.edit().putString(SharedPreferencesKeys.SP_DOWNLOAD_VOLUME, "1234-abcd").commit()
        volumes.value = listOf(phone)
        row(SharedPreferencesKeys.SP_DOWNLOAD_VOLUME)
        compose.onNodeWithText("Not available. Choose where to save downloads.").assertExists()
    }

    @Test
    fun volumesAreOfferedWhenThereIsAChoice() {
        val primary = MediaStore.VOLUME_EXTERNAL_PRIMARY
        assertFalse(offerVolumeChoice(emptyList(), primary))
        assertFalse(offerVolumeChoice(listOf(primary), primary))
        assertTrue(offerVolumeChoice(listOf(primary, "1234-abcd"), primary))
        // The SD card picked was taken out.
        assertTrue(offerVolumeChoice(listOf(primary), "1234-abcd"))
    }

    @Test
    @Config(sdk = [28])
    fun beforeAndroid10DownloadsGoToAFolderPicked() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        row(SharedPreferencesKeys.SP_DOWNLOAD_DIR).assertIsEnabled()
        compose.onNodeWithText("/music").assertExists()

        // One of the folders suggested...
        row(SharedPreferencesKeys.SP_DOWNLOAD_DIR).performClick()
        compose.onAllNodesWithTag("folder")[0].performClick()
        val suggested = preferences.getString(SharedPreferencesKeys.SP_DOWNLOAD_DIR, null)!!
        compose.onNodeWithText(suggested).assertExists()

        // ...or one browsed to, starting from the folder set.
        row(SharedPreferencesKeys.SP_DOWNLOAD_DIR).performClick()
        compose.onNodeWithTag("folderOther").performClick()
        // Its title is the folder browsed.
        compose.onAllNodesWithText(suggested).assertCountEquals(2)
        compose.onNodeWithTag("folderUp").performClick()
        compose.onNodeWithTag("btnFolderSelect").performClick()
        assertEquals(File(suggested).parent, preferences.getString(SharedPreferencesKeys.SP_DOWNLOAD_DIR, null))
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
