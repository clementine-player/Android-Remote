package de.qspool.clementineremote

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.player.MySong
import de.qspool.clementineremote.ui.connect.ConnectActions
import de.qspool.clementineremote.ui.connect.ConnectContent
import de.qspool.clementineremote.ui.connect.ConnectDialog
import de.qspool.clementineremote.ui.connect.ConnectDialogs
import de.qspool.clementineremote.ui.connect.Server
import de.qspool.clementineremote.ui.settings.SettingsActions
import de.qspool.clementineremote.ui.settings.SettingsScreen
import de.qspool.clementineremote.ui.settings.rememberPreferenceStore
import de.qspool.clementineremote.ui.shell.AppShell
import de.qspool.clementineremote.ui.shell.DownloadChooser
import de.qspool.clementineremote.ui.shell.DownloadWhat
import de.qspool.clementineremote.ui.shell.ShellActions
import de.qspool.clementineremote.ui.shell.ShellViewModel
import de.qspool.clementineremote.ui.tasker.TaskerScreen
import de.qspool.clementineremote.ui.tasker.TaskerViewModel
import de.qspool.clementineremote.ui.theme.ClementineTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the Accessibility Test Framework's checks (labels, touch target sizes, contrast and so
 * on) over each Compose screen and dialog, in the light and dark themes. They need a real
 * Android runtime: under Robolectric they don't run.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        compose.enableAccessibilityChecks()
    }

    /** Shows [content] in both themes, checking each; dialogs are windows of their own. */
    private fun check(dialog: Boolean = false, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        compose.setContent {
            ClementineTheme(darkTheme = dark, dynamicColor = false) {
                Surface { content() }
            }
        }
        for (theme in listOf(false, true)) {
            dark = theme
            compose.waitForIdle()
            if (dialog) {
                compose.onNode(isDialog()).tryPerformAccessibilityChecks()
            } else {
                checkRoot()
            }
        }
    }

    /** Checks the screen; on a finding, logs the layout, whose bounds the finding names. */
    private fun checkRoot() {
        try {
            compose.onRoot().tryPerformAccessibilityChecks()
        } catch (e: Throwable) {
            val window = compose.activity.window.decorView
            Log.e(TAG, "Window ${window.width}x${window.height}px at ${window.resources.displayMetrics.density}x")
            compose.onRoot(useUnmergedTree = true).printToLog(TAG)
            throw e
        }
    }

    private companion object {
        const val TAG = "AccessibilityDeviceTest"
    }

    private val connectActions = object : ConnectActions {
        override fun onConnect() {}
        override fun onServer(server: Server) {}
        override fun onCancel() {}
        override fun onSearchAgain() {}
        override fun onSettings() {}
        override fun onAuthCode(code: Int) {}
        override fun onRequestPermissions(permissions: List<String>) {}
    }

    @Test
    fun connectScreen() = check {
        ConnectContent(
            host = "192.168.1.20",
            knownHosts = emptyList(),
            servers = listOf(Server("Living room", "192.168.1.20", 5500)),
            progress = null,
            onHostChange = {},
            actions = connectActions,
        )
    }

    @Test
    fun connectMessageDialog() = check(dialog = true) {
        ConnectDialogs(ConnectDialog.Message("Could not connect", "Check the address."), connectActions) {}
    }

    @Test
    fun authCodeDialog() = check(dialog = true) {
        ConnectDialogs(ConnectDialog.AuthCode, connectActions) {}
    }

    @Test
    fun shellAndPlayer() {
        App.Clementine = Clementine()
        App.Clementine.currentSong = MySong().apply {
            title = "Clair de lune"
            artist = "Claude Debussy"
            album = "Suite bergamasque"
            length = 300
            isLocal = true
        }
        RemoteRepository.refresh()
        val shell = ShellViewModel()
        val actions = object : ShellActions {
            override fun onDownloadSong(what: DownloadWhat) {}
            override fun onSwitchClementine() {}
            override fun onSettings() {}
            override fun onDisconnect() {}
        }
        check { AppShell(shell, actions) }
        shell.playerOpen = true
        compose.waitForIdle()
        checkRoot()
    }

    @Test
    fun downloadChooser() = check(dialog = true) {
        DownloadChooser(onChoose = {}, onDismiss = {})
    }

    @Test
    fun settings() = check {
        SettingsScreen(
            rememberPreferenceStore(App.getPreferences()),
            object : SettingsActions {
                override fun onBack() {}
                override fun onOpenUrl(url: String) {}
            },
        ) { "/music" }
    }

    @Test
    fun taskerSettings() {
        val form = TaskerViewModel().apply {
            host = "192.168.1.20"
            port = "5500"
        }
        check { TaskerScreen(form, onDone = {}) }
    }
}
