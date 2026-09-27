package de.qspool.clementineremote.ui.hints

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.player.MySong
import de.qspool.clementineremote.backend.streaming.ThisRenderer
import de.qspool.clementineremote.ui.shell.MiniPlayer
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
 * A hint points out the output button the first time the mini player shows it, until it's
 * closed or the button is used, and waits while the mini player is covered.
 */
@RunWith(RobolectricTestRunner::class)
class HintsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outputs = RemoteRepository.Outputs(
        supported = true,
        outputs = listOf(
            RemoteRepository.Output(RemoteRepository.LOCAL_OUTPUT, "studio", active = true, activating = false),
            RemoteRepository.Output(ThisRenderer.id(), "Pixel 9", active = false, activating = false),
        ),
    )

    private var covered by mutableStateOf(false)
    private var opened = 0

    @Before
    fun setUp() {
        Hints.reset()
    }

    private fun show(outputs: RemoteRepository.Outputs = this.outputs) {
        val nowPlaying = RemoteRepository.NowPlaying(
            MySong().apply { title = "Clair de lune" },
            Clementine.State.PLAY,
            positionSeconds = 0,
            volume = 50,
            Clementine.ShuffleMode.OFF,
            Clementine.RepeatMode.OFF,
        )
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                MiniPlayer(nowPlaying, {}, {}, {}, outputs = outputs, onOutputs = { opened++ }, hints = !covered)
            }
        }
    }

    @Test
    fun pointsOutTheOutputButtonUntilClosed() {
        show()
        compose.onNodeWithTag("hint_outputs").assertExists()
        compose.onNodeWithText("Play on this device").assertExists()

        compose.onNodeWithTag("hintDone").performClick()

        compose.onNodeWithTag("hint_outputs").assertDoesNotExist()
        assertTrue(Hints.isSeen(Hint.OUTPUTS))
    }

    @Test
    fun usingTheButtonCountsAsSeen() {
        show()
        compose.onNodeWithTag("btnOutputs").performClick()

        assertEquals(1, opened)
        compose.onNodeWithTag("hint_outputs").assertDoesNotExist()
        assertTrue(Hints.isSeen(Hint.OUTPUTS))
    }

    @Test
    fun waitsWhileCovered() {
        covered = true
        show()
        compose.onNodeWithTag("hint_outputs").assertDoesNotExist()

        covered = false
        compose.onNodeWithTag("hint_outputs").assertExists()
        assertFalse(Hints.isSeen(Hint.OUTPUTS))
    }

    @Test
    fun noButtonNoHint() {
        show(RemoteRepository.Outputs(supported = false))

        compose.onNodeWithTag("btnOutputs").assertDoesNotExist()
        compose.onNodeWithTag("hint_outputs").assertDoesNotExist()
    }

    @Test
    fun seenStaysSeenUntilReset() {
        Hints.seen(Hint.OUTPUTS)
        show()
        compose.onNodeWithTag("hint_outputs").assertDoesNotExist()

        Hints.reset()
        compose.onNodeWithTag("hint_outputs").assertExists()
    }
}
