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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class GlobalSearchDatabaseHelper extends SQLiteOpenHelper {

    public final static String TABLE_NAME = "GlobalSearchResults";

    private final static String DATABASE_NAME = "GlobalSearchResults.db";

    private final static int DATABASE_VERSION = 2;

    public GlobalSearchDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE "
                        + TABLE_NAME
                        + "( global_search_id INTEGER NOT NULL,"
                        + "  search_query TEXT,"
                        + "  search_provider TEXT,"
                        + "  title TEXT,"
                        + "  album TEXT,"
                        + "  artist TEXT,"
                        + "  albumartist TEXT,"
                        + "  track INTEGER,"
                        + "  disc INTEGER,"
                        + "  pretty_year TEXT,"
                        + "  year INTEGER, "
                        + "  genre TEXT,"
                        + "  pretty_length TEXT,"
                        + "  filename TEXT NOT NULL,"
                        + "  is_local INTEGER NOT NULL,"
                        + "  filesize INTEGER NOT NULL,"
                        + "  rating REAL,"
                        + "  url TEXT NOT NULL DEFAULT 0"
                        + ")"
        );

        db.execSQL("CREATE INDEX idx_global_search_id ON " + TABLE_NAME + " (global_search_id);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE " + TABLE_NAME);
        onCreate(db);
    }

    public void deleteAll() {
        getWritableDatabase().execSQL("DELETE FROM " + TABLE_NAME);
    }
}
