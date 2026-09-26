package de.qspool.clementineremote.ui.tasker

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.ui.TaskerSettings
import de.qspool.clementineremote.utils.bundle.LocalePluginIntent
import de.qspool.clementineremote.utils.bundle.PluginBundleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Tasker action's settings are saved as the bundle Tasker keeps, and loaded from it. */
@RunWith(RobolectricTestRunner::class)
class TaskerSettingsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun setUp() {
        App.getPreferences().edit()
            .putString(SharedPreferencesKeys.SP_KEY_IP, "192.168.1.20")
            .putString(SharedPreferencesKeys.SP_KEY_PORT, "5500")
            .putInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, 0)
            .commit()
    }

    private fun launch(intent: Intent = Intent(ApplicationProvider.getApplicationContext(), TaskerSettings::class.java)) =
        ActivityScenario.launchActivityForResult<TaskerSettings>(intent)

    @Test
    fun savesTheActionWithItsBlurb() {
        val scenario = launch()
        compose.onNodeWithTag("tasker_play").performScrollTo().performClick()
        compose.onNodeWithTag("btnTaskerDone").performClick()

        val result = scenario.result
        assertEquals(Activity.RESULT_OK, result.resultCode)
        val bundle = result.resultData.getBundleExtra(LocalePluginIntent.EXTRA_BUNDLE)!!
        assertEquals(TaskerSettings.ACTION_PLAY, bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_TYPE))
        assertEquals("Action: Play", result.resultData.getStringExtra(LocalePluginIntent.EXTRA_STRING_BLURB))
    }

    @Test
    fun connectNeedsAPortClementineCanListenOn() {
        val scenario = launch()
        compose.onNodeWithTag("taskerPort").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("btnTaskerDone").performClick()
        // Still open, saying why.
        compose.onNodeWithText("Illegal Port!").assertExists()

        compose.onNodeWithTag("taskerPort").performTextReplacement("5600")
        compose.onNodeWithTag("btnTaskerDone").performClick()
        val result = scenario.result
        assertEquals(Activity.RESULT_OK, result.resultCode)
        val bundle = result.resultData.getBundleExtra(LocalePluginIntent.EXTRA_BUNDLE)!!
        assertEquals(TaskerSettings.ACTION_CONNECT, bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_TYPE))
        assertEquals("192.168.1.20", bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_IP))
        assertEquals(5600, bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_PORT))
        assertTrue(result.resultData.getStringExtra(LocalePluginIntent.EXTRA_STRING_BLURB)!!.endsWith("192.168.1.20:5600"))
    }

    @Test
    fun editsTheActionTaskerPasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val saved = PluginBundleManager.generateBundle(context, TaskerSettings.ACTION_NEXT, "10.0.2.2", 5501, 1234)
        launch(Intent(context, TaskerSettings::class.java).putExtra(LocalePluginIntent.EXTRA_BUNDLE, saved))

        compose.onNodeWithTag("tasker_next").assertIsSelected()
        compose.onNodeWithText("10.0.2.2").assertExists()
        compose.onNodeWithText("5501").assertExists()
    }
}
