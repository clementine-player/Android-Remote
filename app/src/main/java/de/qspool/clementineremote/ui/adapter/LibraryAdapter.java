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
import de.qspool.clementineremote.backend.player.MyLibrary;
import de.qspool.clementineremote.backend.player.MyLibraryItem;

/**
 * Class is used for displaying the song data
 */
public class LibraryAdapter extends CursorAdapter implements Filterable {

    private Context mContext;

    private MyLibrary mLibrary;

    private int mLevel;

    private String mUnknownItem;

    public LibraryAdapter(Context context, Cursor c, MyLibrary library, int level) {
        super(context, c, false);
        mContext = context;
        mLibrary = library;
        mLevel = level;
        mUnknownItem = mContext.getString(R.string.library_unknown_item);
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

        switch (mLevel) {
            case MyLibrary.LVL_ARTIST:
                if (cursor.getString(MyLibrary.IDX_ARTIST).isEmpty()) {
                    libraryViewHolder.title.setText(mUnknownItem);
                } else {
                    libraryViewHolder.title.setText(cursor.getString(MyLibrary.IDX_ARTIST));
                }
                libraryViewHolder.subtitle.setText(String.format(
                        mContext.getString(R.string.library_no_albums),
                        mLibrary.getAlbumCountForArtist(cursor.getString(MyLibrary.IDX_ARTIST))));
                break;
            case MyLibrary.LVL_ALBUM:
                if (cursor.getString(MyLibrary.IDX_ALBUM).isEmpty()) {
                    libraryViewHolder.title.setText(mUnknownItem);
                } else {
                    libraryViewHolder.title.setText(cursor.getString(MyLibrary.IDX_ALBUM));
                }
                libraryViewHolder.subtitle.setText(String.format(
                        mContext.getString(R.string.library_no_tracks),
                        mLibrary.getTitleCountForAlbum(cursor.getString(MyLibrary.IDX_ARTIST),
                                cursor.getString(MyLibrary.IDX_ALBUM))));
                break;
            case MyLibrary.LVL_TITLE:
                if (cursor.getString(MyLibrary.IDX_TITLE).isEmpty()) {
                    String url = cursor.getString(MyLibrary.IDX_URL);
                    String filename = url.substring(url.lastIndexOf("/") + 1);
                    libraryViewHolder.title.setText(filename);
                } else {
                    libraryViewHolder.title.setText(cursor.getString(MyLibrary.IDX_TITLE));
                }
                String artist = cursor.getString(MyLibrary.IDX_ARTIST);
                String album = cursor.getString(MyLibrary.IDX_ALBUM);
                libraryViewHolder.subtitle.setText((artist.isEmpty() ? mUnknownItem : artist)
                        + " / " + (album.isEmpty() ? mUnknownItem : album));
                break;
            default:
                break;
        }
    }

    @Override
    public MyLibraryItem getItem(int position) {
        Cursor c = getCursor();
        c.moveToPosition(position);
        return mLibrary.createMyLibraryItem(c, c.getInt(MyLibrary.IDX_LEVEL));
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        if (constraint.length() == 0) {
            return mLibrary.buildSelectSql(mLevel);
        } else {
            return mLibrary.buildSelectSql(mLevel,
                    mLibrary.getMatchesSubQuery(constraint.toString()));
        }
    }

    private class LibraryViewHolder {

        TextView title;

        TextView subtitle;
    }
}