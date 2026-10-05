package de.qspool.clementineremote

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import de.qspool.clementineremote.ui.ConnectActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Answers Android 17's local network prompt as someone would, refusing it and then allowing it,
 * and checks the connect screen stops and starts looking on the network.
 *
 * The permission mustn't be granted yet, and it can't be taken back here: revoking a permission
 * kills the app, and these tests run in its process. The other device tests grant it, so this
 * one runs on a fresh install, on its own (see ci.yml):
 *
 *     ./gradlew connectedFdroidDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=de.qspool.clementineremote.LocalNetworkPermissionDeviceTest
 *
 * Run with the others after they've granted it, it's skipped.
 */
@RunWith(AndroidJUnit4::class)
class LocalNetworkPermissionDeviceTest {

    /** The app's other permissions, so Android's only prompt is the local network one. */
    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_PHONE_STATE)

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        assumeTrue("Only Android 17 has the local network permission", Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN)
        assumeFalse("The local network permission is granted already: run this test on a fresh install", hasLocalNetwork())
        // The app's own dialog, which leads to Android's prompt, shows until it's been answered,
        // and nothing connects in the meantime.
        App.getPreferences().edit {
            putBoolean(SharedPreferencesKeys.SP_PERMISSIONS_ASKED, false)
            putBoolean(SharedPreferencesKeys.SP_KEY_AC, false)
        }
    }

    @Test
    fun refusingAndThenAllowingTheLocalNetwork() {
        ActivityScenario.launch(ConnectActivity::class.java).use {
            // A dialog is a window of its own, whose test tags aren't resource IDs.
            click(By.text(context.getString(R.string.dialog_continue)))

            // Refused the first time: Android will still ask a second.
            click(prompt("permission_deny_button"))
            waitFor(By.res("notSearching"))
            assertEquals(false, hasLocalNetwork())

            // Searching again asks again.
            click(By.res("btnSearchAgain"))
            click(prompt("permission_allow_button"))
            waitFor(By.res("searching"))
            assertEquals(true, hasLocalNetwork())
        }
    }

    // Only Android 17 and later have the permission, which setUp checks.
    @SuppressLint("InlinedApi")
    private fun hasLocalNetwork(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED

    /** A button on Android's permission prompt, whichever package draws it on this image. */
    private fun prompt(id: String): BySelector = By.res(Pattern.compile(".*:id/$id"))

    private fun waitFor(selector: BySelector) =
        assertNotNull("Not on screen: $selector", device.wait(Until.findObject(selector), TIMEOUT_MILLIS))

    private fun click(selector: BySelector) {
        val found = device.wait(Until.findObject(selector), TIMEOUT_MILLIS)
        assertNotNull("Not on screen: $selector", found)
        found.click()
    }

    private companion object {
        // The emulator in CI draws in software, slowly.
        const val TIMEOUT_MILLIS = 20_000L
    }
}
