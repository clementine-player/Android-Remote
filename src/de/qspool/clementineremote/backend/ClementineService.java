package de.qspool.clementineremote.backend;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;

public class ClementineService extends Service {
	ClementineConnection mClementineConnection = null;
	
	@Override
	public void onCreate() {
		mClementineConnection = new ClementineConnection();
		App.mClementineConnection = mClementineConnection;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (!mClementineConnection.isAlive())
			App.mClementineConnection.start();
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
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
			mClementineConnection.join(1000);
		} catch (InterruptedException e) {}
		
		mClementineConnection = null;
		App.mClementineConnection = null;
	}
}
