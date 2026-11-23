/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.backend;

import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.VisibleForTesting;

import java.net.Socket;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message.Builder;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestConnect;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect;

/**
 * This Thread-Class is used to communicate with Clementine
 */
public class ClementinePlayerConnection extends ClementineSimpleConnection
        implements Runnable {

    public volatile ClementineConnectionHandler mHandler;

    public final static int PROCESS_PROTOC = 874456;

    public enum ConnectionStatus {
        IDLE,
        CONNECTING,
        NO_CONNECTION,
        CONNECTED,
        RECONNECTING,
        RECONNECTED,
        LOST_CONNECTION,
        DISCONNECTED,
    }

    private final long KEEP_ALIVE_TIMEOUT = 25000; // 25 Second timeout

    private final int MAX_RECONNECTS = 5;

    private volatile Handler mUiHandler;

    private volatile int mLeftReconnects;

    private volatile long mLastKeepAlive;

    // CopyOnWriteArrayList lets connection/reader threads iterate safely while
    // service/UI code registers listeners.
    private final List<PlayerConnectionListener> mListeners
            = new CopyOnWriteArrayList<>();

    private volatile ClementineMessage mRequestConnect;

    private long mStartTx;

    private long mStartRx;

    private long mStartTime;

    private volatile Thread mIncomingThread;

    // Serializes reconnect() attempts across keep-alive and sendRequest().
    private final Object mReconnectLock = new Object();

    // Incremented on each reconnect/teardown so ClementineIncoming distinguishes
    // a socket closed for replacement from a failure on the current socket.
    private final AtomicInteger mGeneration = new AtomicInteger(0);

    private volatile boolean mClosing = false;

    // Ensures closeConnection() emits DISCONNECTED and quits the looper once.
    private boolean mClosedFired = false;

    /**
     * Add a new listener for closed connections
     *
     * @param listener The listener object
     */
    public void addPlayerConnectionListener(PlayerConnectionListener listener) {
        mListeners.add(listener);
    }

    public void run() {
        // Start the thread
        Looper.prepare();
        mHandler = new ClementineConnectionHandler(this);

        fireOnConnectionStatusChanged(ConnectionStatus.IDLE);

        Looper.loop();
    }

    public synchronized boolean isClosedOrClosing() {
        return mClosing || mClosedFired;
    }

    /**
     * Try to connect to Clementine
     *
     * @param message The Request Object. Stores the ip to connect to.
     */
    @Override
    public boolean createConnection(ClementineMessage message) {
        fireOnConnectionStatusChanged(ConnectionStatus.CONNECTING);

        // Tear down any prior socket/reader/reconnect loop before reinitializing connection state.
        mClosing = true;
        mGeneration.incrementAndGet();
        abortConnecting();
        closeSocket();
        Thread previousIncoming = mIncomingThread;
        if (previousIncoming != null && previousIncoming != Thread.currentThread()) {
            previousIncoming.interrupt();
            try {
                previousIncoming.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendUiMessage(new ClementineMessage(ErrorMessage.NO_CONNECTION));
                fireOnConnectionStatusChanged(ConnectionStatus.NO_CONNECTION);
                return false;
            }
        }
        synchronized (mReconnectLock) {
            // Wait until any exiting reconnect() loop sees mClosing / incremented generation.
        }
        mIncomingThread = null;

        // Reset the connected flag and lifecycle state
        mLastKeepAlive = 0;
        mClosing = false;
        mClosedFired = false;

        // Now try to connect and set the input and output streams
        boolean connected = super.createConnection(message);

        // Check if Clementine dropped the connection.
        // Is possible when we connect from a public ip and clementine rejects it
        Socket socket = mSocket;
        if (connected && socket != null && !socket.isClosed()) {
            // Now we are connected

            // We can now reconnect MAX_RECONNECTS times when
            // we get a keep alive timeout
            mLeftReconnects = MAX_RECONNECTS;

            // Set the current time to last keep alive
            setLastKeepAlive(System.currentTimeMillis());

            // Until we get a new connection request from ui,
            // don't request the first data a second time
            RequestConnect requestConnect = message.getMessage().getRequestConnect();
            mRequestConnect = ClementineMessageFactory
                    .buildConnectMessage(message.getIp(), message.getPort(),
                            requestConnect.getAuthCode(),
                            false,
                            requestConnect.getDownloader(),
                            // Still an output after reconnecting.
                            requestConnect.hasRenderer() ? requestConnect.getRenderer() : null);

            // Save started transmitted bytes
            int uid = App.getApp().getApplicationInfo().uid;
            mStartTx = TrafficStats.getUidTxBytes(uid);
            mStartRx = TrafficStats.getUidRxBytes(uid);

            mStartTime = new Date().getTime();

            // Create a new thread for reading data from Clementine.
            // This is done blocking, so we receive the data directly instead of
            // waiting for the handler and still be able to send commands directly.
            Thread incomingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!mClosing && !Thread.currentThread().isInterrupted()) {
                        if (!checkKeepAlive()) {
                            break;
                        }

                        int loopGen;
                        synchronized (mReconnectLock) {
                            if (mClosing || !isConnected()) {
                                break;
                            }
                            // Capture generation before reading so we can ignore
                            // socket errors caused by reconnect() swapping sockets.
                            loopGen = mGeneration.get();
                        }

                        ClementineMessage m = getProtoc(3000);
                        if (mClosing) {
                            // closeSocket() unblocks getProtoc() during teardown.
                            break;
                        }
                        if (m.isErrorMessage()) {
                            if (m.getErrorMessage() == ErrorMessage.TIMEOUT) {
                                continue;
                            }
                            synchronized (mReconnectLock) {
                                // Socket was replaced while getProtoc() blocked;
                                // keep reading on the new socket instead of closing.
                                if (mClosing || loopGen != mGeneration.get()) {
                                    continue;
                                }
                            }
                        }

                        ClementineConnectionHandler handler = mHandler;
                        if (handler != null) {
                            Message msg = Message.obtain();
                            msg.obj = m;
                            msg.arg1 = PROCESS_PROTOC;
                            handler.sendMessage(msg);
                        }
                        if (m.isErrorMessage() || m.getMessageType() == MsgType.DISCONNECT) {
                            break;
                        }
                    }
                }
            }, "ClementineIncoming");
            mIncomingThread = incomingThread;
            incomingThread.start();

            // Get hostname
            if (socket.getInetAddress() != null) {
                App.Clementine.setHostname(socket.getInetAddress().getHostName());
            }

            fireOnConnectionStatusChanged(ConnectionStatus.CONNECTED);

        } else {
            sendUiMessage(new ClementineMessage(ErrorMessage.NO_CONNECTION));
            fireOnConnectionStatusChanged(ConnectionStatus.NO_CONNECTION);
            connected = false;
        }

        return connected;
    }

    /**
     * Process the received protocol buffer
     *
     * @param clementineMessage The Message received from Clementine
     */
    protected void processProtocolBuffer(ClementineMessage clementineMessage) {
        fireOnClementineMessageReceived(clementineMessage);

        // Close the connection if we have an old proto verion
        if (clementineMessage.isErrorMessage()) {
            closeConnection(clementineMessage);
        } else if (clementineMessage.getMessageType() == MsgType.DISCONNECT) {
            closeConnection(clementineMessage);
        } else {
            sendUiMessage(clementineMessage);
        }
    }

    /**
     * Send a message to the ui thread
     *
     * @param obj The Message containing data
     */
    private void sendUiMessage(Object obj) {
        Message msg = Message.obtain();
        msg.obj = obj;
        // Send the Messages
        if (mUiHandler != null) {
            mUiHandler.sendMessage(msg);
        }
    }

    /**
     * Send a request to clementine
     *
     * @param message The request as a RequestToThread object
     * @return true if data was sent, false if not
     */
    @Override
    public boolean sendRequest(ClementineMessage message) {
        if (mClosing) {
            return false;
        }
        // Send the request to Clementine
        boolean ret = super.sendRequest(message);

        // If we lost connection, try to reconnect once and resend the command
        if (!ret && !mClosing) {
            if (reconnect(1, 0L)) {
                ret = super.sendRequest(message);
            }
            if (!ret && !mClosing) {
                // Failed. Close connection
                Builder builder = ClementineMessage.getMessageBuilder(MsgType.DISCONNECT);
                ResponseDisconnect.Builder disc = ResponseDisconnect.newBuilder();
                disc.setReasonDisconnect(ReasonDisconnect.Server_Shutdown);
                builder.setResponseDisconnect(disc);
                closeConnection(new ClementineMessage(builder));
            }
        }

        return ret;
    }

    /**
     * Disconnect from Clementine
     *
     * @param message The RequestDisconnect Object
     */
    @Override
    public void disconnect(ClementineMessage message) {
        // Always mark closing and drive closeConnection even if already disconnected, so the
        // looper and listeners never stay stranded.
        mClosing = true;
        if (isConnected()) {
            super.disconnect(message);
        }
        closeConnection(message);
    }

    /**
     * Close the socket and the streams
     */
    void closeConnection(ClementineMessage clementineMessage) {
        mClosing = true;
        // Disconnect socket
        closeSocket();

        // Only the connection's own thread can stop its looper. Off it, as when reconnecting
        // fails on the thread reading from Clementine, the connection's thread closes it.
        ClementineConnectionHandler handler = mHandler;
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            Message msg = Message.obtain();
            msg.obj = clementineMessage;
            msg.arg1 = PROCESS_PROTOC;
            handler.sendMessage(msg);
            return;
        }

        synchronized (this) {
            // Multiple paths (reader error, server DISCONNECT, local disconnect)
            // can invoke closeConnection(); notify listeners only once.
            if (mClosedFired) {
                return;
            }
            mClosedFired = true;
        }

        sendUiMessage(clementineMessage);

        Thread incoming = mIncomingThread;
        // Skip self-join if somehow called directly on ClementineIncoming.
        if (incoming != null && incoming != Thread.currentThread()) {
            try {
                incoming.interrupt();
                incoming.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        mIncomingThread = null;

        // Fire the listener
        if (clementineMessage != null && clementineMessage.isErrorMessage() &&
                (clementineMessage.getErrorMessage() == ErrorMessage.IO_EXCEPTION ||
                        clementineMessage.getErrorMessage() == ErrorMessage.KEEP_ALIVE_TIMEOUT)) {
            fireOnConnectionStatusChanged(ConnectionStatus.LOST_CONNECTION);
        }

        fireOnConnectionStatusChanged(ConnectionStatus.DISCONNECTED);

        // Close thread (only quit the connection thread's own handler looper)
        if (handler != null && Looper.myLooper() == handler.getLooper()) {
            handler.getLooper().quit();
        }
    }

    /**
     * Check the keep alive timeout.
     * If we reached the timeout, we can assume, that we lost the connection.
     *
     * @return true if the connection remains usable, false if reconnect attempts exhausted.
     */
    private boolean checkKeepAlive() {
        long lastKeepAlive = mLastKeepAlive;
        if (lastKeepAlive > 0
                && (System.currentTimeMillis() - lastKeepAlive) > KEEP_ALIVE_TIMEOUT) {
            if (reconnect(MAX_RECONNECTS, KEEP_ALIVE_TIMEOUT)) {
                return true;
            }
            mLeftReconnects = 0;
            if (!mClosing) {
                ClementineConnectionHandler handler = mHandler;
                if (handler != null) {
                    Message msg = Message.obtain();
                    msg.obj = new ClementineMessage(ErrorMessage.KEEP_ALIVE_TIMEOUT);
                    msg.arg1 = PROCESS_PROTOC;
                    handler.sendMessage(msg);
                }
            }
            return false;
        }
        return true;
    }

    private boolean reconnect(int maxAttempts, long requiredExpiredMs) {
        boolean reconnected = false;
        final int reconnectGen;
        synchronized (mReconnectLock) {
            if (mClosing || mRequestConnect == null) {
                return false;
            }
            if (requiredExpiredMs > 0L) {
                // Re-check keep-alive under the lock: sendRequest() may already
                // have reconnected while checkKeepAlive() waited to enter.
                long latestKeepAlive = mLastKeepAlive;
                if (latestKeepAlive <= 0L
                        || (System.currentTimeMillis() - latestKeepAlive) <= requiredExpiredMs) {
                    Socket active = mSocket;
                    if (active != null && !active.isClosed()) {
                        return true;
                    }
                }
            }
            // Bump generation before closing so ClementineIncoming ignores the
            // resulting IOException on the old socket.
            reconnectGen = mGeneration.incrementAndGet();
            closeSocket();
            postConnectionStatusChanged(ConnectionStatus.RECONNECTING, reconnectGen);
            for (int attempt = 0;
                 attempt < maxAttempts && !mClosing && mGeneration.get() == reconnectGen;
                 attempt++) {
                if (super.createConnection(mRequestConnect)) {
                    Socket socket = mSocket;
                    // Discard if disconnect() raced while connect() was blocking.
                    if (mClosing || mGeneration.get() != reconnectGen) {
                        closeSocket();
                        return false;
                    }
                    if (socket != null && !socket.isClosed()) {
                        mLeftReconnects = MAX_RECONNECTS;
                        // Refresh keep-alive timestamp immediately so the next
                        // reader loop does not trigger another reconnect storm.
                        setLastKeepAlive(System.currentTimeMillis());
                        reconnected = true;
                        break;
                    }
                    closeSocket();
                }
            }
        }
        // Emit RECONNECTED outside mReconnectLock so listeners cannot deadlock.
        if (reconnected && !mClosing && mGeneration.get() == reconnectGen) {
            postConnectionStatusChanged(ConnectionStatus.RECONNECTED, reconnectGen);
        }
        return reconnected && !mClosing && mGeneration.get() == reconnectGen;
    }

    /** Delivers status changes on the connection's handler thread rather than under mReconnectLock. */
    private void postConnectionStatusChanged(ConnectionStatus status, int expectedGen) {
        ClementineConnectionHandler handler = mHandler;
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.post(() -> {
                // Terminal statuses still deliver after mClosing becomes true.
                if ((!mClosing && mGeneration.get() == expectedGen)
                        || status == ConnectionStatus.DISCONNECTED
                        || status == ConnectionStatus.LOST_CONNECTION) {
                    fireOnConnectionStatusChanged(status);
                }
            });
        } else if (!mClosing && mGeneration.get() == expectedGen) {
            fireOnConnectionStatusChanged(status);
        }
    }

    /**
     * Fire the event to all listeners
     *
     * @param status The current connection status
     */
    @VisibleForTesting
    void fireOnConnectionStatusChanged(ConnectionStatus status) {
        for (PlayerConnectionListener listener : mListeners) {
            listener.onConnectionStatusChanged(status);
        }
    }

    /**
     * Fire the event to all listeners
     */
    @VisibleForTesting
    void fireOnClementineMessageReceived(ClementineMessage msg) {
        for (PlayerConnectionListener listener : mListeners) {
            listener.onClementineMessageReceived(msg);
        }
    }

    /**
     * Set the ui Handler, to which the thread should talk to
     *
     * @param playerHandler The Handler
     */
    public void setUiHandler(Handler playerHandler) {
        this.mUiHandler = playerHandler;
    }

    /**
     * Set the last keep alive timestamp
     *
     * @param lastKeepAlive The time
     */
    public void setLastKeepAlive(long lastKeepAlive) {
        this.mLastKeepAlive = lastKeepAlive;
    }

    public long getStartTx() {
        return mStartTx;
    }

    public long getStartRx() {
        return mStartRx;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public ClementineMessage getRequestConnect() {
        return mRequestConnect;
    }
}
