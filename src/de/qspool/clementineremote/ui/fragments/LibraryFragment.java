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

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.ClementineLibraryDownloader;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.MyLibrary;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.adapter.DownloadAdapter;
import de.qspool.clementineremote.ui.adapter.LibraryAdapter;

public class LibraryFragment extends AbstractDrawerFragment {
	private ActionBar mActionBar; 
	private ListView mList;
	private LibraryAdapter mAdapter;
	
	private MyLibrary mLibrary = new MyLibrary();
	private ClementineLibraryDownloader mLibraryDownloader;

	private View mEmptyLibrary;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	    // Get the actionbar
	    mActionBar = getSherlockActivity().getSupportActionBar();
	    setHasOptionsMenu(true);
	}

	@Override
	public void onResume() {
		super.onResume();
		// Check if we are still connected
		if (App.mClementineConnection == null
		 || App.mClementine           == null
		 || !App.mClementineConnection.isAlive()
		 || !App.mClementineConnection.isConnected()) {
		} else {
			//RequestPlaylistSongs();
			setActionBarTitle();
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		      Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.library_fragment, container, false);
		
		mList = (ListView) view.findViewById(R.id.library);
		mEmptyLibrary = view.findViewById(R.id.library_empty);
		
		// Create the adapter
		mAdapter = new LibraryAdapter(getActivity(), R.layout.library_row, mLibrary.getArtists());
		
		mList.setOnItemClickListener(oiclDownload);
		mList.setAdapter(mAdapter);
		
	    mActionBar.setTitle("");
	    mActionBar.setSubtitle("");
		
		setHasOptionsMenu(true);
		
		return view;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.library_menu_refresh:
	    	mLibraryDownloader = new ClementineLibraryDownloader(getActivity());
	    	mLibraryDownloader.startDownload(ClementineMessage.getMessage(MsgType.GET_LIBRARY));
	    	break;
	    default:    
	    	return super.onOptionsItemSelected(item);
	    }
	    
	    return true;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.library_menu, menu);
		super.onCreateOptionsMenu(menu,inflater);
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
	
	@Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mList.setFastScrollEnabled(true);
        mList.setTextFilterEnabled(true);
        mList.setSelector(android.R.color.transparent);
        mList.setOnItemClickListener(oiclDownload);
	}
	
	@Override
	public void MessageFromClementine(ClementineMessage clementineMessage) {
		switch (clementineMessage.getMessageType()) {

		default:
			break;
		}
	}
	
	private OnItemClickListener oiclDownload = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			if (App.downloaders.get(position).getStatus() == AsyncTask.Status.FINISHED) {
				Uri lastFile = App.downloaders.get(position).getLastFileUri();
				if (lastFile == null) {
					Toast.makeText(getActivity(), R.string.download_error, Toast.LENGTH_LONG).show();
				} else {
					Intent mediaIntent = new Intent();
					mediaIntent.setAction(Intent.ACTION_VIEW);
					mediaIntent.setDataAndType(lastFile, "audio/*");
					mediaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
					
					if (mediaIntent.resolveActivity(getActivity().getPackageManager()) != null) {
					    startActivity(mediaIntent);
					} else {
					    Toast.makeText(getActivity(), R.string.app_not_available, Toast.LENGTH_LONG).show();
					}
				}
			} else {
				// Just do nothing
			}
		}
	};
}
