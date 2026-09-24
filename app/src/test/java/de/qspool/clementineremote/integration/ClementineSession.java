package de.qspool.clementineremote.integration;

import java.util.ArrayList;
import java.util.List;

import de.qspool.clementineremote.backend.ClementineSimpleConnection;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A connection to a real Clementine, driven through the app's own connection,
 * message factory and parser.
 */
class ClementineSession implements AutoCloseable {

    static final String HOST = System.getProperty("clementine.host");

    static final int PORT = Integer.getInteger("clementine.port", 5500);

    static final int AUTH_CODE = Integer.getInteger("clementine.authCode", 0);

    private static final long DEFAULT_TIMEOUT_MS = 15000;

    private final ClementineSimpleConnection mConnection = new ClementineSimpleConnection();

    /** Every message received so far, in order. */
    final List<Message> received = new ArrayList<>();

    static ClementineSession connect(int authCode, boolean downloader) {
        ClementineSession session = new ClementineSession();
        ClementineMessage connect = ClementineMessageFactory
                .buildConnectMessage(HOST, PORT, authCode, false, downloader);
        assertTrue("Could not connect to Clementine at " + HOST + ":" + PORT,
                session.mConnection.createConnection(connect));
        return session;
    }

    /** Connects and waits until Clementine has sent its initial state. */
    static ClementineSession connectAndSync() {
        ClementineSession session = connect(AUTH_CODE, false);
        session.await(MsgType.FIRST_DATA_SENT_COMPLETE);
        return session;
    }

    void send(ClementineMessage message) {
        assertTrue("Sending " + message.getMessageType() + " failed",
                mConnection.sendRequest(message));
    }

    void send(MsgType type) {
        send(ClementineMessage.getMessage(type));
    }

    Message await(MsgType type) {
        return await(type, message -> true);
    }

    /** Reads messages until one of the given type matches, or fails after a timeout. */
    Message await(MsgType type, Matcher matcher) {
        long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            ClementineMessage message = mConnection.getProtoc(
                    (int) Math.max(1, deadline - System.currentTimeMillis()));
            if (message.isErrorMessage()) {
                if (message.getErrorMessage() == ErrorMessage.TIMEOUT) {
                    break;
                }
                fail("Connection error while waiting for " + type + ": "
                        + message.getErrorMessage());
            }
            received.add(message.getMessage());
            if (message.getMessageType() == type && matcher.matches(message.getMessage())) {
                return message.getMessage();
            }
        }
        fail("Timed out waiting for " + type + "; received " + receivedTypes());
        return null;
    }

    private List<MsgType> receivedTypes() {
        List<MsgType> types = new ArrayList<>();
        for (Message m : received) {
            types.add(m.getType());
        }
        return types;
    }

    @Override
    public void close() {
        mConnection.disconnect(ClementineMessage.getMessage(MsgType.DISCONNECT));
    }

    interface Matcher {
        boolean matches(Message message);
    }
}
