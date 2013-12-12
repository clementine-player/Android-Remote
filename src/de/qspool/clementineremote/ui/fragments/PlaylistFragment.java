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

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.ClementineSongDownloader;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.adapter.PlaylistSongAdapter;

public class PlaylistFragment extends AbstractDrawerFragment {
	public final static String PLAYLIST_ID = "playlist_id";
	
	private PlaylistSongAdapter mAdapter;
	private ProgressDialog mProgressDialog;
	private ActionBar mActionBar; 
	private ListView mList;
	private Spinner mPlaylistSpinner = null;
	private View mEmptyPlaylist;
	
	private final LinkedList<MySong> mData = new LinkedList<MySong>();
	private int mId;
	
	private String mFilterText;
	private boolean mUpdateTrackPositionOnNewTrack = false;
	private int mSelectionOffset;
	
	private int mDownloadPlaylists;
	private LinkedList<String> mDownloadPlaylistNames;
	
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
	    
	    if (App.mClementine.getPlaylists().size() != 0)
	    	mId = App.mClementine.getPlaylists().valueAt(0).getId();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		// Check if we are still connected
		if (App.mClementineConnection == null
		 || App.mClementine           == null
		 || !App.mClementineConnection.isConnected()) {
		} else {
			RequestPlaylistSongs();
			setActionBarTitle();
			// Get the position of the current track if we have one
	        if (App.mClementine.getCurrentSong() != null) {
	        	updateViewPosition();
	        }
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		      Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.playlist_fragment,
				container, false);
		
		mList = (ListView) view.findViewById(R.id.songs);
		mEmptyPlaylist = view.findViewById(R.id.playlist_empty);
		
		// Create the adapter
		mAdapter = new PlaylistSongAdapter(getActivity(), R.layout.playlist_row, mData);
		
		mList.setOnItemClickListener(oiclSong);
		//mList.setOnItemLongClickListener(oilclSong);
		mList.setAdapter(mAdapter);
		registerForContextMenu(mList);
		
		// Filter the results
		mAdapter.getFilter().filter(mFilterText);
		
		mPlaylistSpinner = (Spinner) view.findViewById(R.id.playlist_spinner);
        List<CharSequence> arrayList = new ArrayList<CharSequence>();
        for (int i = 0; i < App.mClementine.getPlaylists().size(); i++) {
            arrayList.add(App.mClementine.getPlaylists().valueAt(i).getName());
        }
        
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getActivity(),
                android.R.layout.simple_spinner_item, arrayList);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mPlaylistSpinner.setAdapter(adapter);

        mPlaylistSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
            	mId = App.mClementine.getPlaylists().valueAt(position).getId();
            	updateSongList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) { }
        });
		
	    mActionBar.setTitle("");
	    mActionBar.setSubtitle("");
		
		return view;
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
	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
	    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	    MySong song = (MySong) mData.get(info.position);
	    switch (item.getItemId()) {
	        case R.id.playlist_context_play:
	        	playSong(song);
	            return true;
	        case R.id.playlist_context_remove:
	            Message msg = Message.obtain();
	            msg.obj = ClementineMessageFactory.buildRemoveSongFromPlaylist(mId, song);
	            App.mClementineConnection.mHandler.sendMessage(msg);
	            mAdapter.remove(song);
	            mAdapter.notifyDataSetChanged();
	            return true;
	        default:
	            return super.onContextItemSelected(item);
	    }
	}
	
	public int getPlaylistId() {
		return mId;
	}
	
	private void setActionBarTitle() {
		MySong currentSong = App.mClementine.getCurrentSong();
		if (currentSong == null) {
			mActionBar.setTitle(getString(R.string.player_nosong));
			mActionBar.setSubtitle("");
		} else {
			mActionBar.setTitle(currentSong.getArtist());
			mActionBar.setSubtitle(currentSong.getTitle());
		}
	}
	
	/**
	 * Update the underlying data. It reloads the current playlist songs from the Clementine object.
	 */
	public void updateSongList() {
		try {
			App.mClementine.PlaylistsAvailable.acquire();
			
			mData.clear();
			mData.addAll(App.mClementine.getPlaylists().get(mId).getPlaylistSongs());
			
			// Check if we should update the current view position
			mAdapter = new PlaylistSongAdapter(getActivity(), R.layout.playlist_row, mData);
			mList.setAdapter(mAdapter);
			
			updateViewPosition();
			
			if (mData.isEmpty()) {
				mList.setEmptyView(mEmptyPlaylist);
			}
			
			App.mClementine.PlaylistsAvailable.release();;
		} catch (InterruptedException e) {
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
	
	@Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mList.setFastScrollEnabled(true);
        mList.setTextFilterEnabled(true);
        mList.setSelector(android.R.color.transparent);
        mList.setDivider(null);
        mList.setDividerHeight(0);
	}
	
	private OnItemClickListener oiclSong = new OnItemClickListener() {
	    @Override
	    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	        MySong song = (MySong) mData.get(position);
	        
	        playSong(song);
	    }
	};
	
	private void playSong(MySong song) {
		Message msg = Message.obtain();
        msg.obj = ClementineMessageFactory.buildRequestChangeSong(song.getIndex(), mId);
        App.mClementineConnection.mHandler.sendMessage(msg);
        
        // save which playlist is the active one
        for (int i = 0; i<App.mClementine.getPlaylists().size(); i++) {
        	App.mClementine.getPlaylists().valueAt(i).setActive(false);
        }
        
        App.mClementine.getPlaylists().get(mId).setActive(true);
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
			setActionBarTitle();
			break;
		case PLAYLIST_SONGS:
			if (mProgressDialog != null && mProgressDialog.isShowing()) {
				checkGotAllPlaylists();
			} else {
				updateSongList();
			}
			
			break;
		default:
			break;
		}
	}

	/**
	 * Sends a request to Clementine to send all songs in all active playlists.
	 */
	public void RequestPlaylistSongs() {
		checkGotAllPlaylists();
		
		// If a progress is showing, do not show again!
		if (mProgressDialog != null && mProgressDialog.isShowing())
			return;
		
		mDownloadPlaylists = 0;
		mDownloadPlaylistNames = new LinkedList<String>();
		
		for (int i=0;i<App.mClementine.getPlaylists().size();i++) {
			// Get the Playlsit
			int key = App.mClementine.getPlaylists().keyAt(i);
			MyPlaylist playlist = App.mClementine.getPlaylists().get(key);
			if (playlist.getPlaylistSongs().size() == 0) {
				Message msg = Message.obtain();
				msg.obj = ClementineMessageFactory.buildRequestPlaylistSongs(playlist.getId());
				App.mClementineConnection.mHandler.sendMessage(msg);
				mDownloadPlaylists++;
				mDownloadPlaylistNames.add(playlist.getName());
			}
		}
		
		// Open it directly only when we got all playlists
		if (mDownloadPlaylists != 0) {
			// Start a Progressbar
			mProgressDialog = new ProgressDialog(getActivity());
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
				getSherlockActivity().supportInvalidateOptionsMenu();
				mPlaylistSpinner.setSelection(App.mClementine.getPlaylists().indexOfValue(App.mClementine.getActivePlaylist()));
			}
		}
	}

	@Override
	public boolean onBackPressed() {
		return false;
	}
}
