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

import java.io.IOException;
import java.util.ArrayList;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.event.OnConnectionClosedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.MessageGroup;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message.Builder;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect;
import de.qspool.clementineremote.backend.pebble.Pebble;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.receivers.ClementineMediaButtonEventReceiver;

/**
 * This Thread-Class is used to communicate with Clementine
 */
public class ClementinePlayerConnection extends ClementineSimpleConnection
										  implements Runnable {
	public ClementineConnectionHandler mHandler;
	
	private final int DELAY_MILLIS = 250;
	private final long KEEP_ALIVE_TIMEOUT = 25000; // 25 Second timeout
	private final int MAX_RECONNECTS = 5;
	public final static int CHECK_FOR_DATA_ARG = 12387194;
	
	private Thread mThread;
	private Handler mUiHandler;
	
	private int mLeftReconnects;
	private long mLastKeepAlive;
	
	private NotificationCompat.Builder mNotifyBuilder;
	private NotificationManager mNotificationManager;
	private int mNotificationWidth;
	private int mNotificationHeight;
	private MySong mLastSong = null;
	private Clementine.State mLastState;
	
	private AudioManager mAudioManager;
	private ComponentName mClementineMediaButtonEventReceiver;
	private RemoteControlClient mRcClient;
	
	private ArrayList<OnConnectionClosedListener> mListeners = new ArrayList<OnConnectionClosedListener>();
	private ClementineMessage mRequestConnect;
	
	private PowerManager.WakeLock mWakeLock;
	
	private Pebble mPebble;
	
	public ClementinePlayerConnection() {
		mThread = new Thread(this);
	}
	
	/**
	 * Start the thread
	 */
	public void start() {
		mThread.start();
	}
	
	/**
	 * Add a new listener for closed connections
	 * @param listener The listener object
	 */
	public void setOnConnectionClosedListener(OnConnectionClosedListener listener) {
		mListeners.add(listener);
	}

	public void run() {
		// Start the thread
		mNotificationManager = (NotificationManager) App.mApp.getSystemService(Context.NOTIFICATION_SERVICE);
		
		Looper.prepare();
		mHandler = new ClementineConnectionHandler(this);
		
		mPebble = new Pebble();
		
		// Get a Wakelock Object
		PowerManager pm = (PowerManager) App.mApp.getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Clementine");
		
		Resources res = App.mApp.getResources();
		mNotificationHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
		mNotificationWidth  = (int) res.getDimension(android.R.dimen.notification_large_icon_width);
		
		mAudioManager = (AudioManager) App.mApp.getSystemService(Context.AUDIO_SERVICE);
		mClementineMediaButtonEventReceiver = new ComponentName(App.mApp.getPackageName(),
																ClementineMediaButtonEventReceiver.class.getName());
		
		Looper.loop();
	}
	
	public void setNotificationBuilder(NotificationCompat.Builder builder) {
		mNotifyBuilder = builder;
	}
	
	/**
	 * Try to connect to Clementine
	 * @param r The Request Object. Stores the ip to connect to.
	 */
	@Override
	public boolean createConnection(ClementineMessage message) {
		boolean connected = false;
		// Reset the connected flag
		App.mClementine.setConnected(false);
		mLastKeepAlive = 0;
		
		// Now try to connect and set the input and output streams
		connected = super.createConnection(message);
		
		// Check if Clementine dropped the connection.
		// Is possible when we connect from a public ip and clementine rejects it
		if (connected && !mSocket.isClosed()) {
			// Enter the main loop in the thread
			Message msg = Message.obtain();
			msg.arg1 = CHECK_FOR_DATA_ARG;
			mHandler.sendMessage(msg);
			
			// Now we are connected
			App.mClementine.setConnected(true);
			mLastSong = null;
			mLastState = App.mClementine.getState();
			
			// Setup the MediaButtonReceiver and the RemoteControlClient
			registerRemoteControlClient();
			
			updateNotification();
			
			// The device shall be awake
			mWakeLock.acquire();
			
			// We can now reconnect MAX_RECONNECTS times when
			// we get a keep alive timeout
			mLeftReconnects = MAX_RECONNECTS;
			
			// Set the current time to last keep alive
			setLastKeepAlive(System.currentTimeMillis());
			
			// Until we get a new connection request from ui,
			// don't request the first data a second time
			mRequestConnect = ClementineMessageFactory.buildConnectMessage(message.getIp(), message.getPort(), 
																	  message.getMessage().getRequestConnect().getAuthCode(), 
																	  false, 
																	  message.getMessage().getRequestConnect().getDownloader());
		} else {
			sendUiMessage(new ClementineMessage(ErrorMessage.NO_CONNECTION));
		}
		
		return connected;
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
				processProtocolBuffer(getProtoc());
			}
		} catch (IOException e) {
			sendUiMessage(new ClementineMessage(ErrorMessage.INVALID_DATA));
		}
		
		// Let the looper send the message again
		if (App.mClementine.isConnected()) {
			Message msg = Message.obtain();
			msg.arg1 = CHECK_FOR_DATA_ARG;
			mHandler.sendMessageDelayed(msg, DELAY_MILLIS);
		}
	}
	
	/**
	 * Process the received protocol buffer
	 * @param bs The binary representation of the protocol buffer
	 */
	private void processProtocolBuffer(ClementineMessage clementineMessage) {
		// Close the connection if we have an old proto verion
		if (clementineMessage.isErrorMessage()) {
			closeConnection(clementineMessage);
			sendUiMessage(clementineMessage);
		} 
		
		if (clementineMessage.getTypeGroup() == MessageGroup.GUI_RELOAD) {
			sendUiMessage(clementineMessage);
			
	    	// Now update the notification and the remote control client			
			if (App.mClementine.getCurrentSong() != mLastSong) {
				mLastSong = App.mClementine.getCurrentSong();
				updateNotification();
				updateRemoteControlClient();
				mPebble.sendMusicUpdateToPebble();
			}
			if (App.mClementine.getState() != mLastState) {
				mLastState = App.mClementine.getState();
				updateRemoteControlClient();
			}
		}
		
		if (clementineMessage.getMessageType() == MsgType.DISCONNECT) {
			closeConnection(clementineMessage);
		}
		sendUiMessage(clementineMessage);
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
	 * @return true if data was sent, false if not
	 */
	@Override
	public boolean sendRequest(ClementineMessage message) {
		// Send the request to Clementine
		boolean ret = super.sendRequest(message);
		
		// If we lost connection, try to reconnect
		if (!ret) {
			ret = super.createConnection(mRequestConnect);
			if (!ret) {
				// Failed. Close connection
				Builder builder = ClementineMessage.getMessageBuilder(MsgType.DISCONNECT);
				ResponseDisconnect.Builder disc = builder.getResponseDisconnectBuilder();
				disc.setReasonDisconnect(ReasonDisconnect.Server_Shutdown);
				builder.setResponseDisconnect(disc);
				closeConnection(new ClementineMessage(builder));
			}
		}
		
		return ret;
	}
	
	/**
	 * Disconnect from Clementine
	 * @param r The RequestDisconnect Object
	 */
	@Override
	public void disconnect(ClementineMessage message) {
		if (App.mClementine.isConnected()) {
			// Set the Connected flag to false, so the loop in
			// checkForData() is interrupted
			App.mClementine.setConnected(false);
			
			super.disconnect(message);
			
			// and close the connection
			closeConnection(message);
		}
	}
	
	/**
	 * Close the socket and the streams
	 */
	private void closeConnection(ClementineMessage clementineMessage) {
		// Disconnect socket
		closeSocket();
		
		// Cancel Notification
		mNotificationManager.cancel(App.NOTIFY_ID);

		unregisterRemoteControlClient();
		
		App.mClementine.setConnected(false);
		
		mWakeLock.release();
		
		sendUiMessage(clementineMessage);
		
		// Close thread
		Looper.myLooper().quit();
		
		// Fire the listener
		fireOnConnectionClosed(clementineMessage);
	}
	
	/**
	 * Fire the event to all listeners
	 * @param r The Disconnect event.
	 */
	private void fireOnConnectionClosed(ClementineMessage clementineMessage) {
		for (OnConnectionClosedListener listener : mListeners ) {
			listener.onConnectionClosed(clementineMessage);
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
	 * @returns Is the connection still active?
	 */
	private void checkKeepAlive() {
		if (mLastKeepAlive > 0 && (System.currentTimeMillis() - mLastKeepAlive) > KEEP_ALIVE_TIMEOUT ) {
			// Check if we shall reconnect
			while (mLeftReconnects > 0) {
				closeSocket();
				if (super.createConnection(mRequestConnect)) {
					mLeftReconnects = MAX_RECONNECTS;
					break;
				}
				
				mLeftReconnects--;
			}
			
			// We tried, but the server isn't there anymore
			if (mLeftReconnects == 0) {
				closeConnection(new ClementineMessage(ErrorMessage.KEEP_ALIVE_TIMEOUT));
			}
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
	 * Update the notification with the new track info
	 */
	private void updateNotification() {
		if (mLastSong != null)  {
			Bitmap scaledArt = Bitmap.createScaledBitmap(mLastSong.getArt(), 
													mNotificationWidth, 
													mNotificationHeight, 
													false);
			mNotifyBuilder.setLargeIcon(scaledArt);
			mNotifyBuilder.setContentTitle(mLastSong.getArtist());
			mNotifyBuilder.setContentText(mLastSong.getTitle() + 
										  " / " + 
										  mLastSong.getAlbum());
		} else {
			mNotifyBuilder.setContentTitle(App.mApp.getString(R.string.app_name));
			mNotifyBuilder.setContentText(App.mApp.getString(R.string.player_nosong));
		}
		mNotificationManager.notify(App.NOTIFY_ID, mNotifyBuilder.build());
	}
	
	/**
	 * Register the RemoteControlClient
	 */
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void registerRemoteControlClient() {
		// Request AudioFocus, so the widget is shown on the lock-screen
		mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener, 
										AudioManager.STREAM_MUSIC, 
										AudioManager.AUDIOFOCUS_GAIN);
		
		mAudioManager.registerMediaButtonEventReceiver(mClementineMediaButtonEventReceiver);
		
		// The rest is only available in API Level 14
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			return;
		
		// Create the intent
		Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
		mediaButtonIntent.setComponent(mClementineMediaButtonEventReceiver);
		PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(App.mApp.getApplicationContext(), 
																	  0, 
																	  mediaButtonIntent, 
																	  0);
		// Create the client
		mRcClient = new RemoteControlClient(mediaPendingIntent);
		if (App.mClementine.getState() == Clementine.State.PLAY) {
			mRcClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
    	} else {
    		mRcClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
    	}
		mRcClient.setTransportControlFlags(	RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
											RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
											RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
											RemoteControlClient.FLAG_KEY_MEDIA_PAUSE);
		mAudioManager.registerRemoteControlClient(mRcClient);
	}
	
	/**
	 * Unregister the RemoteControlClient
	 */
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void unregisterRemoteControlClient() {
		// Disconnect EventReceiver and RemoteControlClient
		mAudioManager.unregisterMediaButtonEventReceiver(mClementineMediaButtonEventReceiver);
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			return;
		
		if (mRcClient != null) {
			mAudioManager.unregisterRemoteControlClient(mRcClient);
			mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
		}
	}
	
	/**
	 * Update the RemoteControlClient
	 */
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void updateRemoteControlClient() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			return;
		
		// Update playstate
		if (App.mClementine.getState() == Clementine.State.PLAY) {
			mRcClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
    	} else {
    		mRcClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
    	}
		
		// Get the metadata editor
		if (mLastSong != null
		 && mLastSong.getArt() != null) {
			RemoteControlClient.MetadataEditor editor = mRcClient.editMetadata(false);
			editor.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, mLastSong.getArt());
			
			// The RemoteControlClients displays the following info:
			// METADATA_KEY_TITLE (white) - METADATA_KEY_ALBUMARTIST (grey) - METADATA_KEY_ALBUM (grey)
			// 
			// So i put the metadata not in the "correct" fields to display artist, track and album
			// TODO: Fix it when changed in newer android versions
			editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, mLastSong.getAlbum());
			editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, mLastSong.getArtist());
			editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, mLastSong.getTitle());
			editor.apply();
		}
	}
	
	/**
	 * Is the thread still alive?
	 * @return A boolean indicating the status
	 */
	public boolean isAlive() {
		return mThread.isAlive();
	}
	
	public void join() throws InterruptedException {
		mThread.join();
	}
	
	private OnAudioFocusChangeListener mOnAudioFocusChangeListener = new OnAudioFocusChangeListener() {
		@Override
		public void onAudioFocusChange(int focusChange) {

		}
	};
}
