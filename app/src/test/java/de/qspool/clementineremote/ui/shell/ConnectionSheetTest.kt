package de.qspool.clementineremote.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.streaming.ThisRenderer
import de.qspool.clementineremote.ui.theme.ClementineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The connection sheet lists where Clementine can play, and moves playback when one is picked. */
@RunWith(RobolectricTestRunner::class)
class ConnectionSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val stats = ConnectionStats("studio", "192.168.1.20", 5500, "1.4.1", "00:01:00", null)

    private val actions = object : ConnectionActions {
        override fun onSwitchClementine() {}
        override fun onSettings() {}
        override fun onDisconnect() {}
    }

    private fun show(outputs: RemoteRepository.Outputs, onOutput: (String) -> Unit = {}) {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                ConnectionSheetContent(stats, actions, outputs = outputs, onOutput = onOutput)
            }
        }
    }

    @Test
    fun picksAnOutput() {
        val picked = mutableListOf<String>()
        show(
            RemoteRepository.Outputs(
                supported = true,
                outputs = listOf(
                    RemoteRepository.Output(RemoteRepository.LOCAL_OUTPUT, "studio", active = true, activating = false),
                    RemoteRepository.Output(ThisRenderer.id(), "Pixel 9", active = false, activating = false),
                ),
            ),
        ) { picked += it }

        compose.onNodeWithText("Play on").performScrollTo()
        compose.onNodeWithText("This computer").assertExists()
        compose.onNodeWithText("Pixel 9 (this phone)").assertExists()
        compose.onNodeWithTag("output_local").assertIsSelected()
        compose.onNodeWithTag("output_" + ThisRenderer.id()).assertIsNotSelected().performScrollTo().performClick()

        assertEquals(listOf(ThisRenderer.id()), picked)
    }

    @Test
    fun hidesOutputsWhenClementineCantPlayElsewhere() {
        show(RemoteRepository.Outputs(supported = false))

        compose.onNodeWithText("Play on").assertDoesNotExist()
    }
}
