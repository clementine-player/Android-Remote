package de.qspool.clementineremote.ui

import android.content.ComponentName
import android.os.Looper
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementineService
import de.qspool.clementineremote.ui.connect.ConnectViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

/**
 * Auto-connect connects to an address typed in straight away, and waits for a Clementine picked
 * from the network to show up there again.
 */
@RunWith(RobolectricTestRunner::class)
class AutoConnectTest {

    private var controller: ActivityController<ConnectActivity>? = null

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.ClementineConnection = null
        // Connecting goes no further than binding the service.
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).declareComponentUnbindable(ComponentName(application, ClementineService::class.java))
    }

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun start(name: String): ConnectViewModel {
        App.getPreferences().edit(commit = true) {
            putBoolean(SharedPreferencesKeys.SP_FIRST_CALL, false)
            putString(SharedPreferencesKeys.SP_KEY_IP, "10.0.0.5")
            putString(SharedPreferencesKeys.SP_KEY_NAME, name)
        }
        val controller = Robolectric.buildActivity(ConnectActivity::class.java).setup()
        this.controller = controller
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        return ViewModelProvider(controller.get())[ConnectViewModel::class.java]
    }

    @Test
    fun connectsToAnAddressTypedInStraightAway() {
        assertTrue(start(name = "").isConnecting)
    }

    @Test
    fun waitsForAClementinePickedFromTheNetwork() {
        assertFalse(start(name = "studio-pc").isConnecting)
    }
}
