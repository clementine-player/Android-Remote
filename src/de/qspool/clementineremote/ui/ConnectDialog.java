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

package de.qspool.clementineremote.ui;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.requests.RequestConnect;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.View.OnClickListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

/**
 * The connect dialog
 */
public class ConnectDialog extends Activity {
	private Button mBtnConnect;
	private EditText mEtIp;
	private CheckBox mCbAutoConnect;
	ProgressDialog mPdConnect;
	private SharedPreferences mSharedPref;
    private ConnectDialogHandler mHandler = new ConnectDialogHandler(this);
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    // Remove title bar
	    this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	
	    setContentView(R.layout.connectdialog);
	    
	    // Get the parameters
	    Bundle extras = getIntent().getExtras();
	    
	    // Get the Layoutelements
	    mBtnConnect = (Button) findViewById(R.id.btnConnect);
	    mBtnConnect.setOnClickListener(oclConnect);
	    mBtnConnect.requestFocus();
	    
	    // Ip and Autoconnect
	    mEtIp = (EditText) findViewById(R.id.etIp);
	    mCbAutoConnect = (CheckBox) findViewById(R.id.cbAutoconnect);
	    
	    // Set the handler
	    App.mClementineConnection.setUiHandler(mHandler);
	    
	    // Create a progress dialog
	    mPdConnect = new ProgressDialog(this);
	    mPdConnect.setCancelable(true);
	    mPdConnect.setOnCancelListener(oclProgressDialog);
	    mPdConnect.setMessage(getString(R.string.connectdialog_connecting));
	    
	    // Get old ip and auto-connect from shared prefences
	    mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
	    mEtIp.setText(mSharedPref.getString(App.SP_KEY_IP, ""));
	    mEtIp.setSelection(mEtIp.length());
	    mCbAutoConnect.setChecked(mSharedPref.getBoolean(App.SP_KEY_AC, false));
	    
	    // Check if Autoconnect is enabled
	    if (mCbAutoConnect.isChecked() && extras.getBoolean(App.SP_KEY_AC)) {
	    	connect();
	    }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inf = getMenuInflater();
		inf.inflate(R.menu.connectdialog_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId())
		{
		case R.id.settings:		Intent settingsIntent = new Intent(this, ClementineRemoteSettings.class);
								startActivity(settingsIntent);
								break;
		default: break;
		}
		return true;
	}

	private OnClickListener oclConnect = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// Save the data
			SharedPreferences.Editor editor = mSharedPref.edit();
			editor.putBoolean(App.SP_KEY_AC, mCbAutoConnect.isChecked());
			editor.putString(App.SP_KEY_IP, mEtIp.getText().toString());
			editor.commit();
			
			// And connect
			connect();
		}
	};
	
	private OnCancelListener oclProgressDialog = new OnCancelListener() {
		@Override
		public void onCancel(DialogInterface dialog) {
			// Create a new request
			RequestDisconnect r = new RequestDisconnect();
			
			// Move the request to the message
			Message msg = Message.obtain();
			msg.obj = r;
			
			// Send the request to the thread
			App.mClementineConnection.mHandler.sendMessage(msg);
		}
		
	};
	
	/**
	 * Connect to clementine
	 */
	private void connect() {
		mPdConnect.show();
		// Get the port to connect to			
		int port = Integer.valueOf(mSharedPref.getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort)));
					
		// Create a new connect request
		RequestConnect r = new RequestConnect(mEtIp.getText().toString(), port);
		
		// Move the request to the message
		Message msg = Message.obtain();
		msg.obj = r;
		
		// Send the request to the thread
		App.mClementineConnection.mHandler.sendMessage(msg);
	}
}
