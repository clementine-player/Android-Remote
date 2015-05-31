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

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.database.DynamicSongQuery;
import de.qspool.clementineremote.backend.database.SongSelectItem;

/**
 * Class is used for displaying the song data
 */
public class DynamicSongQueryAdapter extends CursorAdapter implements Filterable {

    private Context mContext;

    private DynamicSongQuery mDynamicSongQuery;


    public DynamicSongQueryAdapter(Context context, DynamicSongQuery library) {
        super(context, library.buildQuery(), false);
        mContext = context;
        mDynamicSongQuery = library;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View convertView = ((Activity) mContext).getLayoutInflater()
                .inflate(R.layout.item_dynamic_song_query, parent, false);

        convertView.setBackgroundResource(R.drawable.selector_white_orange_selected);

        ViewHolder viewHolder = new ViewHolder();
        viewHolder.title = (TextView) convertView.findViewById(R.id.tv_dsq_title);
        viewHolder.subtitle = (TextView) convertView.findViewById(R.id.tv_dsq_subtitle);
        viewHolder.image = (ImageView) convertView.findViewById(R.id.img_dsq_icon);

        convertView.setTag(viewHolder);

        return convertView;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        SongSelectItem songSelectItem = mDynamicSongQuery.fillSongSelectItem(cursor);
        viewHolder.title.setText(songSelectItem.getListTitle());
        viewHolder.subtitle.setText(songSelectItem.getListSubtitle());

        if (songSelectItem.getIcon() == null) {
            viewHolder.image.setVisibility(View.GONE);
        } else {
            viewHolder.image.setVisibility(View.VISIBLE);
            viewHolder.image.setImageBitmap(songSelectItem.getIcon());
        }
    }

    @Override
    public SongSelectItem getItem(int position) {
        Cursor c = getCursor();
        c.moveToPosition(position);
        return mDynamicSongQuery.fillSongSelectItem(c);
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        if (constraint.length() == 0) {
            return mDynamicSongQuery.buildQuery();
        } else {
            return mDynamicSongQuery.buildQuery(mDynamicSongQuery.getMatchesSubQuery(constraint.toString()));
        }
    }

    private class ViewHolder {
        ImageView image;

        TextView title;

        TextView subtitle;
    }
}