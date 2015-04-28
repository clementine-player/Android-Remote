

package de.qspool.clementineremote.backend.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import java.util.LinkedList;

import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.library.groupings.LibraryGroupArtistAlbum;
import de.qspool.clementineremote.backend.listener.OnLibrarySelectFinishedListener;

public abstract class LibraryGroup {

    protected LibraryAlbumOrder mLibraryAlbumOrder = LibraryAlbumOrder.ALPHABET;

    protected Context mContext;

    protected SQLiteDatabase mDatabase;

    protected int mLevel;

    protected String[] mSelection;

    private LinkedList<OnLibrarySelectFinishedListener> listeners = new LinkedList<>();

    public static LibraryGroup getSelectedLibraryGroup(Context context) {
        // This order has to match R.array.pref_library_grouping !
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        int grouping = Integer.valueOf(sharedPreferences.getString(SharedPreferencesKeys.SP_LIBRARY_GROUPING, "0"));

        switch (grouping) {
            case 0:
                return new LibraryGroupArtistAlbum(context);
        }

        return new LibraryGroupArtistAlbum(context);
    }

    public LibraryGroup(Context context) {
        mContext = context;
        mDatabase = new LibraryDatabaseHelper().openDatabase(SQLiteDatabase.OPEN_READONLY);

        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        mLibraryAlbumOrder = LibraryAlbumOrder.valueOf(
                sharedPreferences.getString(SharedPreferencesKeys.SP_LIBRARY_ALBUM_ORDER,
                        LibraryAlbumOrder.ALPHABET.toString()).toUpperCase());
    }

    abstract public int getMaxLevels();

    abstract public Cursor buildQuery(String fromTable);

    abstract public LibrarySelectItem fillLibrarySelectItem(Cursor c);


    public void addOnLibrarySelectFinishedListener(
            OnLibrarySelectFinishedListener l) {
        listeners.add(l);
    }

    public Cursor buildQuery() {
        return buildQuery(LibraryDatabaseHelper.SONGS);
    }

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

    public void selectDataAsync() {
        new AsyncLibraryTask().execute();
    }

    /**
     * Select the data and returns a list of items
     *
     * @return The list of items
     */
    public LinkedList<LibrarySelectItem> selectData() {
        LinkedList<LibrarySelectItem> itemList = new LinkedList<>();

        Cursor c = buildQuery();

        if (c != null && c.getCount() != 0) {
            c.moveToFirst();
            do {
                itemList.add(fillLibrarySelectItem(c));
            } while (c.moveToNext());
        }

        return itemList;
    }

    public int getLevel() {
        return mLevel;
    }

    public void setLevel(int level) {
        mLevel = level;
    }

    public String[] getSelection() {
        return mSelection;
    }

    public void setSelection(String[] selection) {
        mSelection = selection;
    }

    private class AsyncLibraryTask extends
            AsyncTask<Void, Void, LinkedList<LibrarySelectItem>> {

        @Override
        protected LinkedList<LibrarySelectItem> doInBackground(
                Void... params) {

            return selectData();
        }

        @Override
        protected void onPostExecute(LinkedList<LibrarySelectItem> items) {
            for (OnLibrarySelectFinishedListener l : listeners) {
                l.OnLibrarySelectFinished(items);
            }
        }
    }
}
