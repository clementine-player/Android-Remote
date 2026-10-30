package de.qspool.clementineremote.backend;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.os.Message;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus;
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The connection to Clementine, on its own thread, against an in-process fake server. */
@RunWith(RobolectricTestRunner.class)
public class ClementinePlayerConnectionTest {

    private ServerSocket mServer;

    private ClementinePlayerConnection mConnection;

    private Thread mConnectionThread;

    private final List<ConnectionStatus> mStatuses = new CopyOnWriteArrayList<>();

    private final CountDownLatch mConnected = new CountDownLatch(1);

    @Before
    public void setUp() throws Exception {
        App.Clementine = new Clementine();
        mServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        mConnection = new ClementinePlayerConnection();
        mConnection.addPlayerConnectionListener(new PlayerConnectionListener() {
            @Override
            public void onConnectionStatusChanged(ConnectionStatus status) {
                mStatuses.add(status);
                if (status == ConnectionStatus.CONNECTED) {
                    mConnected.countDown();
                }
            }

            @Override
            public void onClementineMessageReceived(ClementineMessage clementineMessage) {
            }
        });
        mConnectionThread = new Thread(mConnection);
        mConnectionThread.start();
        while (mConnection.mHandler == null) {
            Thread.sleep(10);
        }
    }

    @After
    public void tearDown() throws Exception {
        mServer.close();
        if (mConnectionThread.isAlive() && mConnection.mHandler != null) {
            mConnection.mHandler.getLooper().quit();
        }
        mConnectionThread.join(5000);
    }

    /** Connects on the connection's thread, as the service does. */
    private Socket connect() throws Exception {
        Message msg = Message.obtain();
        msg.obj = ClementineMessageFactory.buildConnectMessage(
                mServer.getInetAddress().getHostAddress(), mServer.getLocalPort(), 0, false, false);
        mConnection.mHandler.sendMessage(msg);
        Socket client = mServer.accept();
        assertTrue(mConnected.await(5, TimeUnit.SECONDS));
        return client;
    }

    /**
     * Sending fails, and so does reconnecting, off the connection's thread: on the thread reading
     * from Clementine, when the keep-alive check reconnects. The connection still closes, on its
     * own thread, instead of crashing for want of a looper there.
     */
    @Test
    public void closesWhenSendingFailsOffItsThread() throws Exception {
        Socket client = connect();
        // Reset, so writing fails, and nothing is there to reconnect to.
        client.setSoLinger(true, 0);
        client.close();
        mServer.close();
        Thread.sleep(200);

        Throwable[] thrown = {null};
        Thread sender = new Thread(() -> {
            try {
                for (int i = 0; i < 3; ++i) {
                    mConnection.sendRequest(ClementineMessage.getMessage(MsgType.PLAY));
                }
            } catch (Throwable t) {
                thrown[0] = t;
            }
        });
        sender.start();
        sender.join(10000);

        assertNull(thrown[0]);
        mConnectionThread.join(10000);
        assertFalse(mConnectionThread.isAlive());
        assertTrue(mStatuses.contains(ConnectionStatus.DISCONNECTED));
    }
}
