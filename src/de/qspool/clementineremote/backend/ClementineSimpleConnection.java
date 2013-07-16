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

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.elements.ClementineElement;
import de.qspool.clementineremote.backend.elements.InvalidData;
import de.qspool.clementineremote.backend.pb.ClementinePbCreator;
import de.qspool.clementineremote.backend.pb.ClementinePbParser;
import de.qspool.clementineremote.backend.requests.RequestConnect;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.backend.requests.RequestToThread;

public class ClementineSimpleConnection extends Socket {
	private DataInputStream mIn;
	private DataOutputStream mOut;
	private ClementinePbCreator mClementinePbCreator = new ClementinePbCreator();
	private ClementinePbParser mClementinePbParser = new ClementinePbParser();
	
	/**
	 * Try to connect to Clementine
	 * @param r The Request Object. Stores the ip to connect to.
	 * @throws IOException 
	 */
	public boolean createConnection(RequestConnect r) {
		SocketAddress socketAddress = new InetSocketAddress(r.getIp(), r.getPort());
		try {
			connect(socketAddress, 3000);
			mIn  = new DataInputStream(getInputStream());
			mOut = new DataOutputStream(getOutputStream());
			
			// Send the connect request to clementine
			sendRequest(r);
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Send a request to clementine
	 * @param r The request as a RequestToThread object
	 */
	public boolean sendRequest(RequestToThread r) {
		// Create the protocolbuffer
		byte[] data = mClementinePbCreator.createRequest(r);
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
	 * Get the raw protocol buffer message
	 */
	public ClementineElement getProtoc() {
		ClementineElement element = new InvalidData();
		int secondsToWait = 30;
		
		try {
			// If there is no data, then check the keep alive timeout
			while (mIn.available() == 0) {
				secondsToWait--;
				if (secondsToWait == 0) {
					Log.d("getProtoc", "Timeout");
					break;
				}
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
			
			if (secondsToWait > 0) {
				// Read the data and return it
				int len = mIn.readInt();
				byte[] data = new byte[len];
				mIn.readFully(data, 0, len);
				element = mClementinePbParser.parse(data);
			}
			
		} catch (IOException e) {
			Log.d("getProtoc", "IOException");
		}
		
		return element;
	}
	
	/**
	 * Disconnect from Clementine
	 * @param r The RequestDisconnect Object
	 */
	public void disconnect(RequestDisconnect r) {
		if (App.mClementine.isConnected()) {			
			// Send the disconnect message to clementine
			byte[] data = mClementinePbCreator.createRequest(r);
			
			try {
				// Now send the data
				mOut.writeInt(data.length);
				mOut.write(data);
				mOut.flush();
				
				// Do not close the connection, Clementine will do this!
				//closeSocket();
			} catch (IOException e) {
				Log.d("disconnect", "IOException");
			}
		}
	}
	
	/**
	 * Close the socket and the in and out streams
	 */
	private void closeSocket() {
		try {
			mOut.close();
		} catch (IOException e) {
		}
		
	}
}
