package de.qspool.clementineremote.backend;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

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
		App.mClementineConnection.start();
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		if (App.mClementine.isConnected()) {
			mClementineConnection.disconnect(new RequestDisconnect());
		}
	}
}
