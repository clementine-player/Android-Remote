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

import android.util.Log;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementinePbParser;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;

public class ClementineSimpleConnection {
	// Socket, input and output streams
	protected Socket mSocket;
	protected DataInputStream mIn;
	protected DataOutputStream mOut;
	
	// Protocol buffer data
	private ClementinePbParser mClementinePbParser = new ClementinePbParser();
	
	/**
	 * Try to connect to Clementine
	 * @param r The Request Object. Stores the ip to connect to.
	 * @throws IOException 
	 */
	public boolean createConnection(ClementineMessage message) {
		SocketAddress socketAddress = new InetSocketAddress(message.getIp(), message.getPort());
		mSocket = new Socket();
		try {
			mSocket.connect(socketAddress, 3000);
			mIn  = new DataInputStream(mSocket.getInputStream());
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
	 * @param r The request as a RequestToThread object
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
	 * @returns The parsed protocol buffer
	 */
	public ClementineMessage getProtoc() {
		ClementineMessage message = null;
		try {
			// Read the data and return it
			int len = mIn.readInt();
			byte[] data = new byte[len];
			mIn.readFully(data, 0, len);
			message = mClementinePbParser.parse(data);
		} catch (IOException e) {
			Log.d("getProtoc", "IOException");
			message = new ClementineMessage(ErrorMessage.INVALID_DATA);
		}
		
		return message;
	}
	
	/**
	 * Check if the Socket is still connected
	 * @return true if a connection is established
	 */
	public boolean isConnected() {
		if (mSocket == null
		 || !mSocket.isConnected())
			return false;
		else
			return true;
	}
	
	/**
	 * Disconnect from Clementine
	 * @param r The RequestDisconnect Object
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
		} catch (IOException e) {
		}
		
	}
}
