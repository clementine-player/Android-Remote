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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import de.qspool.clementineremote.R;

public class NavigationDrawerListAdapter extends ArrayAdapter<NavigationDrawerListAdapter.NavigationDrawerItem> {

    public static class NavigationDrawerItem {
        public static enum Type {
            TYPE_SECTION,
            TYPE_ITEM
        }
        public String title;
        public Drawable icon;
        public Type type;

        public NavigationDrawerItem(String title, Drawable icon, Type type) {
            this.title = title;
            this.icon = icon;
            this.type = type;
        }
    }

    private LayoutInflater mLayoutInflater;

    public NavigationDrawerListAdapter(Context context, int resource,
            List<NavigationDrawerItem> items) {
        super(context, resource, items);

        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        NavigationDrawerItem item = getItem(position);

        switch (item.type) {
            case TYPE_SECTION:
                convertView = mLayoutInflater.inflate(R.layout.item_drawer_list_spacer, parent, false);
                break;
            case TYPE_ITEM:
                NavigationItemViewHolder viewHolder;
                if (convertView == null) {
                    convertView = mLayoutInflater.inflate(R.layout.item_drawer_list, parent, false);

                    viewHolder = new NavigationItemViewHolder();
                    viewHolder.image = (ImageView) convertView.findViewById(R.id.drawer_item_icon);
                    viewHolder.title = (TextView) convertView.findViewById(R.id.drawer_item_title);

                    convertView.setTag(viewHolder);
                } else {
                    viewHolder = (NavigationItemViewHolder) convertView.getTag();
                }

                viewHolder.image.setImageDrawable(item.icon);
                viewHolder.title.setText(item.title);
                break;
        }

        return convertView;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type.ordinal();
    }

    @Override
    public int getViewTypeCount() {
        return NavigationDrawerItem.Type.values().length;
    }

    @Override
    public boolean isEnabled(int position) {
        return getItem(position).type == NavigationDrawerItem.Type.TYPE_ITEM;
    }

    private static class NavigationItemViewHolder {
        public ImageView image;
        public TextView title;
    }
}