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
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.requests.RequestChangeCurrentSong;

@SuppressLint("ValidFragment")
public class PlaylistSongs extends SherlockListFragment {
	private LinkedList<MySong> mData;
	private int mId;
	
	/**
	 * Constructor
	 * @param id The Playlist-Id
	 */
	public PlaylistSongs(int id) {
		mId = id;
		mData = App.mClementine.getPlaylists().get(mId).getPlaylistSongs();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		// Create the adapter
		CustomSongAdapter adapter = new CustomSongAdapter(activity, R.layout.song_row, mData);
		setListAdapter(adapter);
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
	
	/**
	 * Class is used for displaying the song data
	 */
	private class CustomSongAdapter extends ArrayAdapter<MySong> {
		Context mContext;

		public CustomSongAdapter(Context context, int resource,
				List<MySong> objects) {
			super(context, resource, objects);
			mContext = context;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			if (convertView == null) {
				convertView = ((Activity)mContext).getLayoutInflater()
								.inflate(R.layout.song_row, parent, false);
			}
			
			if (App.mClementine.getCurrentSong().equals(mData.get(position))) {
				convertView.setBackgroundResource(R.drawable.orange_background_border);
			} else {
				convertView.setBackgroundResource(R.drawable.white_background_border);
			}
			
			TextView tvArtist = (TextView) convertView.findViewById(R.id.tvRowArtist);
			TextView tvTitle  = (TextView) convertView.findViewById(R.id.tvRowTitle);
			TextView tvLength = (TextView) convertView.findViewById(R.id.tvRowLength);
			
			tvArtist.setText(mData.get(position).getArtist());
			tvTitle .setText(mData.get(position).getTitle() + 
							 " / " + 
							 mData.get(position).getAlbum());
			tvLength.setText(mData.get(position).getPrettyLength());
			
			return convertView;
		}
	}
}
