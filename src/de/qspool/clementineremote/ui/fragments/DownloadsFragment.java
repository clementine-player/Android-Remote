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

import java.util.Timer;
import java.util.TimerTask;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.adapter.DownloadAdapter;
import de.qspool.clementineremote.utils.Utilities;

public class DownloadsFragment extends AbstractDrawerFragment {
	private ActionBar mActionBar; 
	private ListView mList;
	private DownloadAdapter mAdapter;
	private Timer mUpdateTimer;
	private TextView mFreeSpace;

	private View mEmptyDownloads;
	
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
		 || !App.mClementineConnection.isConnected()) {
		} else {
			//RequestPlaylistSongs();
			setActionBarTitle();
			mUpdateTimer = new Timer();
			mUpdateTimer.scheduleAtFixedRate(getTimerTask(), 250, 250);
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (mUpdateTimer != null)
			mUpdateTimer.cancel();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		      Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.download_fragment,
				container, false);
		
		mList = (ListView) view.findViewById(R.id.downloads);
		mEmptyDownloads = view.findViewById(R.id.downloads_empty);
		mFreeSpace = (TextView) view.findViewById(R.id.downloads_freespace);
		
		// Create the adapter
		mAdapter = new DownloadAdapter(getActivity(), R.layout.download_row, App.downloaders);
		
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

	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {		
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
		case CURRENT_METAINFO:
			setActionBarTitle();
			break;
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

	/**
	 * Creates a timer task for refeshing the download list
	 * @return Task to update download list
	 */
	private TimerTask getTimerTask() {
		return new TimerTask() {

			@Override
			public void run() {
				if (mAdapter != null && getActivity() != null) {
					getActivity().runOnUiThread(new Runnable() {

						@Override
						public void run() {
							mAdapter.notifyDataSetChanged();
							if (App.downloaders.isEmpty()) {
								mList.setEmptyView(mEmptyDownloads);
							}
							
							StringBuilder sb = new StringBuilder();
							sb.append(getActivity().getString(R.string.download_freespace));
							sb.append(": ");
							sb.append(Utilities.humanReadableBytes((long) Utilities.getFreeSpaceExternal(), true));
							mFreeSpace.setText(sb.toString());
						}
						
					});
				}
			}
			
		};
	}

	@Override
	public boolean onBackPressed() {
		return false;
	}
}
