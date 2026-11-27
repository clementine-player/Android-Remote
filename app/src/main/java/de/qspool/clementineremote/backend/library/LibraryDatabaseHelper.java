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

package de.qspool.clementineremote.backend.library;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.File;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;

public class LibraryDatabaseHelper {

    private final static String LIBRARY_DB_FILE_NAME = "library.db";

    // Table names
    public final static String SONGS = "songs";
    
    public final static String SONGS_FTS = "songs_fts";

    public final static String SONGS_ARTIST = "songs_artist";

    public final static String SONGS_ALBUM = "songs_album";

    public final static String SONGS_TITLE = "songs_title";

    private SQLiteDatabase db;

    public SQLiteDatabase openDatabase(int flags) {
        return openDatabase(getLibraryDb(), flags);
    }

    private SQLiteDatabase openDatabase(File file, int flags) {
        db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, flags);

        return db;
    }

    public void closeDatabase() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    public boolean checkConsistency() {
        boolean dbConsistent = true;

        // Check the consistency of the database
        if (databaseExists()) {
            try {
                openDatabase(SQLiteDatabase.OPEN_READWRITE);
                dbConsistent = databaseIntegrityOk();
            } catch (SQLiteException e) {
                dbConsistent = false;
            } finally {
                // Close db even if openDatabase/integrity check throws on a corrupt file.
                closeDatabase();
            }

            if (!dbConsistent) {
                getLibraryDb().delete();
            }
        }

        return dbConsistent;
    }

    private static File storageDir() {
        // Fall back to internal filesDir when external storage is unavailable so
        // new File(..., LIBRARY_DB_FILE_NAME) never resolves as a relative path.
        File external = App.getApp().getExternalFilesDir(null);
        return external != null ? external : App.getApp().getFilesDir();
    }

    /**
     * Get the file path to the library database file. The file is stored on external
     * storage (or app-private files dir if unavailable). Filename is library.db
     *
     * @return The path incl. filename to the database file
     */
    public File getLibraryDb() {
        return new File(storageDir(), LIBRARY_DB_FILE_NAME);
    }

    /**
     * Where a library being synced is written, until it's complete and replaces
     * {@link #getLibraryDb()}. Until then, the library on the phone stays usable.
     */
    public File getPartialLibraryDb() {
        return new File(storageDir(), LIBRARY_DB_FILE_NAME + ".part");
    }

    /**
     * Check if the library file is from the currenly connected system. If not, we obviously cannot
     * add songs from this db to Clementine. So here we delete the wrong library file.
     *
     * @return true if a database file existed and the current Clementine connection has a different
     * ip that the ip from the library. False otherwise
     */
    public boolean removeDatabaseIfFromOtherClementine() {
        SharedPreferences prefs = App.getPreferences();
        String libraryClementine = prefs.getString(SharedPreferencesKeys.SP_LIBRARY_IP, "");
        String currentClementine = prefs.getString(SharedPreferencesKeys.SP_KEY_IP, "");

        if (libraryClementine.equals(currentClementine)) {
            return false;
        } else {
            // Save the current library ip
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(SharedPreferencesKeys.SP_LIBRARY_IP, currentClementine);
            edit.apply();
            // Delete the file if exists
            return getLibraryDb().delete();
        }
    }

    /**
     * Optimize library table (table songs). We create a fts virtual table
     * songs_fts for full text search. The following indices will be created:
     * songs_artist (artist) songs_album (artist, album) songs_title (artist,
     * album, title)
     */
    public void optimizeTable() {
        optimizeTable(getLibraryDb());
    }

    /** Prepares a library just received in {@code file} for browsing and searching. */
    public void optimizeTable(File file) {
        openDatabase(file, SQLiteDatabase.OPEN_READWRITE);
        try {
            // Remove unavailable songs
            db.execSQL("DELETE from SONGS where unavailable <> 0");

            StringBuilder sb = new StringBuilder();
            Cursor c = db.rawQuery("PRAGMA table_info(songs);", new String[]{});
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        do {
                            if (sb.length() != 0) {
                                sb.append(", ");
                            }
                            sb.append(c.getString(1));
                        } while (c.moveToNext());
                    }
                } finally {
                    c.close();
                }
            }

            // Fail explicitly before building malformed fts3() SQL if the songs table is absent.
            if (sb.length() == 0) {
                throw new SQLiteException("Missing songs table in library database");
            }

            // FTS Table for search
            db.execSQL("CREATE VIRTUAL TABLE " + SONGS_FTS
                    + " USING fts3(" + sb.toString() + ");");
            db.execSQL("INSERT INTO " + SONGS_FTS + " SELECT * FROM songs");

            // Indices for fragment
            db.execSQL("CREATE INDEX " + SONGS_ARTIST + " ON songs (artist);");
            db.execSQL("CREATE INDEX " + SONGS_ALBUM + " ON songs (artist, album);");
            db.execSQL("CREATE INDEX " + SONGS_TITLE
                    + " ON songs (artist, album, title);");
        } finally {
            // Always close db so the caller can safely delete or rename the partial file on error.
            closeDatabase();
        }
    }

    public boolean databaseExists() {
        return getLibraryDb().exists();
    }

    private boolean databaseIntegrityOk() {
        boolean result = false;
        try {
            Cursor c = db.rawQuery("PRAGMA main.integrity_check(1)", null);
            if (c != null) {
                // Always close cursor and guard empty/null rows on malformed DB files.
                try {
                    if (c.moveToFirst()) {
                        String value = c.getString(0);
                        result = value != null && value.equalsIgnoreCase("ok");
                    }
                } finally {
                    c.close();
                }
            }
        } catch (SQLiteException e) {
            result = false;
        }
        return result;
    }

}
