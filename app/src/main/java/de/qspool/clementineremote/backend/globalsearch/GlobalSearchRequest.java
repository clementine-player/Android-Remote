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

package de.qspool.clementineremote.backend.globalsearch;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer;

public class GlobalSearchRequest {
    private int mId;

    private GlobalSearchDatabaseHelper mGlobalSearchDatabaseHelper;

    private ClementineRemoteProtocolBuffer.GlobalSearchStatus mStatus;

    public GlobalSearchRequest(int id, GlobalSearchDatabaseHelper db) {
        mId = id;
        mGlobalSearchDatabaseHelper = db;
        mStatus = ClementineRemoteProtocolBuffer.GlobalSearchStatus.GlobalSearchStarted;
    }

    public int getId() {
        return mId;
    }

    public ClementineRemoteProtocolBuffer.GlobalSearchStatus getStatus() {
        return mStatus;
    }

    public void setStatus(
            ClementineRemoteProtocolBuffer.GlobalSearchStatus status) {
        mStatus = status;
    }

    public void addSearchResults(
            ClementineRemoteProtocolBuffer.ResponseGlobalSearch searchResult) {

        SQLiteDatabase db = mGlobalSearchDatabaseHelper.getWritableDatabase();
        db.beginTransaction();

        for (ClementineRemoteProtocolBuffer.SongMetadata song : searchResult.getSongMetadataList()) {
            ContentValues contentValues = new ContentValues();

            contentValues.put("global_search_id", getId());
            contentValues.put("search_query", searchResult.getQuery());
            contentValues.put("search_provider", searchResult.getSearchProvider());

            contentValues.put("title", song.getTitle());
            contentValues.put("album", song.getAlbum());
            contentValues.put("artist", song.getArtist());
            contentValues.put("albumartist", song.getAlbumartist());
            contentValues.put("track", song.getTrack());
            contentValues.put("disc", song.getDisc());
            contentValues.put("pretty_year", song.getPrettyYear());
            contentValues.put("genre", song.getGenre());
            contentValues.put("pretty_length", song.getPrettyLength());
            contentValues.put("filename", song.getUrl()); // filename is url
            contentValues.put("is_local", song.getIsLocal() ? 1 : 0);
            contentValues.put("filesize", song.getFileSize());
            contentValues.put("rating", song.getRating());

            db.insert(GlobalSearchDatabaseHelper.TABLE_NAME, null, contentValues);
        }

        db.setTransactionSuccessful();
        db.endTransaction();
        db.close();
    }
}
