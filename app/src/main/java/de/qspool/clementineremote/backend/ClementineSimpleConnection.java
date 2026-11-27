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

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementinePbParser;

public class ClementineSimpleConnection {

    private static final int MAX_FRAME_BYTES = 52428800;

    // Bounds how long an incomplete frame can stall once bytes start arriving.
    private static final long PARTIAL_FRAME_IDLE_TIMEOUT_MS = 10_000L;

    // Socket, input and output streams. Volatile, for abortConnecting and readers off the write lock.
    protected volatile Socket mSocket;

    protected volatile DataInputStream mIn;

    protected volatile DataOutputStream mOut;

    // Guards socket/stream swap and teardown independently of mWriteLock so
    // closeSocket() never blocks behind an in-flight write.
    private final Object mSocketLock = new Object();

    // Serializes length+payload writes so concurrent senders cannot interleave
    // frame bytes.
    private final Object mWriteLock = new Object();

    // Protocol buffer data
    private final ClementinePbParser mClementinePbParser = new ClementinePbParser();

    /**
     * Where this connection reached Clementine, or null before it has. Clementine serves its
     * media there too.
     */
    public InetSocketAddress getServerAddress() {
        Socket socket = mSocket;
        if (socket == null || !socket.isConnected()) {
            return null;
        }
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    /**
     * Try to connect to Clementine
     *
     * @param message The Request Object. Stores the ip to connect to.
     */
    public boolean createConnection(ClementineMessage message) {
        if (message == null || message.isErrorMessage()) {
            return false;
        }
        SocketAddress socketAddress = new InetSocketAddress(message.getIp(), message.getPort());
        Socket socket = new Socket();
        synchronized (mSocketLock) {
            mSocket = socket;
            mIn = null;
            mOut = null;
        }
        try {
            socket.connect(socketAddress, 3000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));
            synchronized (mSocketLock) {
                // abortConnecting() or closeSocket() may have closed or swapped
                // mSocket while connect() was blocking; discard stale streams.
                if (mSocket != socket || socket.isClosed()) {
                    closeQuietly(out);
                    closeQuietly(in);
                    closeQuietly(socket);
                    return false;
                }
                mIn = in;
                mOut = out;
            }

            // Send the connect request using private writeRequest so subclass overrides never re-enter
            if (!writeRequest(message)) {
                closeSocket();
                return false;
            }
        } catch (IOException e) {
            closeSocket();
            return false;
        }

        return true;
    }

