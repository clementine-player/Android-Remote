package de.qspool.clementineremote.backend;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ServerFeature;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
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

    @Test
    public void replacingClosingConnectionIgnoresOldDisconnectedEvents() throws Exception {
        App.Clementine = new Clementine();
        ClementinePlayerConnection oldConnection = new ClementinePlayerConnection();
        oldConnection.closeConnection(ClementineMessage.getMessage(MsgType.DISCONNECT));
        assertTrue(oldConnection.isClosedOrClosing());
        App.ClementineConnection = oldConnection;
        RemoteRepository.attach(oldConnection);

        ClementineMessage oldInfo = new ClementineMessage(
                ClementineMessage.getMessageBuilder(MsgType.INFO)
                        .setResponseClementineInfo(
                                ResponseClementineInfo.newBuilder()
                                        .setVersion("1.5")
                                        .setServerId("srv-old")
                                        .setAllowDownloads(true)
                                        .addFeatures(ServerFeature.SERVER_FEATURE_SYNC)
                                        .addFeatures(ServerFeature.SERVER_FEATURE_RENDERING)
                                        .addFeatures(ServerFeature.SERVER_FEATURE_BROWSE)
                                        .addFeatures(ServerFeature.SERVER_FEATURE_ENQUEUE_NEXT)));
        oldConnection.fireOnClementineMessageReceived(oldInfo);
        assertTrue(RemoteRepository.getSyncing().getValue().getAvailable());
        assertTrue(RemoteRepository.getOutputs().getValue().getSupported());
        assertTrue(RemoteRepository.getBrowsing().getValue().getSupported());
        assertTrue(RemoteRepository.getCanEnqueueNext().getValue());

        ClementineService service = Robolectric.buildService(ClementineService.class).create().get();
        Intent startIntent = new Intent(RuntimeEnvironment.getApplication(), ClementineService.class);
        startIntent.putExtra(ClementineService.SERVICE_ID, ClementineService.SERVICE_START);

        try {
            service.handleServiceAction(startIntent);
            ClementinePlayerConnection newConnection = App.ClementineConnection;
            assertNotNull(newConnection);
            assertNotSame(oldConnection, newConnection);

            // Attaching a new connection immediately clears stale per-connection state.
            assertEquals(ConnectionStatus.IDLE, RemoteRepository.getConnection().getValue());
            assertFalse(RemoteRepository.getSyncing().getValue().getAvailable());
            assertFalse(RemoteRepository.getOutputs().getValue().getSupported());
            assertFalse(RemoteRepository.getBrowsing().getValue().getSupported());
            assertFalse(RemoteRepository.getCanEnqueueNext().getValue());

            // Wait for the player looper thread to create mHandler and finish posting IDLE.
            for (int i = 0; i < 100 && newConnection.mHandler == null; i++) {
                Thread.sleep(10);
            }
            assertNotNull(newConnection.mHandler);
            CountDownLatch looperReady = new CountDownLatch(1);
            assertTrue(newConnection.mHandler.post(looperReady::countDown));
            assertTrue(looperReady.await(5, TimeUnit.SECONDS));

            newConnection.fireOnConnectionStatusChanged(ConnectionStatus.CONNECTED);
            assertEquals(ConnectionStatus.CONNECTED, RemoteRepository.getConnection().getValue());

            // Late DISCONNECTED or INFO from the replaced connection must not clobber the new one.
            oldConnection.fireOnConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
            oldConnection.fireOnClementineMessageReceived(oldInfo);
            assertEquals(ConnectionStatus.CONNECTED, RemoteRepository.getConnection().getValue());
            assertFalse(RemoteRepository.getSyncing().getValue().getAvailable());
            assertFalse(RemoteRepository.getOutputs().getValue().getSupported());
            assertFalse(RemoteRepository.getBrowsing().getValue().getSupported());
            assertFalse(RemoteRepository.getCanEnqueueNext().getValue());
        } finally {
            ClementinePlayerConnection current = App.ClementineConnection;
            if (current != null) {
                current.disconnect(ClementineMessage.getMessage(MsgType.DISCONNECT));
                if (current.mHandler != null) {
                    current.mHandler.getLooper().quit();
                }
                current.fireOnConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
            }
            RemoteRepository.refresh();
            App.ClementineConnection = null;
        }
    }
}
