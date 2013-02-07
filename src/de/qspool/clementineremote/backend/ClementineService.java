package de.qspool.clementineremote.backend;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;

public class ClementineService extends Service {

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (!App.mClementineConnection.isAlive())
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
			App.mClementineConnection.join(1000);
		} catch (InterruptedException e) {}
		
		App.mClementineConnection = null;
	}
}
