package de.qspool.clementineremote.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ClementineThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun schemeFor(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
        lateinit var scheme: ColorScheme
        compose.setContent {
            ClementineTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                scheme = MaterialTheme.colorScheme
            }
        }
        compose.waitForIdle()
        return scheme
    }

    @Test
    fun clementineColoursLight() {
        val scheme = schemeFor(darkTheme = false, dynamicColor = false)
        assertEquals(ClementineLightColors.primary, scheme.primary)
        assertEquals(Color(0xFF9F3C09), scheme.primary)
    }

    @Test
    fun clementineColoursDark() {
        assertEquals(ClementineDarkColors.surface, schemeFor(darkTheme = true, dynamicColor = false).surface)
    }

    @Test
    @Config(sdk = [30])
    fun noDynamicColourBeforeAndroid12() {
        assertEquals(ClementineLightColors.primary, schemeFor(darkTheme = false, dynamicColor = true).primary)
    }

    @Test
    fun darkAndLightDiffer() {
        assertNotEquals(ClementineLightColors.surface, ClementineDarkColors.surface)
    }
}
