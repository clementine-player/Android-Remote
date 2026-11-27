package de.qspool.clementineremote.backend;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.EngineState;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo;
import de.qspool.clementineremote.backend.streaming.Renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the length-prefixed framing and connect handshake against an in-process
 * fake Clementine server.
 */
@RunWith(RobolectricTestRunner.class)
public class ClementineSimpleConnectionTest {

    private ServerSocket mServer;

    private ClementineSimpleConnection mConnection;

    @Before
    public void setUp() throws Exception {
        App.Clementine = new Clementine();
        mServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        mConnection = new ClementineSimpleConnection();
    }

    @After
    public void tearDown() throws Exception {
        mConnection.disconnect(ClementineMessage.getMessage(MsgType.DISCONNECT));
        mServer.close();
    }

    private ClementineMessage connectMessage(int authCode) {
        return ClementineMessageFactory.buildConnectMessage(
                mServer.getInetAddress().getHostAddress(), mServer.getLocalPort(), authCode,
                false, false);
    }

    private static Message readFrame(DataInputStream in) throws Exception {
        byte[] data = new byte[in.readInt()];
        in.readFully(data);
        return Message.parseFrom(data);
    }

    private static void writeFrame(DataOutputStream out, Message message) throws Exception {
        byte[] data = message.toByteArray();
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    @Test
    public void connectSendsAuthCodeAndReceivesInfo() throws Exception {
        assertTrue(mConnection.createConnection(connectMessage(4242)));

        try (Socket client = mServer.accept()) {
            DataInputStream in = new DataInputStream(client.getInputStream());
            DataOutputStream out = new DataOutputStream(client.getOutputStream());

            Message connect = readFrame(in);
            assertEquals(MsgType.CONNECT, connect.getType());
            assertEquals(4242, connect.getRequestConnect().getAuthCode());

            writeFrame(out, ClementineMessage.getMessageBuilder(MsgType.INFO)
                    .setResponseClementineInfo(ResponseClementineInfo.newBuilder()
                            .setVersion("Clementine test")
                            .setState(EngineState.Paused))
                    .build());

            ClementineMessage reply = mConnection.getProtoc(2000);
            assertEquals(MsgType.INFO, reply.getMessageType());
            assertEquals("Clementine test", App.Clementine.getVersion());
            assertEquals(Clementine.State.PAUSE, App.Clementine.getState());
        }
    }

    @Test
    public void knowsWhereItReachedClementine() throws Exception {
        assertEquals(null, mConnection.getServerAddress());
        assertTrue(mConnection.createConnection(connectMessage(0)));
        try (Socket ignored = mServer.accept()) {
            InetSocketAddress server = mConnection.getServerAddress();
            assertEquals(mServer.getInetAddress(), server.getAddress());
            assertEquals(mServer.getLocalPort(), server.getPort());
            // So the renderer fetches Clementine's paths from there.
            assertEquals("http://127.0.0.1:" + mServer.getLocalPort() + "/s/token/1",
                    Renderer.resolve("/s/token/1", server));
        }
    }

    @Test
    public void timesOutWhenServerIsSilent() throws Exception {
        assertTrue(mConnection.createConnection(connectMessage(0)));
        try (Socket ignored = mServer.accept()) {
            assertEquals(ErrorMessage.TIMEOUT, mConnection.getProtoc(200).getErrorMessage());
        }
    }

    @Test
    public void rejectsInvalidFrameLength() throws Exception {
        assertTrue(mConnection.createConnection(connectMessage(0)));
        try (Socket client = mServer.accept()) {
            DataOutputStream out = new DataOutputStream(client.getOutputStream());
            out.writeInt(-1);
            out.flush();
            assertEquals(ErrorMessage.IO_EXCEPTION, mConnection.getProtoc(2000).getErrorMessage());
        }
    }

    @Test
    public void failsToConnectToClosedPort() throws Exception {
        ClementineMessage message = connectMessage(0);
        mServer.close();
        assertEquals(false, mConnection.createConnection(message));
    }

    @Test
    public void abortConnectingGivesUpAtOnce() throws Exception {
        // Unroutable, so connecting waits for its 3 s timeout unless given up on.
        final ClementineMessage message = ClementineMessageFactory.buildConnectMessage(
                "10.255.255.1", 5500, 0, false, false);
        final boolean[] connected = {true};
        Thread attempt = new Thread(() -> connected[0] = mConnection.createConnection(message));
        long start = System.nanoTime();
        attempt.start();
        Thread.sleep(200);
        mConnection.abortConnecting();
        attempt.join(2000);

        assertFalse(attempt.isAlive());
        assertFalse(connected[0]);
        assertTrue(System.nanoTime() - start < 2_000_000_000L);
    }

    @Test
    public void abortConnectingLeavesAConnectionAlone() throws Exception {
        assertTrue(mConnection.createConnection(connectMessage(0)));
        try (Socket client = mServer.accept()) {
            mConnection.abortConnecting();
            assertTrue(mConnection.isConnected());
        }
    }

    @Test
    public void completesInFlightFrameAcrossReadTimeouts() throws Exception {
        assertTrue(mConnection.createConnection(connectMessage(0)));
        try (Socket client = mServer.accept()) {
            Message info = ClementineMessage.getMessageBuilder(MsgType.INFO)
                    .setResponseClementineInfo(ResponseClementineInfo.newBuilder()
                            .setVersion("Clementine chunked")
                            .setState(EngineState.Playing))
                    .build();
            byte[] data = info.toByteArray();
            int split = data.length / 2;

            DataOutputStream out = new DataOutputStream(client.getOutputStream());
            Thread writer = new Thread(() -> {
                try {
                    out.writeInt(data.length);
                    out.write(data, 0, split);
                    out.flush();
                    // Exceed the 100 ms SO_TIMEOUT mid-frame so readFrameRemaining must retry.
                    Thread.sleep(250);
                    out.write(data, split, data.length - split);
                    out.flush();
                } catch (Exception ignored) {
                }
            });
            writer.start();

            ClementineMessage reply = mConnection.getProtoc(100);
            writer.join(2000);
            assertEquals(MsgType.INFO, reply.getMessageType());
            assertEquals("Clementine chunked", App.Clementine.getVersion());
        }
    }

    @Test
    public void failsWhenInFlightFrameExceedsIdleBudget() throws Exception {
        assertTrue(mConnection.createConnection(connectMessage(0)));
        try (Socket client = mServer.accept()) {
            // Send 4 of 100 expected payload bytes then stall past the 120 ms
            // mid-frame idle budget across 50 ms SO_TIMEOUT reads.
            DataOutputStream out = new DataOutputStream(client.getOutputStream());
            out.writeInt(100);
            out.write(new byte[] {1, 2, 3, 4});
            out.flush();

            ClementineMessage reply = mConnection.readProtoc(50, 120, false);
            assertEquals(ErrorMessage.IO_EXCEPTION, reply.getErrorMessage());
        }
    }

    @Test
    public void readRawProtocDoesNotMutateGlobalAppState() throws Exception {
        App.Clementine.setVersion("Foreground Server");
        assertTrue(mConnection.createConnection(connectMessage(0)));
        try (Socket client = mServer.accept()) {
            DataOutputStream out = new DataOutputStream(client.getOutputStream());
            writeFrame(out, ClementineMessage.getMessageBuilder(MsgType.INFO)
                    .setResponseClementineInfo(ResponseClementineInfo.newBuilder()
                            .setVersion("Background Server")
                            .setState(EngineState.Empty))
                    .build());

            ClementineMessage raw = mConnection.readRawProtoc(1000);
            assertEquals(MsgType.INFO, raw.getMessageType());
            assertEquals("Background Server", raw.getMessage().getResponseClementineInfo().getVersion());
            assertEquals("Foreground Server", App.Clementine.getVersion());
        }
    }
}
