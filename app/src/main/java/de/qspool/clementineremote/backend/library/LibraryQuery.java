

package de.qspool.clementineremote.backend.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.database.DynamicSongQuery;

public class LibraryQuery extends DynamicSongQuery {

    public LibraryQuery(Context context) {
        super(context);
    }

    @Override
    protected String[] getSelectedFields() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);

        String grouping = sharedPreferences.getString(SharedPreferencesKeys.SP_LIBRARY_GROUPING,
                "artist-album");
        String[] selectedFields = new String[] {"artist", "title"};

        switch (grouping) {
            case "artist":
                selectedFields = new String[] {"artist", "title"};
                break;
            case "artist-album":
                selectedFields = new String[] {"artist", "album", "title"};
                break;
            case "albumartist-album":
                selectedFields = new String[] {"albumartist", "album", "title"};
                break;
            case "artist-year":
                selectedFields = new String[] {"artist", "year", "title"};
                break;
            case "album":
                selectedFields = new String[] {"album", "title"};
                break;
            case "genre-album":
                selectedFields = new String[] {"genre", "album", "title"};
                break;
            case "genre-artist-album":
                selectedFields = new String[] {"genre", "artist", "album", "title"};
                break;
        }
        return selectedFields;
    }

    @Override
    protected String getSorting() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);

        return sharedPreferences.getString(SharedPreferencesKeys.SP_LIBRARY_SORTING, "ASC");
    }

    @Override
    protected String getTable() {
        return LibraryDatabaseHelper.SONGS;
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        return new LibraryDatabaseHelper().openDatabase(SQLiteDatabase.OPEN_READONLY);
    }

    @Override
    public String getMatchesSubQuery(String match) {
        StringBuilder sb = new StringBuilder();

        sb.append("(SELECT * FROM ");
        sb.append(LibraryDatabaseHelper.SONGS_FTS);
        sb.append(" WHERE ");
        sb.append(LibraryDatabaseHelper.SONGS_FTS);
        sb.append(" MATCH \"");
        sb.append(match);
        sb.append("*");

        sb.append("\" ) ");

        return sb.toString();
    }
}
