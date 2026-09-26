package de.qspool.clementineremote.ui.connect

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import de.qspool.clementineremote.R
import de.qspool.clementineremote.ui.theme.ClementineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The connect screen offers the Clementines found, takes an address, and shows connecting. */
@RunWith(RobolectricTestRunner::class)
class ConnectScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<String>()

    private fun show(
        host: String = "",
        servers: List<Server> = emptyList(),
        progress: Int? = null,
        knownHosts: List<String> = emptyList(),
    ) {
        compose.setContent {
            var typed by remember { mutableStateOf(host) }
            ClementineTheme(dynamicColor = false) {
                ConnectContent(
                    host = typed,
                    knownHosts = knownHosts,
                    servers = servers,
                    progress = progress,
                    onHostChange = { typed = it },
                    actions = actions { typed },
                )
            }
        }
    }

    private fun actions(typed: () -> String = { "" }) = object : ConnectActions {
        override fun onConnect() {
            done += "connect ${typed()}"
        }

        override fun onServer(server: Server) {
            done += "server ${server.host}:${server.port}"
        }

        override fun onCancel() {
            done += "cancel"
        }

        override fun onSearchAgain() {
            done += "search again"
        }

        override fun onSettings() {
            done += "settings"
        }

        override fun onAuthCode(code: Int) {
            done += "auth $code"
        }

        override fun onRequestPermissions(permissions: List<String>) {
            done += "permissions $permissions"
        }
    }

    /** Shows [viewModel]'s dialogs, as the connect screen does. */
    private fun showDialogs(viewModel: ConnectViewModel) {
        compose.setContent {
            val dialog by viewModel.dialog.collectAsState()
            ClementineTheme(dynamicColor = false) {
                ConnectDialogs(dialog, actions(), viewModel::dismissDialog)
            }
        }
    }

    @Test
    fun asksForTheAuthCodeAndConnectsWithIt() {
        val viewModel = ConnectViewModel()
        viewModel.showDialog(ConnectDialog.AuthCode)
        showDialogs(viewModel)

        compose.onNodeWithTag("btnAuthCodeOk").assertIsNotEnabled()
        compose.onNodeWithTag("authCodeField").performTextReplacement("12a34")
        compose.onNodeWithTag("btnAuthCodeOk").assertIsEnabled().performClick()

        assertEquals(listOf("auth 1234"), done)
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun dialogsQueueAndAreShownOnce() {
        val viewModel = ConnectViewModel()
        val first = ConnectDialog.Message("Welcome", "<b>Hello</b> there", html = true)
        val permissions = ConnectDialog.Permissions(listOf("android.permission.READ_PHONE_STATE"))
        viewModel.showDialog(first)
        viewModel.showDialog(permissions)
        // Asked again on each resume, it waits only once.
        viewModel.showDialog(permissions)
        showDialogs(viewModel)

        compose.onNodeWithTag("messageText").assertTextEquals("Hello there")
        compose.onNodeWithTag("btnMessageClose").performClick()
        compose.onNodeWithTag("btnPermissionsContinue").performClick()

        assertEquals(listOf("permissions [android.permission.READ_PHONE_STATE]"), done)
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun withNothingFoundExplainsHowToFindClementine() {
        show()

        compose.onNodeWithTag("searching").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("network remote is turned on", substring = true).performScrollTo().assertIsDisplayed()
        // Nothing to connect to yet.
        compose.onNodeWithTag("btnConnect").assertIsNotEnabled()
    }

    @Test
    fun connectsToAClementineFound() {
        show(servers = listOf(
            Server("Living room", "192.168.1.20", 5500),
            Server("Study", "192.168.1.21", 5501),
        ))

        compose.onNodeWithTag("searching").assertDoesNotExist()
        compose.onNodeWithText("Study").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("192.168.1.21 · port 5501").performClick()

        assertEquals(listOf("server 192.168.1.21:5501"), done)
    }

    @Test
    fun connectsToTheAddressTypedIn() {
        show()

        compose.onNodeWithTag("etIp").performTextReplacement(" 10.0.2.2 ")
        compose.onNodeWithTag("btnConnect").assertIsEnabled().performClick()
        compose.onNodeWithTag("etIp").performImeAction()

        assertEquals(listOf("connect 10.0.2.2", "connect 10.0.2.2"), done)
    }

    @Test
    fun suggestsAddressesConnectedToBefore() {
        show(knownHosts = listOf("192.168.1.20", "10.0.2.2"))

        compose.onNodeWithTag("etIp").performTextReplacement("192")
        compose.onNodeWithText("192.168.1.20").performClick()

        compose.onNodeWithTag("etIp").assertTextContains("192.168.1.20")
    }

    @Test
    fun whileConnectingShowsProgressAndCanCancel() {
        show(host = "10.0.2.2", progress = R.string.connectdialog_download_data)

        compose.onNodeWithTag("btnConnect").assertIsNotEnabled()
        compose.onNodeWithText("Downloading data").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("etIp").assertIsNotEnabled()
        compose.onNodeWithTag("btnCancel").performClick()

        assertEquals(listOf("cancel"), done)
    }

    @Test
    fun opensSettingsAndSearchesAgain() {
        show()

        compose.onNodeWithTag("btnSettings").performClick()
        compose.onNodeWithTag("btnSearchAgain").performClick()

        assertEquals(listOf("settings", "search again"), done)
    }

    @Test
    fun viewModelKeepsTheActivitysState() {
        val state = ConnectViewModel()

        state.setKnownHosts(listOf("", "10.0.2.2"))
        state.showProgress(R.string.connectdialog_connecting)

        assertEquals(listOf("10.0.2.2"), state.knownHosts.value)
        assertEquals(true, state.isConnecting)
        state.hideProgress()
        assertEquals(false, state.isConnecting)
    }
}
