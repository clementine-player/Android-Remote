package de.qspool.clementineremote.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ServerFeature
import de.qspool.clementineremote.backend.player.MySong
import de.qspool.clementineremote.ui.theme.ClementineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The shell switches screens with the navigation bar, opens the player from the mini player
 * and the connection sheet from the connection chip.
 */
@RunWith(RobolectricTestRunner::class)
class AppShellTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val done = mutableListOf<String>()

    private val actions = object : ShellActions {
        override fun onDownloadSong(what: DownloadWhat) {
            done += "download $what"
        }

        override fun onSwitchClementine() {
            done += "switch"
        }

        override fun onSettings() {
            done += "settings"
        }

        override fun onDisconnect() {
            done += "disconnect"
        }
    }

    private val shell = ShellViewModel()

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.ClementineConnection = null
        App.Clementine.currentSong = MySong().apply {
            title = "Clair de lune"
            artist = "Claude Debussy"
            album = "Suite bergamasque"
            length = 300
            isLocal = true
        }
        RemoteRepository.refresh()
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                AppShell(shell, actions)
            }
        }
    }

    @Test
    fun switchesScreensAndBackGoesToTheQueue() {
        compose.onNodeWithTag("navQueue").assertIsSelected()
        compose.onNodeWithTag("navDownloads").performClick()
        compose.onNodeWithTag("downloads").assertIsDisplayed()
        compose.onNodeWithTag("navSearch").performClick()
        compose.onNodeWithTag("searchField").assertIsDisplayed()
        assertEquals(Destination.SEARCH, shell.destination)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(Destination.QUEUE, shell.destination)
    }

    /** Clementine's INFO, as on connecting: it can be browsed, or not. */
    private fun connect(browse: Boolean) {
        val info = ResponseClementineInfo.newBuilder().setVersion("1.4")
        if (browse) {
            info.addFeatures(ServerFeature.SERVER_FEATURE_BROWSE)
        }
        compose.runOnUiThread {
            RemoteRepository.onMessage(ClementineMessage(
                ClementineMessage.getMessageBuilder(MsgType.INFO).setResponseClementineInfo(info)))
        }
        compose.waitForIdle()
    }

    @Test
    fun theInternetScreenIsThereOnlyWhenClementineCanBeBrowsed() {
        try {
            connect(browse = false)
            compose.onNodeWithTag("navInternet").assertDoesNotExist()

            connect(browse = true)
            compose.onNodeWithTag("navInternet").performClick()
            compose.onNodeWithTag("internetRefresh").assertIsDisplayed()
            assertEquals(Destination.INTERNET, shell.destination)

            // Connected to a Clementine that can't be browsed, while it shows: the queue shows.
            connect(browse = false)
            compose.onNodeWithTag("navInternet").assertDoesNotExist()
            assertEquals(Destination.QUEUE, shell.destination)
        } finally {
            connect(browse = false)
        }
    }

    @Test
    fun theMiniPlayerOpensThePlayer() {
        compose.onNodeWithTag("miniTitle", useUnmergedTree = true).assertTextEquals("Clair de lune")
        compose.onNodeWithTag("miniPlayer").performClick()
        compose.waitForIdle()

        assertTrue(shell.playerOpen)
        compose.onNodeWithTag("tvTitle").assertTextEquals("Clair de lune")
        compose.onNodeWithTag("btnDownload").performClick()
        compose.onNodeWithTag("downloadAlbum").performClick()
        compose.onNodeWithTag("btnQueue").performClick()
        compose.waitForIdle()

        assertFalse(shell.playerOpen)
        assertEquals(listOf("download ALBUM"), done)
    }

    @Test
    fun theChipOpensTheConnectionSheet() {
        compose.onNodeWithTag("connectionChip").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("cnHost").assertIsDisplayed()
        compose.onNodeWithTag("btnSettings").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf("settings"), done)
        compose.onNodeWithTag("cnHost").assertDoesNotExist()
    }

    @Test
    fun theChipAndSettingsStayPutAcrossScreens() {
        val places = listOf("navQueue", "navLibrary", "navSearch", "navDownloads").map { destination ->
            compose.onNodeWithTag(destination).performClick()
            val place = compose.onNodeWithTag("connectionChip").getBoundsInRoot() to
                compose.onNodeWithTag("btnTopSettings").getBoundsInRoot()
            compose.onNodeWithTag("btnTopSettings").performClick()
            place
        }

        assertEquals(List(4) { "settings" }, done)
        assertEquals(1, places.toSet().size)
    }

    @Test
    fun theConnectionSheetShowsTheConnection() {
        compose.onNodeWithTag("connectionChip").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Switch Clementine").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cnTraffic").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("btnDisconnect").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf("disconnect"), done)
    }
}
