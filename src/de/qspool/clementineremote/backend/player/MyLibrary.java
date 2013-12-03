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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.event.OnLibrarySelectFinishedListener;

public class MyLibrary extends
		AsyncTask<Integer, Void, LinkedList<MyLibraryItem>> {
	private final static String LIBRARY_DB_FILE_NAME = "library.db";

	// Table names
	private final static String SONGS_FTS = "songs_fts";
	private final static String SONGS_ARTIST = "songs_artist";
	private final static String SONGS_ALBUM = "songs_album";
	private final static String SONGS_TITLE = "songs_title";
	
	// Field indicies
	public final static int IDX_ID     = 0;
	public final static int IDX_LEVEL  = 1;
	public final static int IDX_ARTIST = 2;
	public final static int IDX_ALBUM  = 3;
	public final static int IDX_TITLE  = 4;
	public final static int IDX_URL    = 5;
	public final static int IDX_COUNT  = 6;
	
	// Levels
	public final static int LVL_ARTIST = 0;
	public final static int LVL_ALBUM  = 1;
	public final static int LVL_TITLE  = 2;

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
	 * Check if the library file is from the currenly connected system. If not, we obviously cannot
	 * add songs from this db to Clementine. So here we delete the wrong library file.
	 * @return true if a database file existed and the current Clementine connection has a different
	 * 		   ip that the ip from the library. False otherwise
	 */
	public boolean removeDatabaseIfFromOtherClementine() {
		SharedPreferences prefs =  PreferenceManager.getDefaultSharedPreferences(mContext);
		String libraryClementine = prefs.getString(App.SP_LIBRARY_IP, "");
		String currentClementine = prefs.getString(App.SP_KEY_IP, "");
		
		if (libraryClementine.equals(currentClementine)) {
			return false;
		} else {
			// Save the current library ip
			SharedPreferences.Editor edit = prefs.edit();
			edit.putString(App.SP_LIBRARY_IP, currentClementine);
			edit.commit();
			// Delete the file if exists
			return getLibraryDb().delete();
		}
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
		
		// Remove unavailable songs
		db.execSQL("DELETE from SONGS where unavailable <> 0");
		
		StringBuilder sb = new StringBuilder();
		Cursor c = db.rawQuery("PRAGMA table_info(songs);", new String[] {});
		
		if (c != null && c.moveToFirst()) {
			do {
				if (sb.length() != 0)
					sb.append(", ");
				sb.append(c.getString(1));
			} while (c.moveToNext());
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
		
		closeDatabase();
	}

	public void getArtistsAsync() {
		this.execute(LVL_ARTIST);
	}

	public void getAlbumsAsync(String artist) {
		mSelArtist = artist;
		this.execute(LVL_ALBUM);
	}

	public void getTitlesAsync(String artist, String album) {
		mSelArtist = artist;
		mSelAlbum = album;
		this.execute(LVL_TITLE);
	}
	
	public void getAllTitlesFromArtistAsync(String artist) {
		mSelArtist = artist;
		mSelAlbum  = "";
		this.execute(LVL_TITLE);
	}
	
	public Cursor getArtists() {
		return buildSelectSql(LVL_ARTIST);
	}
	
	public Cursor getAlbums(String artist) {
		mSelArtist = artist;
		return buildSelectSql(LVL_ALBUM);
	}
	
	public Cursor getTitles(String artist, String album) {
		mSelArtist = artist;
		mSelAlbum = album;
		return buildSelectSql(LVL_TITLE);
	}

	/**
	 * How many albums does one artist have in the library?
	 * 
	 * @param artist
	 *            The artist for which the count should be checked
	 * @return The number of albums
	 */
	public int getAlbumCountForArtist(String artist) {
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
	public int getTitleCountForAlbum(String artist, String album) {
		Cursor c = db
				.rawQuery(
						"SELECT count(title) FROM songs where artist = ? and album = ?",
						new String[] { artist, album });
		c.moveToFirst();
		int count = c.getInt(0);
		c.close();
		return count;
	}

	public void openDatabase() {
		db = SQLiteDatabase.openDatabase(getLibraryDb().getAbsolutePath(),
				null, SQLiteDatabase.OPEN_READWRITE);
	}

	public void closeDatabase() {
		if (db != null && db.isOpen()) {
			db.close();
		}
	}
	
	public String getMatchesSubQuery(int level, String match) {
		StringBuilder sb = new StringBuilder();
		
		sb.append("(SELECT * FROM ");
		sb.append(SONGS_FTS);
		sb.append(" WHERE ");
		
		switch (level) {
		case LVL_ARTIST:
			sb.append("artist");
			break;
		case LVL_ALBUM:
			sb.append("album");
			break;
		case LVL_TITLE:
			sb.append("title");
			break;
		default:
			break;
		}
		sb.append(" MATCH \"");
		sb.append(match);
		sb.append("*\")");
		
		return sb.toString();
	}
	
	public Cursor buildSelectSql(int level) {
		return buildSelectSql(level, "songs");
	}

	public Cursor buildSelectSql(int level, String fromTable) {
		Cursor c1 = null;
		String query = "SELECT ROWID as _id, " + level + ", artist, album, title, cast(filename as TEXT) FROM " + fromTable + " ";
		try {
			switch (level) {
			case LVL_ARTIST:
				query += "WHERE artist <> '' group by artist order by artist";
				c1 = db.rawQuery(query, null);
				break;
			case LVL_ALBUM:
				query += "WHERE artist = ? group by album order by album";
				c1 = db.rawQuery(query, new String[] { mSelArtist });
				break;
			case LVL_TITLE:
				if (mSelAlbum.length() == 0) {
					query += "WHERE artist = ? order by album, disc, track";
					c1 = db.rawQuery(query, new String[] { mSelArtist });
				} else {
					query += "WHERE artist = ? and album = ? order by disc, track";
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
	private LinkedList<MyLibraryItem> selectData(Cursor c, int level) {
		LinkedList<MyLibraryItem> itemList = new LinkedList<MyLibraryItem>();

		c.moveToFirst();

		do {
			itemList.add(createMyLibraryItem(c, level));
		} while (c.moveToNext());

		return itemList;
	}
	
	public MyLibraryItem createMyLibraryItem(Cursor c, int level) {
		MyLibraryItem item = new MyLibraryItem();

		switch (level) {
		case LVL_ARTIST:
			item.setArtist(c.getString(IDX_ARTIST));
			break;
		case LVL_ALBUM:
			item.setArtist(c.getString(IDX_ARTIST));
			item.setAlbum(c.getString(IDX_ALBUM));
			break;
		case LVL_TITLE:
			item.setUrl(c.getString(IDX_URL));
			item.setArtist(c.getString(IDX_ARTIST));
			item.setAlbum(c.getString(IDX_ALBUM));
			item.setTitle(c.getString(IDX_TITLE));
			break;
		default:
			break;
		}
		item.setLevel(level);
		return item;
	}

	@Override
	protected LinkedList<MyLibraryItem> doInBackground(
			Integer... params) {
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
