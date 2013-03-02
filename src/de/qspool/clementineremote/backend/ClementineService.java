package de.qspool.clementineremote.backend;

import de.qspool.clementineremote.App; 
import de.qspool.clementineremote.ClementineRemoteControlActivity;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

public class ClementineService extends Service {

	private NotificationCompat.Builder mNotifyBuilder;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		int action = intent.getIntExtra(App.SERVICE_ID, 0);
		switch (action) {
		case App.SERVICE_START:
			// Create a new instance
			App.mClementineConnection = new ClementineConnection(this);
			setupNotification();
			App.mClementineConnection.setNotificationBuilder(mNotifyBuilder);
			App.mClementineConnection.start();
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
			break;
		
		default: break;
		}
		return START_STICKY;
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
	private void setupNotification() {
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
