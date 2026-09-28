

package de.qspool.clementineremote.backend.globalsearch;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.database.DynamicSongQuery;
import de.qspool.clementineremote.ui.search.SearchSectionsKt;

public class GlobalSearchQuery extends DynamicSongQuery {

    public int mQueryId;

    public GlobalSearchQuery(Context context, int queryId) {
        super(context);
        mQueryId = queryId;
    }

    /**
     * Results open by where they came from, then album artist, album and song, whatever the
     * library's grouping: artists and albums of the results are grouped this way.
     */
    @Override
    protected String[] getSelectedFields() {
        return new String[]{"search_provider", SearchSectionsKt.GROUP_ARTIST, "album", "title"};
    }

    @Override
    protected String getHiddenWhere() {
        return " global_search_id = " + mQueryId;
    }

    @Override
    protected String getSorting() {
        SharedPreferences sharedPreferences = App.getPreferences();

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
}
