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

package de.qspool.clementineremote.backend.player;

import java.io.File;
import java.util.LinkedList;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.event.OnLibrarySelectFinishedListener;
import de.qspool.clementineremote.backend.player.MyLibraryItem.Level;

public class MyLibrary extends
		AsyncTask<MyLibraryItem.Level, Void, LinkedList<MyLibraryItem>> {
	private final static String LIBRARY_DB_FILE_NAME = "library.db";

	// Table names
	private final static String SONGS_FTS = "songs_fts";
	private final static String SONGS_ARTIST = "songs_artist";
	private final static String SONGS_ALBUM = "songs_album";
	private final static String SONGS_TITLE = "songs_title";

	// Select values
	private String mSelArtist = "";
	private String mSelAlbum = "";

	private SQLiteDatabase db;
	private Context mContext;
	LinkedList<OnLibrarySelectFinishedListener> listeners = new LinkedList<OnLibrarySelectFinishedListener>();

	public MyLibrary(Context context) {
		mContext = context;
	}

	public void addOnLibrarySelectFinishedListener(
			OnLibrarySelectFinishedListener l) {
		listeners.add(l);
	}

	public boolean databaseExists() {
		return getLibraryDb().exists();
	}

	/**
	 * Get the file path to the library database file. The file is stored on the
	 * external storage in die android dir. Filename is library.db
	 * 
	 * @return The path incl. filename to the database file
	 */
	public File getLibraryDb() {
		return new File(App.mApp.getExternalFilesDir(null),
				LIBRARY_DB_FILE_NAME);
	}

	/**
	 * Optimize library table (table songs). We create a fts virtual table
	 * songs_fts for full text search. The following indices will be created:
	 * songs_artist (artist) songs_album (artist, album) songs_title (artist,
	 * album, title)
	 */
	public void optimizeTable() {
		openDatabase();
		// FTS Table for search
		db.execSQL("CREATE VIRTUAL TABLE " + SONGS_FTS
				+ " USING fts3(select * from songs);");

		// Indices for fragment
		db.execSQL("CREATE INDEX " + SONGS_ARTIST + " ON songs (artist);");
		db.execSQL("CREATE INDEX " + SONGS_ALBUM + " ON songs (artist, album);");
		db.execSQL("CREATE INDEX " + SONGS_TITLE
				+ " ON songs (artist, album, title);");
		
		closeDatabase();
	}

	public void getArtists() {
		this.execute(MyLibraryItem.Level.ARTIST);
	}

	public void getAlbums(String artist) {
		mSelArtist = artist;
		this.execute(MyLibraryItem.Level.ALBUM);
	}

	public void getTitles(String artist, String album) {
		mSelArtist = artist;
		mSelAlbum = album;
		this.execute(MyLibraryItem.Level.TITLE);
	}
	
	public void getAllTitlesFromArtist(String artist) {
		mSelArtist = artist;
		mSelAlbum  = "";
		this.execute(MyLibraryItem.Level.TITLE);
	}

	/**
	 * How many albums does one artist have in the library?
	 * 
	 * @param artist
	 *            The artist for which the count should be checked
	 * @return The number of albums
	 */
	private int getAlbumCountForArtist(String artist) {
		Cursor c = db
				.rawQuery(
						"SELECT count(distinct(album)) FROM songs where artist = ?",
						new String[] { artist });
		c.moveToFirst();
		int count = c.getInt(0);
		c.close();
		return count;
	}

	/**
	 * How many albums does one artist have in the library?
	 * 
	 * @param artist
	 *            The artist for which the count should be checked
	 * @return The number of albums
	 */
	private int getTitleCountForAlbum(String artist, String album) {
		Cursor c = db
				.rawQuery(
						"SELECT count(title) FROM songs where artist = ? and album = ?",
						new String[] { artist, album });
		c.moveToFirst();
		int count = c.getInt(0);
		c.close();
		return count;
	}

	private void openDatabase() {
		db = SQLiteDatabase.openDatabase(getLibraryDb().getAbsolutePath(),
				null, SQLiteDatabase.OPEN_READWRITE);
	}

	private void closeDatabase() {
		db.close();
	}

	private Cursor buildSelectSql(Level level) {
		Cursor c1 = null;
		String query = "";
		try {
			switch (level) {
			case ARTIST:
				query = "SELECT artist from songs where artist <> \" \" group by artist";
				c1 = db.rawQuery(query, null);
				break;
			case ALBUM:
				query = "SELECT artist, album from songs where artist = ? group by album";
				c1 = db.rawQuery(query, new String[] { mSelArtist });
				break;
			case TITLE:
				if (mSelAlbum.isEmpty()) {
					query = "SELECT artist, album, title, cast(filename as TEXT) FROM songs where artist = ?";
					c1 = db.rawQuery(query, new String[] { mSelArtist });
				} else {
					query = "SELECT artist, album, title, cast(filename as TEXT) FROM songs where artist = ? and album = ?";
					c1 = db.rawQuery(query, new String[] { mSelArtist, mSelAlbum });
				}
				break;
			default:
				break;
			}
		} catch (Exception e) {
			System.out.println("DATABASE ERROR " + e);

		}
		return c1;
	}

	/**
	 * Select the data and returns a list of items
	 * 
	 * @param c
	 *            Must not be null and have elements!
	 * @param level
	 *            The level we are operating at
	 * @return The list of items
	 */
	private LinkedList<MyLibraryItem> selectData(Cursor c, Level level) {
		LinkedList<MyLibraryItem> itemList = new LinkedList<MyLibraryItem>();

		c.moveToFirst();

		do {
			MyLibraryItem item = new MyLibraryItem();

			switch (level) {
			case ARTIST:
				item.setText(c.getString(0));
				item.setSubtext(String.format(
						mContext.getString(R.string.library_no_albums),
						getAlbumCountForArtist(c.getString(0))));
				item.setArtist(c.getString(0));
				break;
			case ALBUM:
				item.setText(c.getString(1));
				item.setSubtext(String.format(
						mContext.getString(R.string.library_no_tracks),
						getTitleCountForAlbum(c.getString(0), c.getString(1))));
				item.setArtist(c.getString(0));
				item.setAlbum(c.getString(1));
				break;
			case TITLE:
				item.setText(c.getString(2));
				item.setSubtext(c.getString(1) + " / " + c.getString(0));
				item.setUrl(c.getString(3));
				item.setArtist(c.getString(0));
				item.setAlbum(c.getString(1));
				item.setTitle(c.getString(2));
				break;
			default:
				break;
			}
			item.setLevel(level);
			itemList.add(item);
		} while (c.moveToNext());

		return itemList;
	}

	@Override
	protected LinkedList<MyLibraryItem> doInBackground(
			MyLibraryItem.Level... params) {
		openDatabase();

		LinkedList<MyLibraryItem> itemList = new LinkedList<MyLibraryItem>();

		Cursor c = buildSelectSql(params[0]);

		if (c != null && c.getCount() != 0) {
			itemList = selectData(c, params[0]);
		}

		closeDatabase();
		return itemList;
	}

	@Override
	protected void onPostExecute(LinkedList<MyLibraryItem> items) {
		for (OnLibrarySelectFinishedListener l : listeners) {
			l.OnLibrarySelectFinished(items);
		}
	}
}
