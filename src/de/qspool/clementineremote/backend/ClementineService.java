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

import de.qspool.clementineremote.App; 
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.elements.Disconnected;
import de.qspool.clementineremote.backend.elements.Disconnected.DisconnectReason;
import de.qspool.clementineremote.backend.event.OnConnectionClosedListener;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.ui.ConnectDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

public class ClementineService extends Service {

	private NotificationCompat.Builder mNotifyBuilder;
	private NotificationManager mNotificationManager;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			handleServiceAction(intent);
		}

		return START_STICKY;
	}
	
	/**
	 * Handle the requests to the service
	 * @param action The action to perform
	 */
	private void handleServiceAction(Intent intent) {
		mNotificationManager = (NotificationManager) App.mApp.getSystemService(Context.NOTIFICATION_SERVICE);
		
		int action = intent.getIntExtra(App.SERVICE_ID, 0);
		switch (action) {
		case App.SERVICE_START:
			// Create a new instance
			if (App.mClementineConnection == null) {
				App.mClementineConnection = new ClementinePlayerConnection();
	
				setupNotification(true);
				App.mClementineConnection.setNotificationBuilder(mNotifyBuilder);
				App.mClementineConnection.setOnConnectionClosedListener(occl);
				App.mClementineConnection.start();
			}
			break;
		case App.SERVICE_CONNECTED:
			startForeground(App.NOTIFY_ID, mNotifyBuilder.build());
			break;
		case App.SERVICE_DISCONNECTED:
			stopForeground(true);
			try {
				App.mClementineConnection.join();
			} catch (InterruptedException e) {}
			App.mClementineConnection = null;
			
			// Check if we lost connection due a keep alive
			if (intent.hasExtra(App.SERVICE_DISCONNECT_DATA)) {
				int reason = intent.getIntExtra(App.SERVICE_DISCONNECT_DATA, 0);
				if (reason == DisconnectReason.KEEP_ALIVE.ordinal()) {
					setupNotification(false);
					showKeepAliveDisconnectNotification();
				}
			}
			break;		
		default: break;
		}
	}
	
	@Override
	public void onDestroy() {
		stopForeground(true);
		if (App.mClementine.isConnected()) {
			// Create a new request
			RequestDisconnect r = new RequestDisconnect();
			
			// Move the request to the message
			Message msg = Message.obtain();
			msg.obj = r;
			
			// Send the request to the thread
			App.mClementineConnection.mHandler.sendMessage(msg);
		}
		try {
			App.mClementineConnection.join();
		} catch (InterruptedException e) {}
		App.mClementineConnection = null;
	}
	
	/**
	 * Setup the Notification
	 */
	private void setupNotification(boolean ongoing) {
	    mNotifyBuilder = new NotificationCompat.Builder(App.mApp);
	    mNotifyBuilder.setSmallIcon(R.drawable.ic_launcher);
	    mNotifyBuilder.setOngoing(ongoing);
	    
	    // If we don't have an ongoing notification, it shall be closed after clicked.
	    if (!ongoing) {
	    	mNotifyBuilder.setAutoCancel(true);
	    }
	    
	    // Set the result intent
	    Intent resultIntent = new Intent(App.mApp, ConnectDialog.class);
	    resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    // Create a TaskStack, so the app navigates correctly backwards
	    TaskStackBuilder stackBuilder = TaskStackBuilder.create(App.mApp);
	    stackBuilder.addParentStack(ConnectDialog.class);
	    stackBuilder.addNextIntent(resultIntent);
	    PendingIntent resultPendingintent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
	    mNotifyBuilder.setContentIntent(resultPendingintent);
	}
	
	/**
	 * Create a notification that shows, that we got a keep alive timeout
	 */
	private void showKeepAliveDisconnectNotification() {
		mNotifyBuilder.setContentTitle(App.mApp.getString(R.string.app_name));
		mNotifyBuilder.setContentText(App.mApp.getString(R.string.notification_disconnect_keep_alive));
		mNotificationManager.notify(App.NOTIFY_ID, mNotifyBuilder.build());
	}
	
	private OnConnectionClosedListener occl = new OnConnectionClosedListener() {
		
		@Override
		public void onConnectionClosed(Disconnected disconnected) {
			Intent mServiceIntent = new Intent(ClementineService.this, ClementineService.class);
	    	mServiceIntent.putExtra(App.SERVICE_ID, App.SERVICE_DISCONNECTED);
	    	mServiceIntent.putExtra(App.SERVICE_DISCONNECT_DATA, disconnected.getReason().ordinal());
	    	startService(mServiceIntent);
		}
	};
}
