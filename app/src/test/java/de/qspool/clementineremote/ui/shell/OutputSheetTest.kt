package de.qspool.clementineremote.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.streaming.ThisRenderer
import de.qspool.clementineremote.ui.theme.ClementineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The output sheet lists where Clementine can play, and moves playback when one is picked. */
@RunWith(RobolectricTestRunner::class)
class OutputSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun outputs(active: String) = RemoteRepository.Outputs(
        supported = true,
        outputs = listOf(
            RemoteRepository.Output(RemoteRepository.LOCAL_OUTPUT, "studio", active = active == RemoteRepository.LOCAL_OUTPUT, activating = false),
            RemoteRepository.Output(ThisRenderer.id(), "Pixel 9", active = active == ThisRenderer.id(), activating = false),
        ),
    )

    @Test
    fun picksAnOutput() {
        val picked = mutableListOf<String>()
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                OutputSheetContent(outputs(RemoteRepository.LOCAL_OUTPUT), onOutput = { picked += it })
            }
        }

        compose.onNodeWithText("Play on").assertExists()
        compose.onNodeWithText("This computer").assertExists()
        compose.onNodeWithText("Pixel 9 (this phone)").assertExists()
        compose.onNodeWithTag("output_local").assertIsSelected()
        compose.onNodeWithTag("output_" + ThisRenderer.id()).assertIsNotSelected().performClick()

        assertEquals(listOf(ThisRenderer.id()), picked)
    }

    @Test
    fun theButtonSaysWhereClementinePlays() {
        var outputs by mutableStateOf(outputs(RemoteRepository.LOCAL_OUTPUT))
        var clicks = 0
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                OutputButton(outputs, onClick = { clicks++ })
            }
        }

        compose.onNodeWithTag("btnOutputs").assertContentDescriptionEquals("Choose where to play").performClick()
        assertEquals(1, clicks)

        outputs = outputs(ThisRenderer.id())
        compose.onNodeWithTag("btnOutputs").assertContentDescriptionEquals("Playing on Pixel 9")
    }

    @Test
    fun isOnlyThereWithSomewhereElseToPlay() {
        assertTrue(outputs(RemoteRepository.LOCAL_OUTPUT).switchable)
        assertFalse(RemoteRepository.Outputs(supported = false).switchable)
        assertFalse(
            RemoteRepository.Outputs(
                supported = true,
                outputs = listOf(RemoteRepository.Output(RemoteRepository.LOCAL_OUTPUT, "studio", active = true, activating = false)),
            ).switchable,
        )
    }
}
