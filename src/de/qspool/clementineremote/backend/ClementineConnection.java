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
import java.net.UnknownHostException;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.ClementineRemoteControlActivity;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.elements.ClementineElement;
import de.qspool.clementineremote.backend.elements.Disconnected;
import de.qspool.clementineremote.backend.elements.Disconnected.DisconnectReason;
import de.qspool.clementineremote.backend.elements.InvalidData;
import de.qspool.clementineremote.backend.elements.NoConnection;
import de.qspool.clementineremote.backend.elements.OldProtoVersion;
import de.qspool.clementineremote.backend.elements.Reload;
import de.qspool.clementineremote.backend.pb.ClementinePbCreator;
import de.qspool.clementineremote.backend.pb.ClementinePbParser;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.requests.CheckForData;
import de.qspool.clementineremote.backend.requests.RequestConnect;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.backend.requests.RequestToThread;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

/**
 * This Thread-Class is used to communicate with Clementine
 */
public class ClementineConnection extends Thread {
	public ClementineConnectionHandler mHandler;
	
	private final int DELAY_MILLIS = 250;
	private final String TAG = "ClementineConnection";
	private final long KEEP_ALIVE_TIMEOUT = 60000; // 60 Second timeout
	private Socket mClient;
	private DataInputStream mIn;
	private DataOutputStream mOut;
	private Handler mUiHandler;
	private long mLastKeepAlive;
	private ClementinePbCreator mClementinePbCreator;
	private ClementinePbParser mClementinePbParser;
	
	private NotificationCompat.Builder mNotifyBuilder;
	private NotificationManager mNotificationManager;
	private int mNotifyId = 1;
	private int mNotificationWidth;
	private int mNotificationHeight;
	private MySong mLastSong = null;

	@Override
	public void run() {
		// Start the thread
		mClementinePbCreator = new ClementinePbCreator();
		mClementinePbParser  = new ClementinePbParser();
		Looper.prepare();
		mHandler = new ClementineConnectionHandler(this);
		
		Resources res = App.mApp.getResources();
		mNotificationHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
		mNotificationWidth  = (int) res.getDimension(android.R.dimen.notification_large_icon_width);
		
		Looper.loop();
	}
	
	/**
	 * Try to connect to Clementine
	 * @param r The Request Object. Stores the ip to connect to.
	 */
	void createConnection(RequestConnect r) {
		// Reset the connected flag
		App.mClementine.setConnected(false);
		mLastKeepAlive = 0;
		try {
			// Now try to connect and set the input and output streams
			SocketAddress socketAddress = new InetSocketAddress(r.getIp(), r.getPort());
			mClient = new Socket();
			mClient.connect(socketAddress, 3000);
			
			mIn  = new DataInputStream(mClient.getInputStream());
			mOut = new DataOutputStream(mClient.getOutputStream());
			
			// Check if Clementine dropped the connection.
			// Is possible when we connect from a public ip and clementine rejects it
			if (!mClient.isClosed()) {
				// Send the connect request to clementine
				sendRequest(r);
		
				// Enter the main loop in the thread
				Message msg = Message.obtain();
				msg.obj = new CheckForData();
				mHandler.sendMessage(msg);
				
				// Now we are connected
				App.mClementine.setConnected(true);
				setupNotification();
			}
		} catch(UnknownHostException e) {
			// If we can't connect, then tell that the ui-thread 
			sendUiMessage(new NoConnection());
			Log.d(TAG, "Unknown host: " + r.getIp());
		} catch(IOException e) {
			sendUiMessage(new NoConnection());
			Log.d(TAG, "No I/O");
		}
	}
	
	/**
	 * Check if we have data to process
	 */
	void checkForData() {
		try {
			// If there is no data, then check the keep alive timeout
			if (mIn.available() == 0) {
				checkKeepAlive();
			} else {
				// Otherwise read the data and parse it
				int len = mIn.readInt();
				byte[] data = new byte[len];
				mIn.readFully(data, 0, len);
				processProtocolBuffer(data);
			}
		} catch (IOException e) {
			sendUiMessage(new InvalidData());
		}
		
		// Let the looper send the message again
		if (App.mClementine.isConnected()) {
			Message msg = Message.obtain();
			msg.obj = new CheckForData();
			mHandler.sendMessageDelayed(msg, DELAY_MILLIS);
		}
	}
	
