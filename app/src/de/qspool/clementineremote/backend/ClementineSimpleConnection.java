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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementinePbParser;

public class ClementineSimpleConnection {

    // Socket, input and output streams
    protected Socket mSocket;

    protected DataInputStream mIn;

    protected DataOutputStream mOut;

    // Protocol buffer data
    private ClementinePbParser mClementinePbParser = new ClementinePbParser();

    /**
     * Try to connect to Clementine
     *
     * @param message The Request Object. Stores the ip to connect to.
     */
    public boolean createConnection(ClementineMessage message) {
        SocketAddress socketAddress = new InetSocketAddress(message.getIp(), message.getPort());
        mSocket = new Socket();
        try {
            mSocket.connect(socketAddress, 3000);
            mIn = new DataInputStream(mSocket.getInputStream());
            mOut = new DataOutputStream(mSocket.getOutputStream());

            // Send the connect request to clementine
            sendRequest(message);
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    /**
     * Send a request to clementine
     *
     * @param message The request as a RequestToThread object
     * @return true if data was sent, false if not
     */
    public boolean sendRequest(ClementineMessage message) {
        // Create the protocolbuffer
        byte[] data = message.getMessage().toByteArray();
        try {
            mOut.writeInt(data.length);
            mOut.write(data);
            mOut.flush();
        } catch (Exception e) {
            // Try to reconnect
            closeSocket();
            return false;
        }
        return true;
    }

    /**
     * Get the raw protocol buffer message. This function blocks until data is
     * available!
     *
     * @returns The parsed protocol buffer
     */
    public ClementineMessage getProtoc() {
        ClementineMessage message;
        try {
            // Read the data and return it
            mSocket.setSoTimeout(3000);
            int len = mIn.readInt();
            byte[] data = new byte[len];
            mIn.readFully(data, 0, len);
            message = mClementinePbParser.parse(data);
        } catch (SocketTimeoutException e) {
            message = new ClementineMessage(ErrorMessage.TIMEOUT);
        } catch (IOException e) {
            message = new ClementineMessage(ErrorMessage.IO_EXCEPTION);
        }

        return message;
    }

    /**
     * Check if the Socket is still connected
     *
     * @return true if a connection is established
     */
    public boolean isConnected() {
        if (mSocket == null
                || mOut == null
                || !mSocket.isConnected()
                || mSocket.isClosed()) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Disconnect from Clementine
     *
     * @param message The RequestDisconnect Object
     */
    public void disconnect(ClementineMessage message) {
        if (isConnected()) {
            // Send the disconnect message to clementine
            byte[] data = message.getMessage().toByteArray();

            try {
                // Now send the data
                mOut.writeInt(data.length);
                mOut.write(data);
                mOut.flush();

                closeSocket();
            } catch (IOException e) {
            }
        }
    }

    /**
     * Close the socket and the in and out streams
     */
    protected void closeSocket() {
        try {
            mOut.close();
            mSocket.close();
        } catch (IOException e) {
        }

    }
}
