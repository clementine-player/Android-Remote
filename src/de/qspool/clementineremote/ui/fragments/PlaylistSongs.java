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

import java.util.LinkedList;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.requests.RequestChangeCurrentSong;
import de.qspool.clementineremote.ui.adapter.CustomSongAdapter;

public class PlaylistSongs extends SherlockListFragment {
	public final static String PLAYLIST_ID = "playlist_id";
	private LinkedList<MySong> mData;
	private int mId;
	private Activity mActivity;
	CustomSongAdapter mAdapter;
	
	public PlaylistSongs() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// On the first call we don't have the id yet
		if (savedInstanceState != null
		 && savedInstanceState.containsKey(PLAYLIST_ID)) {
			setId(savedInstanceState.getInt(PLAYLIST_ID));
		}
		
		// Create the adapter
		mAdapter = new CustomSongAdapter(mActivity, R.layout.song_row, mData);
		setListAdapter(mAdapter);
	}
	
	public void setId(int id) {
		mId = id;
		mData = new LinkedList<MySong>(App.mClementine.getPlaylists().get(mId).getPlaylistSongs());
	}
	
	public CustomSongAdapter getAdapter() {
		return mAdapter;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(PLAYLIST_ID, mId);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mActivity = activity;
	}
	
	@Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getListView().setFastScrollEnabled(true);
        getListView().setTextFilterEnabled(true);
        getListView().setSelector(android.R.color.transparent);
         
        // Get the position of the current track if we have one
        if (App.mClementine.getCurrentSong() != null) {
        	int pos = App.mClementine.getCurrentSong().getIndex();
        	getListView().setSelection(pos - 3);
        }
	}
	
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        MySong song = (MySong) getListAdapter().getItem(position);
        
        Message msg = Message.obtain();
        msg.obj = new RequestChangeCurrentSong(song, mId);
        App.mClementineConnection.mHandler.sendMessage(msg);

        getActivity().finish();
    }
}