	/**
	 * Process the received protocol buffer
	 * @param bs The binary representation of the protocol buffer
	 */
	private void processProtocolBuffer(byte[] bs) {
		// Send the parsed Message to the ui thread
		ClementineElement clementineElement = mClementinePbParser.parse(bs);
		sendUiMessage(clementineElement);
		
		// Close the connection if we have an old proto verion
		if (clementineElement instanceof OldProtoVersion) {
			closeConnection();
			App.mClementine.setConnected(false);
			sendUiMessage(new Disconnected(DisconnectReason.WRONG_PROTO));
		} else if (clementineElement instanceof Reload) {
	    	// Now update the notification
			if (App.mClementine.getCurrentSong() != mLastSong
			 && App.mClementine.getCurrentSong().getArt() != null) {
				mLastSong = App.mClementine.getCurrentSong();
				Bitmap scaledArt = Bitmap.createScaledBitmap(App.mClementine.getCurrentSong().getArt(), 
														mNotificationWidth, 
														mNotificationHeight, 
														false);
		    	mNotifyBuilder.setLargeIcon(scaledArt);
		    	mNotifyBuilder.setContentTitle(App.mClementine.getCurrentSong().getArtist());
		    	mNotifyBuilder.setContentText(App.mClementine.getCurrentSong().getTitle() + 
		    								  " / " + 
		    								  App.mClementine.getCurrentSong().getAlbum());
		    	mNotificationManager.notify(mNotifyId, mNotifyBuilder.build());
			}
		}
	}
	
	/**
	 * Send a message to the ui thread
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
	 * @param r The request as a RequestToThread object
	 */
	void sendRequest(RequestToThread r) {
		// Create the protocolbuffer
		byte[] data = mClementinePbCreator.createRequest(r);
		try {
			mOut.writeInt(data.length);
			mOut.write(data);
			mOut.flush();
		} catch (IOException e) {
			Log.e(TAG, "Error writing to socket: " + e.getMessage());
		}
	}
	
	/**
	 * Disconnect from Clementine
	 * @param r The RequestDisconnect Object
	 */
	void disconnect(RequestDisconnect r) {
		Log.d(TAG, "Disconnect Request");
		if (App.mClementine.isConnected()) {
			// Set the Connected flag to false, so the loop in
			// checkForData() is interrupted
			App.mClementine.setConnected(false);
			
			// Send the disconnect message to clementine
			byte[] data = mClementinePbCreator.createRequest(r);
			
			try {
				// Now send the data
				mOut.writeInt(data.length);
				mOut.write(data);
				mOut.flush();
				
				// and close the connection
				closeConnection();
			} catch (IOException e) {	
			}
		}
			
		// Send the result to the ui thread
		sendUiMessage(new Disconnected(DisconnectReason.CLIENT_CLOSE));
		
		// Check if the thread shall be closed
		if (r.getKillThread()) {
			Looper.myLooper().quit();
		}
	}
	
	/**
	 * Close the socket and the streams
	 */
	private void closeConnection() {
		mNotificationManager.cancel(mNotifyId);
		try {
			mClient.close();
			mIn.close();
			mOut.close();
		} catch (IOException e) {
		}
		
	}

	/**
	 * Set the ui Handler, to which the thread should talk to
	 * @param playerHandler The Handler
	 */
	public void setUiHandler(Handler playerHandler) {
		this.mUiHandler = playerHandler;
	}
	
	/**
	 * Check the keep alive timeout.
	 * If we reached the timeout, we can assume, that we lost the connection
	 */
	private void checkKeepAlive() {
		if (mLastKeepAlive > 0 && (System.currentTimeMillis() - mLastKeepAlive) > KEEP_ALIVE_TIMEOUT ) {
			Log.d(TAG, "KeepAlive disconnect");
			closeConnection();
			
			// Tell the ui, that we lost the connection
			App.mClementine.setConnected(false);
			sendUiMessage(new NoConnection());
		}
	}
	
	/**
	 * Set the last keep alive timestamp
	 * @param lastKeepAlive The time
	 */
	public void setLastKeepAlive(long lastKeepAlive) {
		this.mLastKeepAlive = lastKeepAlive;
	}
	
	/**
	 * Setup the Notification
	 */
	private void setupNotification() {
		mNotificationManager = (NotificationManager) App.mApp.getSystemService(Context.NOTIFICATION_SERVICE);
	    mNotifyBuilder = new NotificationCompat.Builder(App.mApp);
	    mNotifyBuilder.setSmallIcon(R.drawable.ic_launcher);
	    mNotifyBuilder.setOngoing(true);
	    
	    // Set the result intent
	    Intent resultIntent = new Intent(App.mApp, ClementineRemoteControlActivity.class);
	    resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    // Create a TaskStack, so the app navigates correctly backwards
	    TaskStackBuilder stackBuilder = TaskStackBuilder.create(App.mApp);
	    stackBuilder.addParentStack(ClementineRemoteControlActivity.class);
	    stackBuilder.addNextIntent(resultIntent);
	    PendingIntent resultPendingintent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
	    mNotifyBuilder.setContentIntent(resultPendingintent);
	}
}
