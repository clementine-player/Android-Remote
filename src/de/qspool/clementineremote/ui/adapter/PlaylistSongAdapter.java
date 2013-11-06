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

package de.qspool.clementineremote.ui.adapter;

import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MySong;

/**
 * Class is used for displaying the song data
 */
public class PlaylistSongAdapter extends ArrayAdapter<MySong> implements Filterable {
	private Context mContext;
	private List<MySong> mData;
	private List<MySong> mOrigData;
	private Filter mFilter;
	private boolean mShowTrackNo = true;

	public PlaylistSongAdapter(Context context, int resource,
			List<MySong> data) {
		super(context, resource, data);
		mContext = context;
		mData = data;
		mOrigData = new LinkedList<MySong>(data);
		
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		mShowTrackNo = sharedPref.getBoolean(App.SP_SHOW_TRACKNO, true);
	}
	
	public void updateSongs(List<MySong> data) {
		mOrigData = new LinkedList<MySong>(data);
		notifyDataSetChanged();
	}
	
	@Override
	public Filter getFilter() {
		if (mFilter == null) {
			mFilter = new CustomFilter();
		}
		return mFilter;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		PlaylistViewHolder playlistViewHolder;
		
		if (convertView == null) {
			convertView = ((Activity)mContext).getLayoutInflater()
							.inflate(R.layout.playlist_row, parent, false);
			
			playlistViewHolder = new PlaylistViewHolder();
			
			playlistViewHolder.artist  = (TextView) convertView.findViewById(R.id.tvRowArtist);
			playlistViewHolder.title   = (TextView) convertView.findViewById(R.id.tvRowTitle);
			playlistViewHolder.length  = (TextView) convertView.findViewById(R.id.tvRowLength);
			playlistViewHolder.trackNo = (TextView) convertView.findViewById(R.id.tvTrackNo);
			
			convertView.setTag(playlistViewHolder);
		} else {
			playlistViewHolder = (PlaylistViewHolder) convertView.getTag();
		}
		
		if (App.mClementine.getCurrentSong() != null 
		 && App.mClementine.getCurrentSong().equals(mData.get(position))) {
			convertView.setBackgroundResource(R.drawable.listitem_orange);
		} else {
			convertView.setBackgroundResource(R.drawable.listitem_white);
		}

		// Hide the tracknumber  
		if (!mShowTrackNo) {
			LayoutParams params = playlistViewHolder.trackNo.getLayoutParams();
			params.width = 0;
			params.height = 0;
			playlistViewHolder.trackNo.setLayoutParams(params);
		}
		
		playlistViewHolder.trackNo.setText(String.valueOf(mData.get(position).getTrack()) + ".");
		playlistViewHolder.artist.setText(mData.get(position).getArtist());
		playlistViewHolder.title .setText(mData.get(position).getTitle() + 
						 " / " + 
						 mData.get(position).getAlbum());
		playlistViewHolder.length.setText(mData.get(position).getPrettyLength());
		
		return convertView;
	}
	
	 @Override
     public int getCount() {
     	return mData.size();
     }
     @Override
     public MySong getItem(int position) {
         return mData.get(position);
     }
     @Override
     public int getPosition(MySong item) {
         return mData.indexOf(item);
     }
     @Override
     public long getItemId(int position) {
         return position;
     }
     @Override
     public void notifyDataSetChanged() {
         super.notifyDataSetChanged();
     }
	
	private class CustomFilter extends Filter {

	    @Override
	    protected FilterResults performFiltering(CharSequence constraint) {
	    	String cs = constraint.toString();
	        FilterResults results = new FilterResults();

	        if(constraint == null || constraint.length() == 0) {
	            List<MySong> list = new LinkedList<MySong>(mOrigData);
	            results.values = list;
	            results.count = list.size();
	        } else {
	            List<MySong> filteredSongs = new LinkedList<MySong>();
	            for(int i = 0; i < mOrigData.size(); i++) {
	                MySong song = mOrigData.get(i);
	                if(song.contains(cs)) {
	                    filteredSongs.add(song);
	                }
	            }
	            results.values = filteredSongs;
	            results.count = filteredSongs.size();
	        }       

	        return results;
	    }

	    @SuppressWarnings("unchecked")
	    @Override
	    protected void publishResults(CharSequence constraint,
	            FilterResults results) {
	    	mData.clear();
	    	mData.addAll((List<MySong>) results.values);
	        notifyDataSetChanged();
	    }

	}
	
	private class PlaylistViewHolder {
		TextView artist;
		TextView title;
		TextView length;
		TextView trackNo;
	}
}