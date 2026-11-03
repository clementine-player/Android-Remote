package de.qspool.clementineremote.backend;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ClementineServiceTest {

    @Test
    public void clementineStartingToPlayStartsPlaying() {
        assertTrue(ClementineService.startsPlaying(ClementineMessage.getMessage(MsgType.PLAY)));
        assertFalse(ClementineService.startsPlaying(ClementineMessage.getMessage(MsgType.PAUSE)));
    }

    /**
     * The connection hands its listeners the errors it has too, such as losing the connection:
     * they don't start anything playing, or crash for want of a message type.
     */
    @Test
    public void errorsDontStartPlaying() {
        for (ErrorMessage error : ErrorMessage.values()) {
            if (error == ErrorMessage.NONE) {
                continue;
            }
            assertFalse(error.toString(),
                    ClementineService.startsPlaying(new ClementineMessage(error)));
        }
    }
}
