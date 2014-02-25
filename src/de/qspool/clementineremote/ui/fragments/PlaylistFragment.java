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

package de.qspool.clementineremote.ui.fragments;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.OnNavigationListener;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.ClementineSongDownloader;
import de.qspool.clementineremote.backend.event.OnPlaylistReceivedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.player.PlaylistManager;
import de.qspool.clementineremote.ui.adapter.PlaylistSongAdapter;

public class PlaylistFragment extends AbstractDrawerFragment {
	public final static String PLAYLIST_ID = "playlist_id";
	
	private PlaylistSongAdapter mAdapter;
	private ProgressDialog mProgressDialog;
	private ActionBar mActionBar;
	private ActionMode mActionMode;
	private ListView mList;
	private View mEmptyPlaylist;
	
	private View mSelectedItem;
	private Drawable mSelectedItemDrawable;
	private MySong mSelectedSong;
	
	private PlaylistManager mPlaylistManager;
	private OnPlaylistReceivedListener mPlaylistListener;
	
	private LinkedList<MyPlaylist> mPlaylists = new LinkedList<MyPlaylist>();
	
	private String mFilterText;
	private boolean mUpdateTrackPositionOnNewTrack = false;
	private int mSelectionOffset;
	
	public PlaylistFragment() {
		mFilterText = "";
		mSelectionOffset = 3;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	    // Get the actionbar
	    mActionBar = getSherlockActivity().getSupportActionBar();
	    setHasOptionsMenu(true);
	    
	    mPlaylistManager = App.mClementine.getPlaylistManager();
	    mPlaylistListener = new OnPlaylistReceivedListener() {
			@Override
			public void onPlaylistSongsReceived(final MyPlaylist p) {
				getSherlockActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (mProgressDialog != null) {
							mProgressDialog.setProgress(mProgressDialog.getProgress()+1);
							mProgressDialog.setMessage(p.getName());
						}
					}
				});
			}
			
			@Override
			public void onPlaylistReceived(final MyPlaylist p) {
				getSherlockActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						updatePlaylistSpinner();
					}
				});
			}
			
			@Override
			public void onAllRequestedPlaylistSongsReceived() {
				getSherlockActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (mProgressDialog != null && mProgressDialog.isShowing()) {
							mPlaylists = mPlaylistManager.getAllPlaylists();
							
							mProgressDialog.dismiss();
							getSherlockActivity().supportInvalidateOptionsMenu();
							
							mActionBar.setSelectedNavigationItem(mPlaylists.indexOf(mPlaylistManager.getActivePlaylist()));
						}
					}
				});
			}
		};
	}
	
	@Override
	public void onResume() {
		super.onResume();
		// Check if we are still connected
		if (App.mClementineConnection == null
		 || App.mClementine           == null
		 || !App.mClementineConnection.isConnected()) {
			return;
		} 

		RequestPlaylistSongs();
		
		mPlaylistManager.addOnPlaylistReceivedListener(mPlaylistListener);
		mPlaylists = mPlaylistManager.getAllPlaylists();
		
		// Get the position of the current track if we have one
        if (App.mClementine.getCurrentSong() != null) {
        	updateViewPosition();
        }
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		mPlaylistManager.removeOnPlaylistReceivedListener(mPlaylistListener);
		mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		      Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.playlist_fragment,
				container, false);
		
		mPlaylists = mPlaylistManager.getAllPlaylists();
		
		mList = (ListView) view.findViewById(R.id.songs);
		mEmptyPlaylist = view.findViewById(R.id.playlist_empty);
		
		// update spinner
		mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		updatePlaylistSpinner();

		// Create the adapter
		mAdapter = new PlaylistSongAdapter(getActivity(), R.layout.playlist_row, getSelectedPlaylistSongs());
		
		mList.setOnItemClickListener(oiclSong);
		mList.setAdapter(mAdapter);
		mList.setLongClickable(true);
		mList.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (mActionMode != null) {
		            return false;
		        }
				
				mSelectedItem = view;
				mSelectedItemDrawable = mSelectedItem.getBackground();
				mSelectedItem.setBackgroundColor(getResources().getColor(R.color.orange_light));
				
				mSelectedSong = (MySong) getSelectedPlaylistSongs().get(position);

		        // Start the CAB using the ActionMode.Callback defined above
		        mActionMode = getSherlockActivity().startActionMode(mActionModeCallback);
		        view.setSelected(true);
		        return true;
			}
		});
		
		// Filter the results
		mAdapter.getFilter().filter(mFilterText);
		
	    mActionBar.setTitle("");
	    mActionBar.setSubtitle("");
		
		return view;
	}
	
	@Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mList.setFastScrollEnabled(true);
        mList.setTextFilterEnabled(true);
        mList.setSelector(android.R.color.transparent);
        mList.setDivider(null);
        mList.setDividerHeight(0);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case R.id.download_playlist: 
				ClementineSongDownloader downloaderAlbum = new ClementineSongDownloader(getActivity());
				
				// Get the playlist id and download the playlist
				downloaderAlbum.startDownload(ClementineMessageFactory.buildDownloadSongsMessage(getPlaylistId(), DownloadItem.APlaylist));
				return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.playlist_menu, menu);
		
		super.onCreateOptionsMenu(menu,inflater);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	                                ContextMenuInfo menuInfo) {
	    super.onCreateContextMenu(menu, v, menuInfo);
	    android.view.MenuInflater inflater = getActivity().getMenuInflater();
	    inflater.inflate(R.menu.playlist_context_menu, menu);
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		// Create a listener for search change
		SearchView searchView = (SearchView) menu.findItem(R.id.playlist_menu_search).getActionView();
		
		final SearchView.OnQueryTextListener queryTextListener = new    SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
            	// Set the filter text as the fragments might not yet
            	// created. Only the left and right fragment from the
            	// currently active is created (onCreate() called).
            	// Therefore the other adapters are not yet created,
            	// onCreate filters for this string given in setFilterText()
            	setFilterText(newText);
            	if (getAdapter() != null) {
            		getAdapter().getFilter().filter(newText);
            	}
                return true;
            }
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Do something
            	setFilterText(query);
            	if (getAdapter() != null) {
            		getAdapter().getFilter().filter(query);
            	}

                return true;
            }
        };
        searchView.setOnQueryTextListener(queryTextListener);
		searchView.setQueryHint(getString(R.string.playlist_search_hint));
		
		super.onPrepareOptionsMenu(menu);
	}
	
	/**
	 * Update the underlying data. It reloads the current playlist songs from the Clementine object.
	 */
	public void updateSongList() {
		// Check if we should update the current view position
		mAdapter = new PlaylistSongAdapter(getActivity(), R.layout.playlist_row, getSelectedPlaylistSongs());
		mList.setAdapter(mAdapter);
		
		updateViewPosition();
		
		if (mPlaylists.isEmpty()) {
			mList.setEmptyView(mEmptyPlaylist);
		}
		
	}
	
	/**
	 * Set the text to filter
	 * @param filterText String, which results are filtered by
	 */
	public void setFilterText(String filterText) {
		mFilterText = filterText;
	}
	
	/**
	 * Get the song adapter
	 * @return The CustomSongAdapter
	 */
	public PlaylistSongAdapter getAdapter() {
		return mAdapter;
	}
	
	private OnItemClickListener oiclSong = new OnItemClickListener() {
	    @Override
	    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	        MySong song = (MySong) getSelectedPlaylistSongs().get(position);
	        
	        playSong(song);
	    }
	};
	
	private void playSong(MySong song) {
		Message msg = Message.obtain();
        msg.obj = ClementineMessageFactory.buildRequestChangeSong(song.getIndex(), getPlaylistId());
        App.mClementineConnection.mHandler.sendMessage(msg);
        
        mPlaylistManager.setActivePlaylist(getPlaylistId());
	}

    
    /**
     * Set the selection to the currently played item
     */
    private void updateViewPosition() {
    	if (App.mClementine.getCurrentSong() != null) {
    		int pos = App.mClementine.getCurrentSong().getIndex();
    		mList.setSelection(pos - mSelectionOffset);
    	}
    }

	public boolean isUpdateTrackPositionOnNewTrack() {
		return mUpdateTrackPositionOnNewTrack;
	}

	public void setUpdateTrackPositionOnNewTrack(
			boolean updateTrackPositionOnNewTrack, int offset) {
		this.mUpdateTrackPositionOnNewTrack = updateTrackPositionOnNewTrack;
		mSelectionOffset = offset;
	}
	
	@Override
	public void MessageFromClementine(ClementineMessage clementineMessage) {
		switch (clementineMessage.getMessageType()) {
		case CURRENT_METAINFO:
			updateSongList();
			break;
		default:
			break;
		}
	}

	/**
	 * Sends a request to Clementine to send all songs in all active playlists.
	 */
	public void RequestPlaylistSongs() {
		// If a progress is showing, do not show again!
		if (mProgressDialog != null && mProgressDialog.isShowing())
			return;
		
		// Open it directly only when we got all playlists
		int requests = mPlaylistManager.requestAllPlaylistSongs();
		if (requests > 0) {
			// Start a Progressbar
			mProgressDialog = new ProgressDialog(getActivity());
			mProgressDialog.setMax(requests);
			mProgressDialog.setCancelable(true);
			mProgressDialog.setTitle(R.string.player_download_playlists);
			mProgressDialog.setMessage(getString(R.string.playlist_loading));
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.show();
		}
	}

	@Override
	public boolean onBackPressed() {
		return false;
	}
	
	private void updatePlaylistSpinner() {
		List<CharSequence> arrayList = new ArrayList<CharSequence>();
        for (int i = 0; i < mPlaylists.size(); i++) {
            arrayList.add(mPlaylists.get(i).getName());
        }
        
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getActivity(),
                android.R.layout.simple_spinner_item, arrayList);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mActionBar.setListNavigationCallbacks(adapter, new OnNavigationListener() {
			
			@Override
			public boolean onNavigationItemSelected(int itemPosition, long itemId) {
				updateSongList();
				return true;
			}
		});
	}
	
	private int getPlaylistId() {
		return mPlaylists.get(mActionBar.getSelectedNavigationIndex()).getId();
	}
	
	private LinkedList<MySong> getSelectedPlaylistSongs() {
		int pos = mActionBar.getSelectedNavigationIndex();
		if (pos == Spinner.INVALID_POSITION) {
			pos = 0; // We have always at least one playlist!
		}
		return mPlaylists.get(pos).getPlaylistSongs();
	}
	
	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

	    // Called when the action mode is created; startActionMode() was called
	    @Override
	    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
	        // Inflate a menu resource providing context menu items
	        MenuInflater inflater = mode.getMenuInflater();
	        inflater.inflate(R.menu.playlist_context_menu, menu);
	        return true;
	    }

	    // Called each time the action mode is shown. Always called after onCreateActionMode, but
	    // may be called multiple times if the mode is invalidated.
	    @Override
	    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
	        return false; // Return false if nothing is done
	    }

	    // Called when the user selects a contextual menu item
	    @Override
	    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {		    
	        switch (item.getItemId()) {
	        case R.id.playlist_context_play:
	        	playSong(mSelectedSong);
	        	mode.finish();
	            return true;
	        case R.id.playlist_context_remove:
	            Message msg = Message.obtain();
	            msg.obj = ClementineMessageFactory.buildRemoveSongFromPlaylist(getPlaylistId(), mSelectedSong);
	            App.mClementineConnection.mHandler.sendMessage(msg);
	            mAdapter.remove(mSelectedSong);
	            mAdapter.notifyDataSetChanged();
	            mode.finish();
	            return true;
	        default:
	        	return false;
	        }
	    }

	    // Called when the user exits the action mode
	    @SuppressWarnings("deprecation")
		@SuppressLint("NewApi")
		@Override
	    public void onDestroyActionMode(ActionMode mode) {
	        mActionMode = null;
	        mSelectedSong = null;
	        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
	        	mSelectedItem.setBackground(mSelectedItemDrawable);
	        else
	        	mSelectedItem.setBackgroundDrawable(mSelectedItemDrawable);
	    }
	};
}
