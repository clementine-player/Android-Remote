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

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MyLibraryItem.Level;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class MyLibrary {
	private final static String LIBRARY_DB_FILE_NAME = "library.db";
	
	// Table names
	private final static String SONGS_FTS = "songs_fts";
	private final static String SONGS_ARTIST = "songs_artist";
	private final static String SONGS_ALBUM = "songs_album";
	private final static String SONGS_TITLE = "songs_title";
	
	private SQLiteDatabase db;
	
	private Context mContext;
	
	public MyLibrary(Context context) {
		mContext = context;
	}
	
	public void openDatabase() {
		db = SQLiteDatabase.openDatabase(getLibraryDb().getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
	}
	
	public void closeDatabase() {
		db.close();
	}
	
	public boolean databaseExists() {
		return getLibraryDb().exists();
	}
	
	/**
	 * Get the file path to the library database file. The file is stored on the external storage
	 * in die android dir. Filename is library.db
	 * @return The path incl. filename to the database file
	 */
	public File getLibraryDb() {
		return new File(App.mApp.getExternalFilesDir(null), LIBRARY_DB_FILE_NAME);
	}
	
	/**
	 * Optimize library table (table songs). We create a fts virtual table songs_fts for full text search.
	 * The following indices will be created:
	 * 		songs_artist (artist)
	 * 		songs_album  (artist, album)
	 * 		songs_title  (artist, album, title)
	 */
	public void optimizeTable() {
		// FTS Table for search
		db.execSQL("CREATE VIRTUAL TABLE " + SONGS_FTS + " USING fts3(select * from songs);");
		
		// Indices for fragment
		db.execSQL("CREATE INDEX " + SONGS_ARTIST + " ON songs (artist);");
		db.execSQL("CREATE INDEX " + SONGS_ALBUM  + " ON songs (artist, album);");
		db.execSQL("CREATE INDEX " + SONGS_TITLE  + " ON songs (artist, album, title);");
	}
	
	public LinkedList<MyLibraryItem> getArtists() {
		LinkedList<MyLibraryItem> itemList = new LinkedList<MyLibraryItem>();
		
		Cursor c = selectQuery("SELECT artist from songs where artist <> \" \" group by artist");
		
		if (c != null && c.getCount() != 0) {
			c.moveToFirst();
			do {
				MyLibraryItem item = new MyLibraryItem();
				item.setText(c.getString(0));
				item.setSubtext(String.format(mContext.getString(R.string.library_no_albums), 
											   getAlbumCountForArtist(c.getString(0))));
				item.setLevel(Level.ARTIST);
				item.setArtist(c.getString(0));
				itemList.add(item);
			} while (c.moveToNext());
		}
		
		return itemList;
	}
	
	public LinkedList<MyLibraryItem> getAlbums(String artist) {
		LinkedList<MyLibraryItem> itemList = new LinkedList<MyLibraryItem>();
		
		Cursor c = selectQuery("SELECT artist, album from songs where artist = \"" + artist + "\" group by album");
		
		if (c != null && c.getCount() != 0) {
			c.moveToFirst();
			do {
				MyLibraryItem item = new MyLibraryItem();
				item.setText(c.getString(1));
				item.setSubtext(String.format(mContext.getString(R.string.library_no_tracks), 
								 getTitleCountForAlbum(c.getString(0), c.getString(1))));
				item.setLevel(Level.ALBUM);
				item.setArtist(c.getString(0));
				item.setAlbum(c.getString(1));
				itemList.add(item);
			} while (c.moveToNext());
		}
		
		return itemList;
	}
	
	public LinkedList<MyLibraryItem> getTitles(String artist, String album) {
		LinkedList<MyLibraryItem> itemList = new LinkedList<MyLibraryItem>();
		
		Cursor c = selectQuery("SELECT artist, album, title, cast(filename as TEXT) FROM songs where artist = \"" + artist +
							   "\" and album = \"" + album + "\"");
		
		if (c != null && c.getCount() != 0) {
			c.moveToFirst();
			do {
				MyLibraryItem item = new MyLibraryItem();
				item.setText(c.getString(2));
				item.setSubtext(c.getString(1) + " / " + c.getString(0));
				item.setUrl(c.getString(3));
				item.setLevel(Level.TITLE);
				item.setArtist(c.getString(0));
				item.setAlbum(c.getString(1));
				item.setTitle(c.getString(2));
				itemList.add(item);
			} while (c.moveToNext());
		}
		
		return itemList;
	}
	
	/**
	 * How many albums does one artist have in the library?
	 * @param artist The artist for which the count should be checked
	 * @return The number of albums
	 */
	private int getAlbumCountForArtist(String artist) {
		Cursor c = selectQuery("SELECT count(album) FROM songs where artist = \"" + artist + "\" group by album");
		c.moveToFirst();
		int count = c.getInt(0);
		c.close();
		return count;
	}
	
	/**
	 * How many albums does one artist have in the library?
	 * @param artist The artist for which the count should be checked
	 * @return The number of albums
	 */
	private int getTitleCountForAlbum(String artist, String album) {
		Cursor c = selectQuery("SELECT count(title) FROM songs where artist = \"" + artist +
							   "\" and album = \"" + album + "\"");
		c.moveToFirst();
		int count = c.getInt(0);
		c.close();
		return count;
	}
	
	 private Cursor selectQuery(String query) {
		 Cursor c1 = null;
		 try {
			 c1 = db.rawQuery(query, null);
		 } catch (Exception e) {
			 System.out.println("DATABASE ERROR " + e);

		 }
		 return c1;
	 }
}
