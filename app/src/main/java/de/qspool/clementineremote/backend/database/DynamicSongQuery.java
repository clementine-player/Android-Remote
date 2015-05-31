

package de.qspool.clementineremote.backend.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import java.util.LinkedList;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.listener.OnSongSelectFinishedListener;

public abstract class DynamicSongQuery {

    protected Context mContext;

    private SQLiteDatabase mDatabase;

    private int mMaxLevels;

    private int mLevel;

    private String[] mSelectedFields;

    private String[] mSelection = new String[] {};

    private String mSort;

    private LinkedList<OnSongSelectFinishedListener> listeners = new LinkedList<>();

    abstract protected String[] getSelectedFields();
    abstract protected String getSorting();
    abstract protected String getTable();
    abstract protected SQLiteDatabase getReadableDatabase();
    abstract public String getMatchesSubQuery(String match);

    public DynamicSongQuery(Context context) {
        mContext = context;

        mSelectedFields = getSelectedFields();
        mSort = getSorting();

        mMaxLevels = mSelectedFields.length;
    }

    public void openDatabase() {
        mDatabase = getReadableDatabase();
    }

    public int getMaxLevels() {
        return mMaxLevels;
    }

    public Cursor buildQuery(String fromTable) {
        Cursor c1 = null;
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        query.append("ROWID as _id"); // _id for ListView

        for (String field : mSelectedFields) {
            query.append(", ");
            query.append(field);
        }

        query.append(", cast(filename as TEXT) "); // URL
        query.append(", artist, album ");

        query.append(" FROM ");
        query.append(fromTable);

        if (mSelection.length > 0) {
            query.append(" WHERE ");
            for (int i = 0; i < mSelection.length; i++) {
                query.append(mSelectedFields[i]);
                query.append(" = ? ");
                if (i < mSelection.length - 1)
                    query.append(" and ");
            }
        }

        if (isTitleLevel()) {
            query.append(" ORDER BY ");
            query.append(" album, disc, track ");
            query.append(mSort);
        } else {
            query.append(" GROUP BY ");
            query.append(mSelectedFields[mLevel]);
            query.append(" ORDER BY ");
            query.append(mSelectedFields[mLevel]);
            query.append(" ");
            query.append(mSort);
        }

        try {
            c1 = mDatabase.rawQuery(query.toString(), mSelection);
        } catch (Exception e) {
            System.out.println("DATABASE ERROR " + e);

        }
        return c1;
    }

    private int countItems(String[] selection) {
        int items = 0;

        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        query.append(" COUNT(DISTINCT(");
        query.append(mSelectedFields[selection.length]);
        query.append("))");

        query.append(" FROM ");
        query.append(getTable());

        if (selection.length > 0) {
            query.append(" WHERE ");
            for (int i = 0; i < selection.length; i++) {
                query.append(mSelectedFields[i]);
                query.append(" = ? ");
                if (i < selection.length - 1)
                    query.append(" and ");
            }
        }

        Cursor c = mDatabase.rawQuery(query.toString(), selection);

        if (c != null && c.moveToFirst()) {
            items = c.getInt(0);
            c.close();
        }

        return items;
    }

    public SongSelectItem fillSongSelectItem(Cursor c) {
        SongSelectItem item = new SongSelectItem();
        String unknownItem = mContext.getString(R.string.unknown);

        String[] values = new String[mSelectedFields.length];
        for (int i=0;i<mSelectedFields.length;i++) {
            values[i] = c.getString(i+1); // 0 is _id, selected fields begin at index 1!
        }

        // Get default fields
        String url = c.getString(mSelectedFields.length+1);
        String artist = c.getString(mSelectedFields.length+2);
        String album = c.getString(mSelectedFields.length+3);

        // Fill the selection and list items
        String[] selection = new String[mLevel+1];
        for (int i=0;i<=mLevel;i++) {
            selection[i] = values[i];
        }

        item.setSelection(selection);
        item.setUrl(url);

        if (selection[mLevel].isEmpty()) {
            item.setListTitle(unknownItem);
        } else {
            item.setListTitle(selection[mLevel]);
            if (mSelectedFields[mLevel].equals("year")) {
                item.setListTitle(selection[mLevel] + " - " + album);
            }
        }

        if (isTitleLevel()) {
            item.setListSubtitle((artist.isEmpty() ? unknownItem : artist)
                    + " / " + (album.isEmpty() ? unknownItem : album));
        } else {
            item.setListSubtitle(String.format(
                    mContext.getString(R.string.number_items),
                    countItems(item.getSelection())));
        }

        item.setLevel(mLevel);

        return item;
    }

    private boolean isTitleLevel() {
        return mLevel == mMaxLevels-1;
    }


    public void addOnLibrarySelectFinishedListener(
            OnSongSelectFinishedListener l) {
        listeners.add(l);
    }

    public Cursor buildQuery() {
        return buildQuery(getTable());
    }

    public void selectDataAsync() {
        new AsyncQueryTask().execute();
    }

    /**
     * Select the data and returns a list of items
     *
     * @return The list of items
     */
    public LinkedList<SongSelectItem> selectData() {
        LinkedList<SongSelectItem> itemList = new LinkedList<>();

        Cursor c = buildQuery();

        if (c != null && c.getCount() != 0) {
            c.moveToFirst();
            do {
                itemList.add(fillSongSelectItem(c));
            } while (c.moveToNext());
            c.close();
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

    private class AsyncQueryTask extends
            AsyncTask<Void, Void, LinkedList<SongSelectItem>> {

        @Override
        protected LinkedList<SongSelectItem> doInBackground(
                Void... params) {

            return selectData();
        }

        @Override
        protected void onPostExecute(LinkedList<SongSelectItem> items) {
            for (OnSongSelectFinishedListener l : listeners) {
                l.OnSongSelectFinished(items);
            }
        }
    }
}
