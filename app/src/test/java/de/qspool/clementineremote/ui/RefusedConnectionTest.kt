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
import de.qspool.clementineremote.backend.ClementineService
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect
import de.qspool.clementineremote.ui.connect.ConnectDialog
import de.qspool.clementineremote.ui.connect.ConnectViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

/** Clementine says why it closed the connection, and the connect screen tells the user. */
@RunWith(RobolectricTestRunner::class)
class RefusedConnectionTest {

    private var controller: ActivityController<ConnectActivity>? = null

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.ClementineConnection = null
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).declareComponentUnbindable(ComponentName(application, ClementineService::class.java))
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        App.getPreferences().edit(commit = true) { putBoolean(SharedPreferencesKeys.SP_FIRST_CALL, false) }
    }

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun disconnect(reason: ReasonDisconnect, retryAfterSeconds: Int? = null): ConnectViewModel {
        val controller = Robolectric.buildActivity(ConnectActivity::class.java).setup()
        this.controller = controller
        val response = ResponseDisconnect.newBuilder().setReasonDisconnect(reason)
        retryAfterSeconds?.let { response.setRetryAfterSeconds(it) }
        val builder = ClementineMessage.getMessageBuilder(MsgType.DISCONNECT).setResponseDisconnect(response)
        controller.get().disconnected(ClementineMessage(builder))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        return ViewModelProvider(controller.get())[ConnectViewModel::class.java]
    }

    @Test
    fun saysClementineOnlyAcceptsItsLocalNetwork() {
        val dialog = disconnect(ReasonDisconnect.Not_Local_Network).dialog.value
        val application = RuntimeEnvironment.getApplication()
        assertEquals(
            ConnectDialog.Message(
                application.getString(R.string.not_local_network_title),
                application.getString(R.string.not_local_network),
            ),
            dialog,
        )
    }

    @Test
    fun asksForTheAuthCode() {
        assertEquals(ConnectDialog.AuthCode, disconnect(ReasonDisconnect.Wrong_Auth_Code).dialog.value)
    }

    @Test
    fun saysToWaitAfterTooManyWrongAuthCodes() {
        val dialog = disconnect(ReasonDisconnect.Too_Many_Wrong_Auth_Codes, retryAfterSeconds = 20).dialog.value
        assertEquals(
            ConnectDialog.Message(
                "Too many wrong auth codes",
                "Clementine won't check another auth code from this phone for 20 seconds. " +
                    "Then enter the code shown in its Network Remote settings.",
            ),
            dialog,
        )
    }
}
