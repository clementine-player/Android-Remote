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

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filterable;
import android.widget.TextView;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MyLibraryItem;

/**
 * Class is used for displaying the song data
 */
public class LibraryAdapter extends ArrayAdapter<MyLibraryItem> implements Filterable {
	private Context mContext;
	
	private List<MyLibraryItem> mData;

	public LibraryAdapter(Context context, int resource,
			List<MyLibraryItem> data) {
		super(context, resource, data);
		mContext = context;
		mData = data;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		MyLibraryItem item = mData.get(position);
		
		if (convertView == null) {
			convertView = ((Activity)mContext).getLayoutInflater()
							.inflate(R.layout.library_row, parent, false);
		}

		convertView.setBackgroundResource(R.drawable.white_background);
		
		TextView tvTitle    = (TextView) convertView.findViewById(R.id.tv_lib_title);
		TextView tvSubtitle = (TextView) convertView.findViewById(R.id.tv_lib_subtitle);
		
		tvTitle.setText(item.getText());
		tvSubtitle.setText(item.getSubtext());
		
		return convertView;
	}
}