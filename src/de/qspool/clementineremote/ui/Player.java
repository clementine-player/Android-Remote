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
import de.qspool.clementineremote.backend.player.Song;
import de.qspool.clementineremote.backend.requests.RequestControl;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.backend.requests.RequestVolume;
import de.qspool.clementineremote.backend.requests.RequestControl.Request;
import de.qspool.clementineremote.utils.Utilities;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class Player extends Activity {
	private TextView mTvArtist;
	private TextView mTvTitle;
	private TextView mTvAlbum;
	
	private TextView mTvGenre;
	private TextView mTvYear;
	private TextView mTvLength;
	
	private ImageButton mBtnNext;
	private ImageButton mBtnPrev;
	private ImageButton mBtnPlayPause;
	
	private ImageView mImgArt;
	
	private SharedPreferences mSharedPref;
	private PlayerHandler mHandler = new PlayerHandler(this);
	
	private NotificationCompat.Builder mNotifyBuilder;
	private NotificationManager mNotificationManager;
	private int mNotifyId = 1;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    // Remove title bar and set the view
	    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
	    	this.requestWindowFeature(Window.FEATURE_NO_TITLE);
	    setContentView(R.layout.player);
	    
	    // Get the Views
	    mTvArtist = (TextView) findViewById(R.id.tvArtist);
	    mTvTitle  = (TextView) findViewById(R.id.tvTitle);
	    mTvAlbum  = (TextView) findViewById(R.id.tvAlbum);
	    
	    mTvGenre  = (TextView) findViewById(R.id.tvGenre);
	    mTvYear   = (TextView) findViewById(R.id.tvYear);
	    mTvLength = (TextView) findViewById(R.id.tvLength);
	    
	    mBtnNext  = (ImageButton) findViewById(R.id.btnNext);
	    mBtnPrev  = (ImageButton) findViewById(R.id.btnPrev);
	    mBtnPlayPause  = (ImageButton) findViewById(R.id.btnPlaypause);
	    
	    mImgArt = (ImageView) findViewById(R.id.imgArt);

	    // Set the onclicklistener for the buttons
	    mBtnNext.setOnClickListener(oclControl);
	    mBtnPrev.setOnClickListener(oclControl);
	    mBtnPlayPause.setOnClickListener(oclControl);
	    
	    // Set the handler
	    App.mClementineConnection.setUiHandler(mHandler);
	    
	    // Get the shared preferences
	    mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
	    
	    // Get the notification manager
	    setupNotification();
	    
	    // Reload the player ui
	    reloadInfo();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		// after resume, the connection shall not be dropped immediatly
		App.mClementineConnection.setLastKeepAlive(System.currentTimeMillis());
		
		// Reload infos
		reloadInfo();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inf = getMenuInflater();
		inf.inflate(R.menu.player_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId())
		{
		case R.id.disconnect: 	requestDisconnect();
								break;
		case R.id.shuffle:		Message msg = Message.obtain();
								msg.obj = new RequestControl(Request.SHUFFLE);
								App.mClementineConnection.mHandler.sendMessage(msg);
								break;
		case R.id.settings:		Intent settingsIntent = new Intent(this, ClementineRemoteSettings.class);
								startActivity(settingsIntent);
								break;
		default: break;
		}
		return true;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			// Control the volume of clementine if enabled in the options
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
					Message msgDown = Message.obtain();
					msgDown.obj = new RequestVolume(App.mClementine.getVolume() - 10);
					App.mClementineConnection.mHandler.sendMessage(msgDown);
					return true;
				}
				break;
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
					Message msgUp = Message.obtain();
					msgUp.obj = new RequestVolume(App.mClementine.getVolume() + 10);
					App.mClementineConnection.mHandler.sendMessage(msgUp);
					return true;
				}
				break;
			default: break;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	private void setupNotification() {
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	    mNotifyBuilder = new NotificationCompat.Builder(this);
	    mNotifyBuilder.setSmallIcon(R.drawable.ic_launcher);
	    mNotifyBuilder.setOngoing(true);
	    
	    // Set the result intent
	    Intent resultIntent = new Intent(this, Player.class);
	    resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    // Create a TaskStack, so the app navigates correctly backwards
	    TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
	    stackBuilder.addParentStack(Player.class);
	    stackBuilder.addNextIntent(resultIntent);
	    PendingIntent resultPendingintent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
	    mNotifyBuilder.setContentIntent(resultPendingintent);
	}
	
	/**
	 * Disconnect was finished, now finish this activity
	 */
	void disconnect() {
		mNotificationManager.cancel(mNotifyId);
		Toast.makeText(this, R.string.player_disconnected, Toast.LENGTH_SHORT).show();
		setResult(ClementineRemoteControlActivity.RESULT_DISCONNECT);
		finish();
	}
	
	/**
	 * Request a disconnect from clementine
	 */
	void requestDisconnect() {
		// Create a new request
		RequestDisconnect r = new RequestDisconnect();
		
		// Move the request to the message
		Message msg = Message.obtain();
		msg.obj = r;
		
		// Send the request to the thread
		App.mClementineConnection.mHandler.sendMessage(msg);
	}
    
	/**
	 * Reload the player ui
	 */
    void reloadInfo() {
    	// display play / pause image
    	if (App.mClementine.getState() == Clementine.State.PLAY) {
    		mBtnPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_media_pause));
    	} else {
    		mBtnPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_media_play));
    	}
    	
    	// Get the currently played song
    	Song currentSong = App.mClementine.getCurrentSong();
    	if (currentSong == null) {
    		// If none is played right now, show a text and the clementine icon
    		mTvArtist.setText(getString(R.string.player_nosong));
	    	mTvTitle. setText("");
	    	mTvAlbum. setText("");
	    	
	    	mTvGenre. setText("");
	    	mTvYear.  setText("");
	    	mTvLength.setText("");
	    	
    		mImgArt.setImageResource(R.drawable.icon_large);
    	} else {
	    	mTvArtist.setText(currentSong.getArtist());
	    	mTvTitle. setText(currentSong.getTitle());
	    	mTvAlbum. setText(currentSong.getAlbum());
	    	
	    	mTvGenre. setText(currentSong.getGenre());
	    	mTvYear.  setText(currentSong.getYear());
	    	mTvLength.setText(buildTrackPosition());
	    	
	    	// Check if a coverart is valid
	    	if (currentSong.getArt() == null) {
	    		mImgArt.setImageResource(R.drawable.icon_large);
	    	} else {
	    		mImgArt.setImageBitmap(currentSong.getArt());
	    	}
    	}
    	
    	// Now update the notification
    	mNotifyBuilder.setLargeIcon(mImgArt.getDrawingCache());
    	mNotifyBuilder.setContentTitle(mTvArtist.getText().toString());
    	mNotifyBuilder.setContentText(mTvTitle.getText().toString() + 
    								  " / " + 
    								  mTvAlbum.getText().toString());
    	mNotificationManager.notify(mNotifyId, mNotifyBuilder.build());
    	
    }
    
    private String buildTrackPosition() {
    	StringBuilder sb = new StringBuilder();
    	sb.append(Utilities.PrettyTime(App.mClementine.getSongPosition()));
    	sb.append("/");
    	sb.append(Utilities.PrettyTime(App.mClementine.getCurrentSong().getLength()));
    	
    	return sb.toString();
    }
	
	private OnClickListener oclControl = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Message msg = Message.obtain();
			
			switch(v.getId()) {
			case R.id.btnNext: msg.obj = new RequestControl(Request.NEXT);
							   break;
			case R.id.btnPrev: msg.obj = new RequestControl(Request.PREV);
							   break;
			case R.id.btnPlaypause: msg.obj = new RequestControl(Request.PLAYPAUSE);
								break;
		    default: break;
			}
			// Send the request to the thread
			App.mClementineConnection.mHandler.sendMessage(msg);
		}
	};
}
