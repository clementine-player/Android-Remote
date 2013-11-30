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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.ui.adapter.SeparatedListAdapter;
import de.qspool.clementineremote.ui.fragments.AbstractDrawerFragment;
import de.qspool.clementineremote.ui.fragments.DonateFragment;
import de.qspool.clementineremote.ui.fragments.DownloadsFragment;
import de.qspool.clementineremote.ui.fragments.LibraryFragment;
import de.qspool.clementineremote.ui.fragments.PlayerFragment;
import de.qspool.clementineremote.ui.fragments.PlaylistFragment;
import de.qspool.clementineremote.ui.fragments.SongInfoFragment;

public class MainActivity extends SherlockFragmentActivity {
	private final static String TAG = "MainActivity";
	private final static String MENU_POSITION = "last_menu_position";
	
	private SharedPreferences mSharedPref;
	private PlayerHandler mHandler;
	
	private Toast mToast;
	
	private int mCurrentFragment;
	private LinkedList<AbstractDrawerFragment> mFragments = new LinkedList<AbstractDrawerFragment>();
	
    private ListView mDrawerList;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private int mLastPosition = 1;
    
    private boolean mOpenConnectDialog = true;
    private boolean mInstanceSaved = false;
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    Log.d(TAG, "onCreate");
	    
	    setContentView(R.layout.main_activity);
	    
