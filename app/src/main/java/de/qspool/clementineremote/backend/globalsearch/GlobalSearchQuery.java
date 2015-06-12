

package de.qspool.clementineremote.backend.globalsearch;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.database.DynamicSongQuery;
import de.qspool.clementineremote.backend.database.SongSelectItem;

public class GlobalSearchQuery extends DynamicSongQuery {

    public int mQueryId;

    public GlobalSearchQuery(Context context, int queryId) {
        super(context);
        mQueryId = queryId;
    }

    @Override
    protected String[] getSelectedFields() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);

        String grouping = sharedPreferences.getString(SharedPreferencesKeys.SP_LIBRARY_GROUPING,
                "artist-album");
        String[] selectedFields = new String[]{"search_provider", "artist", "title"};

        switch (grouping) {
            case "artist":
                selectedFields = new String[]{"search_provider", "artist", "title"};
                break;
            case "artist-album":
                selectedFields = new String[]{"search_provider", "artist", "album", "title"};
                break;
            case "artist-year":
                selectedFields = new String[]{"search_provider", "artist", "year", "title"};
                break;
            case "album":
                selectedFields = new String[]{"search_provider", "album", "title"};
                break;
            case "genre-album":
                selectedFields = new String[]{"search_provider", "genre", "album", "title"};
                break;
            case "genre-artist-album":
                selectedFields = new String[]{"search_provider", "genre", "artist", "album",
                        "title"};
                break;
        }
        return selectedFields;
    }

    @Override
    protected String getHiddenWhere() {
        return " global_search_id = " + mQueryId;
    }

    @Override
    protected String getSorting() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);

        return sharedPreferences.getString(SharedPreferencesKeys.SP_LIBRARY_SORTING, "ASC");
    }

    @Override
    protected String getTable() {
        return GlobalSearchDatabaseHelper.TABLE_NAME;
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        return new GlobalSearchDatabaseHelper(mContext).getReadableDatabase();
    }

    @Override
    public String getMatchesSubQuery(String match) {
        return "";
    }

    @Override
    public SongSelectItem fillSongSelectItem(Cursor c) {
        SongSelectItem item = super.fillSongSelectItem(c);

        if (item.getLevel() == 0) {
            Bitmap bitmap = GlobalSearchManager.getInstance().getGlobalSearchProviderIconStore()
                    .getProviderIcon(item.getListTitle());
            item.setIcon(bitmap);
        }

        return item;
    }
}
