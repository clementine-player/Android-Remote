package de.qspool.clementineremote.backend;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
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

import static org.junit.Assert.assertEquals;
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
}