	    /* 
	     * Define here the available fragments in the mail layout
	     */
        mFragments.add(new PlayerFragment());
        mFragments.add(new SongInfoFragment());
        mFragments.add(new PlaylistFragment());
        mFragments.add(new LibraryFragment());
        mFragments.add(new DownloadsFragment());
        mFragments.add(new DonateFragment());
	    
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        // Create the adapters for the sections
        ArrayAdapter<String> remoteAdapter = new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, getResources().getStringArray(R.array.navigation_array_remote));
        ArrayAdapter<String> settingsAdapter = new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, getResources().getStringArray(R.array.navigation_array_settings));
        ArrayAdapter<String> disconnectAdapter = new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, getResources().getStringArray(R.array.navigation_array_disconnect));
        
        // Create the header adapter
        SeparatedListAdapter separatedListAdapter = new SeparatedListAdapter(this);
        String[] headers = getResources().getStringArray(R.array.navigation_headers);
        
        separatedListAdapter.addSection(headers[0], remoteAdapter);
        separatedListAdapter.addSection(headers[1], settingsAdapter);
        separatedListAdapter.addSection(headers[2], disconnectAdapter);
        
        mDrawerList.setAdapter(separatedListAdapter);
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
		
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.connectdialog_connect,  /* "open drawer" description */
                R.string.dialog_close  /* "close drawer" description */
                ) {
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        
		// When we have a download notifitication and it was clicked, show the download.
		if (getIntent().hasExtra(App.NOTIFICATION_ID)) {
			int id = getIntent().getIntExtra(App.NOTIFICATION_ID, 0);
			if (id == -1) {
				mLastPosition = 1;
			} else {
				mLastPosition = 5;
			}
		}
	}
	
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
        
        if (savedInstanceState != null && savedInstanceState.containsKey(MENU_POSITION)) {
        	mLastPosition = savedInstanceState.getInt(MENU_POSITION);
        }
        
        Log.d(TAG, "onPostCreate");
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	outState.putInt(MENU_POSITION, mLastPosition);
    	mInstanceSaved = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
        
        selectItem(mLastPosition, 0);
    }
	
	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");
		
		mOpenConnectDialog = true;
		mInstanceSaved = false;
		
		// Check if we are still connected
		if (App.mClementineConnection == null
		 || App.mClementine           == null
		 || !App.mClementineConnection.isConnected()) {
			Log.d(TAG, "onResume - disconnect");
			setResult(ConnectDialog.RESULT_DISCONNECT);
			finish();
		} else {
			Log.d(TAG, "onResume - start");
		    // Set the handler
		    mHandler = new PlayerHandler(this);
		    App.mClementineConnection.setUiHandler(mHandler);
		    selectItem(mLastPosition, 0);
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		Log.d(TAG, "onPause");
		
		mHandler = null;
		if (App.mClementineConnection != null) {
			App.mClementineConnection.setUiHandler(mHandler);
		}
	}
	
	@Override 
	public void onDestroy() {
		super.onDestroy();
		
		Log.d(TAG, "onDestroy");
		
		// If we disconnected, open connectdialog
		if (App.mClementineConnection == null
		 || App.mClementine           == null
		 || !App.mClementineConnection.isConnected()) {
			Log.d(TAG, "onDestroy - disconnect");
			if (mOpenConnectDialog) {
				Intent connectDialog = new Intent(this, ConnectDialog.class);
				connectDialog.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				startActivity(connectDialog);
			}
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		mFragments.get(mCurrentFragment).onActivityResult(requestCode, resultCode, data);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			int currentVolume = App.mClementine.getVolume();
			// Control the volume of clementine if enabled in the options
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
					Message msgDown = Message.obtain();
					msgDown.obj = ClementineMessageFactory.buildVolumeMessage(App.mClementine.getVolume() - 10);
					App.mClementineConnection.mHandler.sendMessage(msgDown);
					if (currentVolume >= 10)
						currentVolume -= 10;
					else
						currentVolume = 0;
					makeToast(getString(R.string.playler_volume) + " " + currentVolume + "%", Toast.LENGTH_SHORT);
					return true;
				}
				break;
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
					Message msgUp = Message.obtain();
					msgUp.obj = ClementineMessageFactory.buildVolumeMessage(App.mClementine.getVolume() + 10);
					App.mClementineConnection.mHandler.sendMessage(msgUp);
					if (currentVolume > 90)
						currentVolume = 100;
					else
						currentVolume += 10;
					makeToast(getString(R.string.playler_volume) + " " + currentVolume + "%", Toast.LENGTH_SHORT);
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
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		if (item.getItemId() == android.R.id.home) {
			if (mDrawerLayout.isDrawerOpen(mDrawerList)) {
				mDrawerLayout.closeDrawer(mDrawerList);
			} else {
				mDrawerLayout.openDrawer(mDrawerList);
			}
		}

        return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onBackPressed() {
		// Let the fragment handle the back button first
		if (mFragments.get(mCurrentFragment) == null || 
			!mFragments.get(mCurrentFragment).isVisible() ||
			!mFragments.get(mCurrentFragment).onBackPressed()) {
			super.onBackPressed();
		}
	}
	
	/**
     * Request a disconnect from clementine
     */
    private void requestDisconnect() {
        // Move the request to the message
        Message msg = Message.obtain();
        msg.obj = ClementineMessage.getMessage(MsgType.DISCONNECT);
       
        // Send the request to the thread
        App.mClementineConnection.mHandler.sendMessage(msg);
    }

	
	/**
	 * Disconnect was finished, now finish this activity
	 */
	void disconnect() {
		makeToast(R.string.player_disconnected, Toast.LENGTH_SHORT);
		if (mOpenConnectDialog) {
			setResult(ConnectDialog.RESULT_DISCONNECT);
		} else {
			setResult(ConnectDialog.RESULT_QUIT);	
		}
		mLastPosition = 1;
		finish();
	}
	
	/**
	 * We got a message from Clementine. Here we process it for the main activity
	 * and pass the data to the currently active fragment.
	 * Info: Errormessages were already parsed in PlayerHandler!
	 * @param clementineMessage The message from Clementine
	 */
	void MessageFromClementine(ClementineMessage clementineMessage) {
		// Update the Player Fragment
		if (mFragments.get(mCurrentFragment) != null && 
			mFragments.get(mCurrentFragment).isVisible()) {
			mFragments.get(mCurrentFragment).MessageFromClementine(clementineMessage);
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
    
    private class DrawerItemClickListener implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position, 300);
        }
    }

    /** Swaps fragments in the main content view */
    private void selectItem(final int position, int delay) {
        mDrawerLayout.closeDrawer(mDrawerList);
        
        new Handler().postDelayed(new Runnable() {

			@Override
			public void run() {
				if (mInstanceSaved) {
					return;
				}
				FragmentManager fragmentManager = getSupportFragmentManager();
				FragmentTransaction ft = fragmentManager.beginTransaction();
				ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
				ft.setCustomAnimations(R.drawable.anim_fade_in, R.drawable.anim_fade_out);
				
		    	switch (position) {
		    	case 0: // Header Remote
		    		break;
		    	case 1: // Player
		        	ft.replace(R.id.content_frame, mFragments.get(0)).commit();
		        	mCurrentFragment = 0;
		        	mLastPosition = position;
		        	break;
		    	case 2: // Songinfo
		        	ft.replace(R.id.content_frame, mFragments.get(1)).commit();
		        	mCurrentFragment = 1;
		        	mLastPosition = position;
		        	break;
		    	case 3: // Playlist
		        	ft.replace(R.id.content_frame, mFragments.get(2)).commit();
		        	mCurrentFragment = 2;
		        	mLastPosition = position;
		        	break;
		    	case 4: // Library
		    		ft.replace(R.id.content_frame, mFragments.get(3)).commit();
		        	mCurrentFragment = 3;
		        	mLastPosition = position;
		    		break;
		    	case 5: // Downloads
		    		ft.replace(R.id.content_frame, mFragments.get(4)).commit();
		    		mCurrentFragment = 4;
		            mLastPosition = position;
		    		break;
		    	case 6: // Header Settings
		    		break;
		    	case 7: // Settings
		    		Intent settingsIntent = new Intent(MainActivity.this, ClementineSettings.class);
		            startActivity(settingsIntent);
		            break;
		    	case 8: // Donate
		    		ft.replace(R.id.content_frame, mFragments.get(5)).commit();
		    		mCurrentFragment = 5;
		            mLastPosition = position;
		    		break;
		    	case 9: // Header Disconnect
		    		break;
		    	case 10: // Disonnect
		    		mOpenConnectDialog = true;
		    		requestDisconnect();
		    		break;
		    	case 11: // Quit
		    		mOpenConnectDialog = false;
		    		requestDisconnect();
		    	default:
		    		break;
		    	}
			}
        	
        }, delay);
    }

}
