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
import de.qspool.clementineremote.ClementineRemoteControlActivity;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.elements.Disconnected;
import de.qspool.clementineremote.backend.elements.Disconnected.DisconnectReason;
import de.qspool.clementineremote.backend.requests.RequestConnect;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.View.OnClickListener;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The connect dialog
 */
public class ConnectDialog extends Activity {
	private Button mBtnConnect;
	private ImageButton mBtnSettings;
	private EditText mEtIp;
	private CheckBox mCbAutoConnect;
	ProgressDialog mPdConnect;
	private SharedPreferences mSharedPref;
    private ConnectDialogHandler mHandler = new ConnectDialogHandler(this);
    private int mAuthCode = 0;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    // Remove title bar
	    this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	
	    setContentView(R.layout.connectdialog);
	    
	    // Get the Layoutelements
	    mBtnConnect = (Button) findViewById(R.id.btnConnect);
	    mBtnConnect.setOnClickListener(oclConnect);
	    mBtnConnect.requestFocus();
	    
	    mBtnSettings = (ImageButton) findViewById(R.id.btnSettings);
	    mBtnSettings.setOnClickListener(oclSettings);
	    
	    // Ip and Autoconnect
	    mEtIp = (EditText) findViewById(R.id.etIp);
	    mEtIp.setRawInputType(InputType.TYPE_CLASS_NUMBER);
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
	    
	    // Get the last auth code
	    mAuthCode = mSharedPref.getInt(App.SP_LAST_AUTH_CODE, 0);
	}
	
	@Override 
	public void onResume() {
		super.onResume();
		
	    // Get the parameters
	    Bundle extras = getIntent().getExtras();
	    
	    // Check if Autoconnect is enabled
	    if (mCbAutoConnect.isChecked() && extras.getBoolean(App.SP_KEY_AC)) {
	    	connect();
	    }
	    extras.putBoolean(App.SP_KEY_AC, true);
	}

	private OnClickListener oclConnect = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// And connect
			connect();
		}
	};
	
	private OnClickListener oclSettings = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Intent settingsIntent = new Intent(ConnectDialog.this, ClementineRemoteSettings.class);
			startActivity(settingsIntent);
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
		// Save the data
		SharedPreferences.Editor editor = mSharedPref.edit();
		editor.putBoolean(App.SP_KEY_AC, mCbAutoConnect.isChecked());
		editor.putString(App.SP_KEY_IP, mEtIp.getText().toString());
		editor.putInt(App.SP_LAST_AUTH_CODE, mAuthCode);
		editor.commit();
		
		mPdConnect.show();
		// Get the port to connect to			
		int port = Integer.valueOf(mSharedPref.getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort)));
					
		// Create a new connect request
		RequestConnect r = new RequestConnect(mEtIp.getText().toString(), port, mAuthCode);
		
		// Move the request to the message
		Message msg = Message.obtain();
		msg.obj = r;
		
		// Send the request to the thread
		App.mClementineConnection.mHandler.sendMessage(msg);
	}
	
	/**
	 * Show the user the dialog to enter the auth code
	 */
	void showAuthCodePromt() {
		final Dialog authCodeDialog = new Dialog(this, R.style.Dialog_Transparent);
		authCodeDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		authCodeDialog.setContentView(R.layout.dialog_auth_code);
		
		// Set the Views
		Button connectButton = (Button) authCodeDialog.findViewById(R.id.btnConnectAuth);
		final EditText etAuthCode = (EditText) authCodeDialog.findViewById(R.id.etAuthCode);
		connectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					mAuthCode = Integer.parseInt(etAuthCode.getText().toString());
					authCodeDialog.cancel();
	        	    connect();
				} catch (NumberFormatException e) {
					Toast.makeText(ConnectDialog.this, R.string.invalid_code, Toast.LENGTH_SHORT).show();
				}
			}
	    });
		// Show the keyboard directly
		authCodeDialog.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		authCodeDialog.show();
	}
	
	/**
	 * We connected to clementine successfully. Now open other view
	 */
	void connected() {
		Toast.makeText(this, R.string.connectdialog_connected, Toast.LENGTH_SHORT).show();
		setResult(ClementineRemoteControlActivity.RESULT_CONNECT);
		finish();
	}
	
	/**
	 * We couldn't connect to clementine. Inform the user
	 */
	void noConnection() {
		Toast.makeText(this, R.string.connectdialog_error, Toast.LENGTH_SHORT).show();
	}
	
	/**
	 * We have an old Proto version. User has to update Clementine
	 */
	void oldProtoVersion() {
		final Dialog errorDialog = new Dialog(this, R.style.Dialog_Transparent);
		errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		errorDialog.setContentView(R.layout.dialog_message);
		
		// Set the ViewsButton connectButton = (Button) authCodeDialog.findViewById(R.id.btnConnectAuth);
		final TextView title = (TextView) errorDialog.findViewById(R.id.tvTitle);
		final TextView message = (TextView) errorDialog.findViewById(R.id.tvMessage);
		title.setText(R.string.error_versions);
		message.setText(R.string.old_proto);
		
		Button connectButton = (Button) errorDialog.findViewById(R.id.btnClose);
				connectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				errorDialog.cancel();
			}
	    });
		
		errorDialog.show();
	}
	
	/**
	 * Clementine closed the connection
	 * @param disconnected The object to work with
	 */
	void disconnected(Disconnected disconnected) {
		if (disconnected.getReason() == DisconnectReason.WRONG_AUTH_CODE) {
			showAuthCodePromt();
		}
	}
}
