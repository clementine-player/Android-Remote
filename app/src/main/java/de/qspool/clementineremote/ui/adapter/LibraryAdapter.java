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
import android.widget.TextView;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.library.LibraryGroup;
import de.qspool.clementineremote.backend.library.LibrarySelectItem;

/**
 * Class is used for displaying the song data
 */
public class LibraryAdapter extends CursorAdapter implements Filterable {

    private Context mContext;

    private LibraryGroup mLibrary;


    public LibraryAdapter(Context context, LibraryGroup library) {
        super(context, library.buildQuery(), false);
        mContext = context;
        mLibrary = library;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View convertView = ((Activity) mContext).getLayoutInflater()
                .inflate(R.layout.item_library, parent, false);

        convertView.setBackgroundResource(R.drawable.selector_white_orange_selected);

        LibraryViewHolder libraryViewHolder = new LibraryViewHolder();
        libraryViewHolder.title = (TextView) convertView.findViewById(R.id.tv_lib_title);
        libraryViewHolder.subtitle = (TextView) convertView.findViewById(R.id.tv_lib_subtitle);

        convertView.setTag(libraryViewHolder);

        return convertView;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        LibraryViewHolder libraryViewHolder = (LibraryViewHolder) view.getTag();

        LibrarySelectItem librarySelectItem = mLibrary.fillLibrarySelectItem(cursor);
        libraryViewHolder.title.setText(librarySelectItem.getListTitle());
        libraryViewHolder.subtitle.setText(librarySelectItem.getListSubtitle());
    }

    @Override
    public LibrarySelectItem getItem(int position) {
        Cursor c = getCursor();
        c.moveToPosition(position);
        return mLibrary.fillLibrarySelectItem(c);
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        if (constraint.length() == 0) {
            return mLibrary.buildQuery();
        } else {
            return mLibrary.buildQuery(mLibrary.getMatchesSubQuery(constraint.toString()));
        }
    }

    private class LibraryViewHolder {
        TextView title;

        TextView subtitle;
    }
}