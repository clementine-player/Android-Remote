package de.qspool.clementineremote.ui

import android.Manifest
import android.content.ComponentName
import android.os.Looper
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementinePlayerConnection
import de.qspool.clementineremote.backend.ClementineService
import de.qspool.clementineremote.ui.connect.ConnectViewModel
import org.junit.After
import org.junit.Assert.assertFalse
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

/** Cancel gives up on connecting at once, and what the attempt says afterwards isn't shown. */
@RunWith(RobolectricTestRunner::class)
class CancelConnectingTest {

    private var controller: ActivityController<ConnectActivity>? = null

    private var aborted = false

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
        App.ClementineConnection = null
    }

    /** Opens the connect screen, which auto-connects to a saved address. */
    private fun startConnecting(): Pair<ConnectActivity, ConnectViewModel> {
        App.getPreferences().edit(commit = true) {
            putBoolean(SharedPreferencesKeys.SP_FIRST_CALL, false)
            putString(SharedPreferencesKeys.SP_KEY_IP, "10.0.0.5")
            putString(SharedPreferencesKeys.SP_KEY_NAME, "")
        }
        val controller = Robolectric.buildActivity(ConnectActivity::class.java).setup()
        this.controller = controller
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        App.ClementineConnection = object : ClementinePlayerConnection() {
            override fun abortConnecting() {
                aborted = true
            }
        }
        val state = ViewModelProvider(controller.get())[ConnectViewModel::class.java]
        assertTrue(state.isConnecting)
        return controller.get() to state
    }

    @Test
    fun givesUpOnTheAttempt() {
        val (activity, state) = startConnecting()
        activity.onCancel()
        assertTrue(aborted)
        assertFalse(state.isConnecting)
    }

    @Test
    fun saysNothingWhenTheAttemptFails() {
        val (activity, state) = startConnecting()
        activity.onCancel()
        activity.connectionEnded()
        activity.noConnection()
        assertNull(state.dialog.value)
    }

    @Test
    fun keepsProgressHiddenWhenTheAttemptGetsThrough() {
        val (activity, state) = startConnecting()
        activity.onCancel()
        activity.showProgress(R.string.connectdialog_download_data)
        assertFalse(state.isConnecting)
    }
}
