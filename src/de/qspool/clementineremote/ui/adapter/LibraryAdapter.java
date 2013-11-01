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
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MyLibraryItem;

/**
 * Class is used for displaying the song data
 */
public class LibraryAdapter extends ArrayAdapter<MyLibraryItem> implements Filterable {
	private Context mContext;
	
	private Filter mFilter;
	
	private List<MyLibraryItem> mData;
	private List<MyLibraryItem> mOrigData;

	public LibraryAdapter(Context context, int resource,
			List<MyLibraryItem> data) {
		super(context, resource, data);
		mContext = context;
		mData = data;
		mOrigData = new LinkedList<MyLibraryItem>(data);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		MyLibraryItem item = mData.get(position);
		
		if (convertView == null) {
			convertView = ((Activity)mContext).getLayoutInflater()
							.inflate(R.layout.library_row, parent, false);
		}

		convertView.setBackgroundResource(R.drawable.listitem_white);
		
		TextView tvTitle    = (TextView) convertView.findViewById(R.id.tv_lib_title);
		TextView tvSubtitle = (TextView) convertView.findViewById(R.id.tv_lib_subtitle);
		
		tvTitle.setText(item.getText());
		tvSubtitle.setText(item.getSubtext());
		
		return convertView;
	}
	
	@Override
	public Filter getFilter() {
		if (mFilter == null) {
			mFilter = new CustomFilter();
		}
		return mFilter;
	}
	
	private class CustomFilter extends Filter {

	    @Override
	    protected FilterResults performFiltering(CharSequence constraint) {
	    	String cs = constraint.toString().toLowerCase();
	        FilterResults results = new FilterResults();

	        if(constraint == null || constraint.length() == 0) {
	            List<MyLibraryItem> list = new LinkedList<MyLibraryItem>(mOrigData);
	            results.values = list;
	            results.count = list.size();
	        } else {
	            List<MyLibraryItem> filteredItems = new LinkedList<MyLibraryItem>();
	            for(int i = 0; i < mOrigData.size(); i++) {
	            	MyLibraryItem item = mOrigData.get(i);
	                if(item.getArtist().toLowerCase().contains(cs)
	                 || item.getAlbum().toLowerCase().contains(cs)
	                 || item.getTitle().toLowerCase().contains(cs)) {
	                    filteredItems.add(item);
	                }
	            }
	            results.values = filteredItems;
	            results.count = filteredItems.size();
	        }       

	        return results;
	    }

	    @SuppressWarnings("unchecked")
	    @Override
	    protected void publishResults(CharSequence constraint,
	            FilterResults results) {
	    	mData.clear();
	    	mData.addAll((List<MyLibraryItem>) results.values);
	        notifyDataSetChanged();
	    }

	}
}