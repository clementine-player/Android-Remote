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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.requests.RequestControl;
import de.qspool.clementineremote.backend.requests.RequestControl.Request;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.backend.requests.RequestVolume;
import de.qspool.clementineremote.ui.fragments.PlayerFragment;
import de.qspool.clementineremote.ui.fragments.PlaylistSongs;

public class Player extends SherlockFragmentActivity {

	private SharedPreferences mSharedPref;
	private PlayerHandler mHandler;
	
	private ActionBar mActionBar;
	
	private Toast mToast;
	
	PlayerFragment mPlayerFragment;
	PlaylistSongs mPlaylistSongs;
	View mPlaylistFragmentView;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    setContentView(R.layout.player);
	    
		mPlayerFragment = (PlayerFragment) getSupportFragmentManager().findFragmentById(R.id.playerFragment);
		mPlaylistFragmentView = (View) findViewById(R.id.playlistSongsFragment);
		
		if (mPlaylistFragmentView != null) {
			createPlaylistFragment();
		}
		
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
		 || App.mClementine           == null
		 || !App.mClementineConnection.isAlive()
		 || !App.mClementine.isConnected()) {
			setResult(ConnectDialog.RESULT_DISCONNECT);
			finish();
		} else {
		    // Set the handler
		    mHandler = new PlayerHandler(this);
		    App.mClementineConnection.setUiHandler(mHandler);
		    
			// Reload infos
			reloadInfo();
			reloadPlaylist();
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
		case R.id.playlist:		Intent playlistIntent = new Intent(this, Playlists.class);
								startActivity(playlistIntent);
								break;
		default: break;
		}
		return true;
	}
	
	/**
	 * Creates the playlistsongs fragment and adds it to the activity
	 */
	private void createPlaylistFragment() {
		mPlaylistSongs = new PlaylistSongs();
		mPlaylistSongs.setUpdateTrackPositionOnNewTrack(true, 1);
		mPlaylistSongs.setId(App.mClementine.getActivePlaylist().getId());
		
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.replace(R.id.playlistSongsFragment, mPlaylistSongs);
		fragmentTransaction.commit();
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
					makeToast(getString(R.string.playler_volume) + " " + (App.mClementine.getVolume() - 10), Toast.LENGTH_SHORT);
					return true;
				}
				break;
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
					Message msgUp = Message.obtain();
					msgUp.obj = new RequestVolume(App.mClementine.getVolume() + 10);
					App.mClementineConnection.mHandler.sendMessage(msgUp);
					makeToast(getString(R.string.playler_volume) + " " + (App.mClementine.getVolume() + 10), Toast.LENGTH_SHORT);
					return true;
				}
				break;
			default: break;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				return true;
			}
		}
		return super.onKeyUp(keyCode, keyEvent);
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
		// Update the Player Fragment
		if (mPlayerFragment != null && mPlayerFragment.isInLayout()) {
			mPlayerFragment.reloadInfo();
		}
		
    	// ActionBar shows the current playlist
    	if (App.mClementine.getActivePlaylist() != null) {
    		mActionBar.setSubtitle(App.mClementine.getActivePlaylist().getName());
    	}
    }
	
	/**
	 * Reload the playlist songs fragment
	 */
	void reloadPlaylist() {
		// Update the playlist songs fragment
		if (mPlaylistSongs != null) {
			if (App.mClementine.getActivePlaylist().getId() != mPlaylistSongs.getPlaylistId()) {
				createPlaylistFragment();
			} else {
				mPlaylistSongs.updateSongList();
			}
		}
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
}
