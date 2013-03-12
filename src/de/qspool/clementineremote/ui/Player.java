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

import java.util.LinkedList;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.requests.RequestControl;
import de.qspool.clementineremote.backend.requests.RequestControl.Request;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.backend.requests.RequestPlaylistSong;
import de.qspool.clementineremote.backend.requests.RequestVolume;
import de.qspool.clementineremote.utils.Utilities;


public class Player extends SherlockActivity {
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
	private PlayerHandler mHandler;
	
	private ActionBar mActionBar;
	
	private int mDownloadPlaylists;
	private LinkedList<String> mDownloadPlaylistNames;
	private ProgressDialog mProgressDialog;
	
	private Toast mToast;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
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
	    
	    // Get the shared preferences
	    mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
	    
	    // Get the actionbar
	    mActionBar = getSupportActionBar();
	    mActionBar.setTitle(R.string.player_playlist);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		// Check if we are still connected
		if (App.mClementineConnection == null 
		 || !App.mClementineConnection.isAlive()) {
			setResult(ConnectDialog.RESULT_DISCONNECT);
			finish();
		} else {
		    // Set the handler
		    mHandler = new PlayerHandler(this);
		    App.mClementineConnection.setUiHandler(mHandler);
		    
			// Reload infos
			reloadInfo();
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		mHandler = null;
		if (App.mClementineConnection != null) {
			App.mClementineConnection.setUiHandler(mHandler);
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inf = getSupportMenuInflater();
		inf.inflate(R.menu.player_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Message msg = Message.obtain();
		switch (item.getItemId())
		{
		case R.id.disconnect: 	requestDisconnect();
								break;
		case R.id.shuffle:		App.mClementine.nextShuffleMode();
								msg.obj = new RequestControl(Request.SHUFFLE);
								App.mClementineConnection.mHandler.sendMessage(msg);
								showShuffleToast();
								break;
		case R.id.repeat:		App.mClementine.nextRepeatMode();
								msg.obj = new RequestControl(Request.REPEAT);
								App.mClementineConnection.mHandler.sendMessage(msg);
								showRepeatToast();
								break;
		case R.id.settings:		Intent settingsIntent = new Intent(this, ClementineRemoteSettings.class);
								startActivity(settingsIntent);
								break;
		case R.id.playlist:		openPlaylistView();
								break;
		default: break;
		}
		return true;
	}
	
	/**
	 * Show the toast for the shuffle mode
	 */
	private void showShuffleToast() {
		switch (App.mClementine.getShuffleMode()) {
		case OFF: 		makeToast(R.string.shuffle_off, Toast.LENGTH_SHORT);
						break;
		case ALL:		makeToast(R.string.shuffle_all, Toast.LENGTH_SHORT);
						break;
		case INSIDE_ALBUM:	makeToast(R.string.shuffle_inside_album, Toast.LENGTH_SHORT);
							break;
		case ALBUMS:	makeToast(R.string.shuffle_albums, Toast.LENGTH_SHORT);
						break;
		}
	}
	
	/**
	 * Show the toast for the repeat mode
	 */
	public void showRepeatToast() {
		switch (App.mClementine.getRepeatMode()) {
		case OFF: 		makeToast(R.string.repeat_off, Toast.LENGTH_SHORT);
						break;
		case TRACK:		makeToast(R.string.repeat_track, Toast.LENGTH_SHORT);
						break;
		case ALBUM:		makeToast(R.string.repeat_album, Toast.LENGTH_SHORT);
						break;
		case PLAYLIST:	makeToast(R.string.repeat_playlist, Toast.LENGTH_SHORT);
						break;
		}
	}
	
	/**
	 * Check if we have all Playlists, otherwise get them
	 */
	private void openPlaylistView() {
		mDownloadPlaylists = 0;
		mDownloadPlaylistNames = new LinkedList<String>();
		for (int i=0;i<App.mClementine.getPlaylists().size();i++) {
			// Get the Playlsit
			int key = App.mClementine.getPlaylists().keyAt(i);
			MyPlaylist playlist = App.mClementine.getPlaylists().get(key);
			if (playlist.getPlaylistSongs().size() == 0) {
				Message msg = Message.obtain();
				msg.obj = new RequestPlaylistSong(playlist.getId());
				App.mClementineConnection.mHandler.sendMessage(msg);
				mDownloadPlaylists++;
				mDownloadPlaylistNames.add(playlist.getName());
			}
		}
		
		// Open it directly only when we got all playlists
		if (mDownloadPlaylists == 0) {
			Intent playlistIntent = new Intent(this, Playlists.class);
			startActivity(playlistIntent);
		} else {
			// Start a Progressbar
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setMax(mDownloadPlaylists);
			mProgressDialog.setCancelable(true);
			mProgressDialog.setTitle(R.string.player_download_playlists);
			mProgressDialog.setMessage(mDownloadPlaylistNames.poll());
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.show();
		}
	}
	
	/**
	 * Update the Progressbar and open the intent if necessary
	 */
	void checkGotAllPlaylists() {
		if (mProgressDialog != null) {
			mProgressDialog.setProgress(mProgressDialog.getProgress()+1);
			mProgressDialog.setMessage(mDownloadPlaylistNames.poll());
			mDownloadPlaylists--;
			
			if (mDownloadPlaylists == 0 && mProgressDialog.isShowing()) {
				mProgressDialog.dismiss();
				Intent playlistIntent = new Intent(this, Playlists.class);
				startActivity(playlistIntent);
			}
		}
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
					makeToast(getString(R.string.playler_volume) + " " + App.mClementine.getVolume(), Toast.LENGTH_SHORT);
					return true;
				}
				break;
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
					Message msgUp = Message.obtain();
					msgUp.obj = new RequestVolume(App.mClementine.getVolume() + 10);
					App.mClementineConnection.mHandler.sendMessage(msgUp);
					makeToast(getString(R.string.playler_volume) + " " + App.mClementine.getVolume(), Toast.LENGTH_SHORT);
					return true;
				}
				break;
			default: break;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	/**
	 * Disconnect was finished, now finish this activity
	 */
	void disconnect() {
		makeToast(R.string.player_disconnected, Toast.LENGTH_SHORT);
		setResult(ConnectDialog.RESULT_DISCONNECT);
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
    	MySong currentSong = App.mClementine.getCurrentSong();
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
    	
    	// ActionBar shows the current playlist
    	if (App.mClementine.getActivePlaylist() != null) {
    		mActionBar.setSubtitle(App.mClementine.getActivePlaylist().getName());
    	}
    }
    
    private String buildTrackPosition() {
    	StringBuilder sb = new StringBuilder();
    	sb.append(Utilities.PrettyTime(App.mClementine.getSongPosition()));
    	sb.append("/");
    	sb.append(Utilities.PrettyTime(App.mClementine.getCurrentSong().getLength()));
    	
    	return sb.toString();
    }
    
    /**
     * Show text in a toast. Cancels previous toast
     * @param resId The resource id
     * @param length length
     */
    private void makeToast(int resId, int length) {
    	makeToast(getString(resId), length);
    }
    
    /**
     * Show text in a toast. Cancels previous toast
     * @param tetx The text to show
     * @param length length
     */
    private void makeToast(String text, int length) {
    	if (mToast != null) {
    		mToast.cancel();
    	}
    	mToast = Toast.makeText(this, text, length);
    	mToast.show();
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
