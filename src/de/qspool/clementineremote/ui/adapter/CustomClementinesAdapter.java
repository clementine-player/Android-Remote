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

import javax.jmdns.ServiceInfo;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import de.qspool.clementineremote.R;

/**
 * Class is used for displaying the song data
 */
public class CustomClementinesAdapter extends ArrayAdapter<ServiceInfo> {
	private Context mContext;
	private List<ServiceInfo> mData;

	public CustomClementinesAdapter(Context context, int resource,
			List<ServiceInfo> data) {
		super(context, resource, data);
		mContext = context;
		mData = data;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		if (convertView == null) {
			convertView = ((Activity)mContext).getLayoutInflater()
							.inflate(R.layout.dialog_list_item, parent, false);
		}

		convertView.setBackgroundResource(R.drawable.white_background_border);
		
		TextView tvClHost = (TextView) convertView.findViewById(R.id.tvClItemHost);
		TextView tvClIp  = (TextView) convertView.findViewById(R.id.tvClIp);
		
		tvClHost.setText(mData.get(position).getName());
		tvClIp.setText( mData.get(position).getInet4Addresses()[0].toString().split("/")[1] + 
						":" + mData.get(position).getPort());
		
		return convertView;
	}

}