    /**
     * Gives up on a connection attempt still under way, from another thread: createConnection
     * then fails at once instead of waiting for its timeout. Does nothing once connected.
     */
    public void abortConnecting() {
        Socket socket = mSocket;
        if (socket != null && !socket.isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * Send a request to clementine
     *
     * @param message The request as a RequestToThread object
     * @return true if data was sent, false if not
     */
    public boolean sendRequest(ClementineMessage message) {
        return writeRequest(message);
    }

    private boolean writeRequest(ClementineMessage message) {
        if (message == null || message.isErrorMessage()) {
            return false;
        }
        byte[] data = message.getMessage().toByteArray();
        synchronized (mWriteLock) {
            DataOutputStream out = mOut;
            if (out == null) {
                return false;
            }
            try {
                out.writeInt(data.length);
                out.write(data);
                out.flush();
                return true;
            } catch (Exception e) {
                // Try to reconnect
                closeSocket();
                return false;
            }
        }
    }

    /**
     * Get the raw protocol buffer message. This function blocks until data is available!
     * Only returns {@link ErrorMessage#TIMEOUT} when 0 bytes of a frame have arrived before timeout.
     *
     * @param timeout Timeout in milliseconds (0 waits indefinitely)
     * @return The parsed protocol buffer
     */
    public ClementineMessage getProtoc(int timeout) {
        return readProtoc(timeout, PARTIAL_FRAME_IDLE_TIMEOUT_MS, true);
    }

    /**
     * Reads and decodes one protocol buffer message without mutating global
     * player state. Used by background sync/downloaders so their frames do not
     * overwrite foreground {@link App#Clementine} or playlist state.
     *
     * @param timeout Timeout in milliseconds (0 waits indefinitely)
     * @return The decoded protocol buffer
     */
    public ClementineMessage readRawProtoc(int timeout) {
        return readProtoc(timeout, PARTIAL_FRAME_IDLE_TIMEOUT_MS, false);
    }

    ClementineMessage readProtoc(int timeout, long partialIdleTimeoutMs, boolean mutateState) {
        Socket socket = mSocket;
        DataInputStream in = mIn;
        if (socket == null || in == null) {
            return new ClementineMessage(ErrorMessage.IO_EXCEPTION);
        }
        try {
            // Read only byte 0 with SO_TIMEOUT so TIMEOUT is returned only when
            // no frame has started. Once >= 1 byte arrives, finish the full
            // frame so the caller cannot loop and misread mid-frame bytes as a
            // new length header.
            socket.setSoTimeout(timeout);
            byte[] header = new byte[4];
            int first = in.read(header, 0, 1);
            if (first <= 0) {
                throw new EOFException("Unexpected EOF reading frame length");
            }

            // Use a finite mid-frame idle budget only when timeout > 0;
            // timeout == 0 waits indefinitely, so the mid-frame budget is unbounded too.
            long idleBudgetMs = timeout > 0
                    ? Math.max(timeout, partialIdleTimeoutMs)
                    : 0L;
            readFrameRemaining(socket, in, header, 1, 4, timeout, idleBudgetMs);

            int len = ((header[0] & 0xFF) << 24)
                    | ((header[1] & 0xFF) << 16)
                    | ((header[2] & 0xFF) << 8)
                    | (header[3] & 0xFF);
            // Check length. If it is less than zero or more than 50 MiB it's likely invalid data.
            if (len < 0 || len > MAX_FRAME_BYTES) {
                throw new IOException("Invalid data length");
            }
            byte[] data = new byte[len];
            if (len > 0) {
                readFrameRemaining(socket, in, data, 0, len, timeout, idleBudgetMs);
            }
            return mutateState
                    ? mClementinePbParser.parse(data)
                    : mClementinePbParser.decode(data);
        } catch (SocketTimeoutException e) {
            return new ClementineMessage(ErrorMessage.TIMEOUT);
        } catch (IOException e) {
            return new ClementineMessage(ErrorMessage.IO_EXCEPTION);
        }
    }

    private static void readFrameRemaining(
            Socket socket,
            DataInputStream in,
            byte[] buffer,
            int offset,
            int totalLength,
            int soTimeoutMs,
            long idleBudgetMs) throws IOException {
        long lastProgressNs = System.nanoTime();
        long idleBudgetNs = TimeUnit.MILLISECONDS.toNanos(idleBudgetMs);
        int pos = offset;
        while (pos < totalLength) {
            if (Thread.currentThread().isInterrupted() || socket.isClosed()) {
                throw new IOException("Connection interrupted or closed mid-frame");
            }
            try {
                if (soTimeoutMs > 0) {
                    socket.setSoTimeout(soTimeoutMs);
                }
                int read = in.read(buffer, pos, totalLength - pos);
                if (read < 0) {
                    throw new EOFException("Unexpected EOF mid-frame");
                }
                if (read > 0) {
                    pos += read;
                    // Large frames can legitimately span multiple SO_TIMEOUT
                    // slices; only stall time without progress counts toward
                    // idleBudgetNs.
                    lastProgressNs = System.nanoTime();
                }
            } catch (SocketTimeoutException e) {
                if (idleBudgetNs > 0 && (System.nanoTime() - lastProgressNs) >= idleBudgetNs) {
                    throw new IOException("Timed out mid-frame after partial read", e);
                }
            }
        }
    }

    /**
     * Check if the Socket is still connected
     *
     * @return true if a connection is established
     */
    public boolean isConnected() {
        Socket socket = mSocket;
        DataOutputStream out = mOut;
        return socket != null
                && out != null
                && socket.isConnected()
                && !socket.isClosed();
    }

    /**
     * Disconnect from Clementine
     *
     * @param message The RequestDisconnect Object
     */
    public void disconnect(ClementineMessage message) {
        // Synthetic error or null messages cannot be serialized onto the wire.
        if (message == null || message.isErrorMessage()) {
            closeSocket();
            return;
        }
        if (isConnected()) {
            // Send the disconnect message to clementine
            byte[] data = message.getMessage().toByteArray();
            // Serialize the final disconnect frame against concurrent writes.
            synchronized (mWriteLock) {
                DataOutputStream out = mOut;
                if (out != null) {
                    try {
                        // Now send the data
                        out.writeInt(data.length);
                        out.write(data);
                        out.flush();
                    } catch (IOException ignored) {
                    }
                }
                closeSocket();
            }
        }
    }

    /**
     * Close the socket and the in and out streams
     */
    protected void closeSocket() {
        Socket socket;
        DataInputStream in;
        DataOutputStream out;
        // Detach references atomically under mSocketLock, then close outside it;
        // close in and socket in finally so an out.close() error cannot leak FDs.
        synchronized (mSocketLock) {
            socket = mSocket;
            in = mIn;
            out = mOut;
            mSocket = null;
            mIn = null;
            mOut = null;
        }
        try {
            closeQuietly(out);
        } finally {
            closeQuietly(in);
            closeQuietly(socket);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
