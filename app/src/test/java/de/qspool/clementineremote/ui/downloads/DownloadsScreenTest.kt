package de.qspool.clementineremote.ui.downloads

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.ui.theme.ClementineTheme
import de.qspool.clementineremote.utils.Utilities
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Downloads show their progress, let a download be cancelled, and play what was downloaded. */
@RunWith(RobolectricTestRunner::class)
class DownloadsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<String>()

    private val running = Download(
        id = 1, item = DownloadItem.ItemAlbum, title = "Downloading album Suite bergamasque",
        subtitle = "(2/4) Claude Debussy - Menuet", progress = 50, size = "10 MiB / 20 MiB (1 MiB/s)",
        running = true, songs = emptyList(),
    )

    private val finished = Download(
        id = 2, item = DownloadItem.Urls, title = "Downloading songs Erik Satie - Gymnopédie No. 1",
        subtitle = "(1/1) Download finished", progress = 100, size = "3 MiB / 3 MiB",
        running = false, songs = listOf(DownloadedSong("Gymnopédie No. 1", "Erik Satie", Uri.parse("content://media/1"))),
    )

    private fun show(state: DownloadsState) {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                DownloadsContent(
                    state,
                    onCancel = { done += "cancel ${it.id}" },
                    onPlay = { done += "play ${it.title}" },
                    onChangeSettings = { done += "settings" },
                )
            }
        }
    }

    @Test
    fun withoutDownloadsSaysSo() {
        show(DownloadsState(freeSpace = "1.0 GiB"))

        compose.onNodeWithTag("downloadsEmpty").assertIsDisplayed()
        compose.onNodeWithText("1.0 GiB free on this phone").assertIsDisplayed()
    }

    @Test
    fun runningDownloadsCanBeCancelled() {
        show(DownloadsState(running = listOf(running)))

        compose.onNodeWithText("Downloading album Suite bergamasque").assertIsDisplayed()
        compose.onNodeWithText("10 MiB / 20 MiB (1 MiB/s)").assertIsDisplayed()
        compose.onNodeWithTag("cancel1").performClick()

        assertEquals(listOf("cancel 1"), done)
    }

    @Test
    fun finishedDownloadsPlayTheirSongs() {
        show(DownloadsState(finished = listOf(finished)))

        compose.onNodeWithText("Downloading songs Erik Satie - Gymnopédie No. 1").performClick()
        compose.onNodeWithText("Erik Satie - Gymnopédie No. 1").performClick()

        assertEquals(listOf("play Gymnopédie No. 1"), done)
    }

    @Test
    fun saysWhenDownloadsOnlyRunOnWifi() {
        show(DownloadsState(wifiOnly = true))

        compose.onNodeWithTag("downloadsWifiOnly").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Change").performClick()

        assertEquals(listOf("settings"), done)
    }

    @Test
    fun viewModelReadsTheDownloadsAndSettings() {
        App.getPreferences().edit().putBoolean(SharedPreferencesKeys.SP_WIFI_ONLY, true).commit()
        val downloads = DownloadsViewModel(downloads = { emptyList() }, freeSpace = { 1L shl 30 })
        downloads.viewModelScope.launch { downloads.state.collect {} }

        // The state is read off the main thread; wait for the first reading.
        val end = System.currentTimeMillis() + 5_000
        while (downloads.state.value.freeSpace.isEmpty() && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertEquals(Utilities.humanReadableBytes(1L shl 30, true), downloads.state.value.freeSpace)
        assertTrue(downloads.state.value.wifiOnly)
        assertTrue(downloads.state.value.running.isEmpty())
    }
}
