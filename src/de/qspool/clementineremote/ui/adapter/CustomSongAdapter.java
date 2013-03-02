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
import android.view.View;
import android.view.ViewGroup;
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
public class CustomSongAdapter extends ArrayAdapter<MySong> implements Filterable {
	private Context mContext;
	private List<MySong> mData;
	private List<MySong> mOrigData;
	private Filter mFilter;

	public CustomSongAdapter(Context context, int resource,
			List<MySong> data) {
		super(context, resource, data);
		mContext = context;
		mData = data;
		mOrigData = new LinkedList<MySong>(data);
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
		
		if (convertView == null) {
			convertView = ((Activity)mContext).getLayoutInflater()
							.inflate(R.layout.song_row, parent, false);
		}
		
		if (App.mClementine.getCurrentSong() != null 
		 && App.mClementine.getCurrentSong().equals(mData.get(position))) {
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
}