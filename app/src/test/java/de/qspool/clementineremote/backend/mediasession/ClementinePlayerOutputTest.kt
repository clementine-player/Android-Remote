package de.qspool.clementineremote.backend.mediasession

import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Output
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.OutputState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseOutputs
import de.qspool.clementineremote.backend.streaming.ThisRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Playing on this phone, the volume keys set the phone's volume rather than Clementine's. */
@RunWith(RobolectricTestRunner::class)
class ClementinePlayerOutputTest {

    private lateinit var player: ClementinePlayer

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        player = ClementinePlayer(Looper.getMainLooper()) {}
        player.setConnected(true)
        idle()
    }

    @After
    fun tearDown() {
        playOn(RemoteRepository.LOCAL_OUTPUT)
        player.release()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun playOn(outputId: String) {
        RemoteRepository.onMessage(ClementineMessage(ClementineMessage.getMessageBuilder(MsgType.OUTPUTS)
            .setResponseOutputs(ResponseOutputs.newBuilder()
                .addOutputs(Output.newBuilder().setOutputId(outputId).setState(OutputState.OUTPUT_STATE_ACTIVE)))))
        player.invalidate()
        idle()
    }

    @Test
    fun theVolumeIsThePhonesWhilePlayingHere() {
        playOn(ThisRenderer.id())

        assertTrue(RemoteRepository.isPlayingHere())
        assertEquals(DeviceInfo.PLAYBACK_TYPE_LOCAL, player.deviceInfo.playbackType)
        assertFalse(player.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS))

        playOn(RemoteRepository.LOCAL_OUTPUT)

        assertEquals(DeviceInfo.PLAYBACK_TYPE_REMOTE, player.deviceInfo.playbackType)
        assertTrue(player.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS))
    }
}
