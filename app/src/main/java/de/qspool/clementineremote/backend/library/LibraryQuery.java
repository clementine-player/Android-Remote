

package de.qspool.clementineremote.backend.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.database.DynamicSongQuery;

public class LibraryQuery extends DynamicSongQuery {

    /** Clementine's library groups by album artist, then album, unless told otherwise. */
    public static final String DEFAULT_GROUPING = "albumartist-album";

    /**
     * The SQL the library's songs are grouped by for {@code field}. As Clementine groups them,
     * songs it finds are on compilations (a various artists album) are under
     * {@link DynamicSongQuery#VARIOUS_ARTISTS}, not their artist, and an album artist is the
     * artist where a song has none.
     */
    public static String expression(String field) {
        switch (field) {
            case "artist":
                return "CASE WHEN effective_compilation = 1 THEN char(1) ELSE artist END";
            case "albumartist":
                return "CASE WHEN effective_compilation = 1 THEN char(1) "
                        + "WHEN albumartist <> '' THEN albumartist ELSE artist END";
            default:
                return field;
        }
    }

    public LibraryQuery(Context context) {
        super(context);
    }

    @Override
    protected String[] getSelectedFields() {
        SharedPreferences sharedPreferences = App.getPreferences();

        String grouping = sharedPreferences.getString(SharedPreferencesKeys.SP_LIBRARY_GROUPING,
                DEFAULT_GROUPING);
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
        SharedPreferences sharedPreferences = App.getPreferences();

        return sharedPreferences.getString(SharedPreferencesKeys.SP_LIBRARY_SORTING, "ASC");
    }

    @Override
    protected String getExpression(String field) {
        return expression(field);
    }

    @Override
    protected String getTable() {
        return LibraryDatabaseHelper.SONGS;
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        return new LibraryDatabaseHelper().openDatabase(SQLiteDatabase.OPEN_READONLY);
    }
}
