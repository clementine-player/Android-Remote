package de.qspool.clementineremote.ui

import android.Manifest
import android.content.ComponentName
import android.os.Looper
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementineService
import de.qspool.clementineremote.ui.connect.ConnectDialog
import de.qspool.clementineremote.ui.connect.ConnectViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Auto-connect tries the saved address straight away. If a Clementine picked from the network
 * isn't there, it says nothing, to look for it by name instead; an address typed in says it
 * couldn't connect.
 */
@RunWith(RobolectricTestRunner::class)
class AutoConnectTest {

    private var controller: ActivityController<ConnectActivity>? = null

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.ClementineConnection = null
        val application = RuntimeEnvironment.getApplication()
        // Connecting goes no further than binding the service.
        shadowOf(application).declareComponentUnbindable(ComponentName(application, ClementineService::class.java))
        // No permissions dialog.
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun start(name: String): ConnectViewModel {
        App.getPreferences().edit(commit = true) {
            putString(SharedPreferencesKeys.SP_KEY_IP, "10.0.0.5")
            putString(SharedPreferencesKeys.SP_KEY_NAME, name)
        }
        val controller = Robolectric.buildActivity(ConnectActivity::class.java).setup()
        this.controller = controller
        idle()
        return ViewModelProvider(controller.get())[ConnectViewModel::class.java]
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    /** The saved address couldn't be reached. */
    private fun fail() {
        controller!!.get().run {
            connectionEnded()
            noConnection()
        }
        idle()
    }

    @Test
    fun triesTheSavedAddressOfAnAddressTypedIn() {
        val state = start(name = "")
        assertTrue(state.isConnecting)
        assertEquals("10.0.0.5", state.host.value)
    }

    @Test
    fun triesTheSavedAddressOfAClementinePickedFromTheNetwork() {
        val state = start(name = "studio-pc")
        assertTrue(state.isConnecting)
        assertEquals("10.0.0.5", state.host.value)
    }

    @Test
    fun saysAnAddressTypedInCouldNotBeReached() {
        val state = start(name = "")
        fail()
        assertTrue(state.dialog.value is ConnectDialog.Message)
    }

    @Test
    fun waitsQuietlyForAClementinePickedFromTheNetwork() {
        val state = start(name = "studio-pc")
        fail()
        assertNull(state.dialog.value)
    }
}
