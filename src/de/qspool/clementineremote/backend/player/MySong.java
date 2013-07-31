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

import java.util.LinkedList;
import java.util.List;

import com.google.protobuf.ByteString;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Representation of a song
 */
public class MySong {
	private int id;
	private int index;
	private String title;
	private String artist;
	private String album;
	private String albumartist;
	private String prettyLength;
	private int length;
	private String genre;
	private String year;
	private int track;
	private int disc;
	private int playcount;
	private byte[] art;
	private boolean loved;
	private List<LyricsProvider> mLyricsProvider = new LinkedList<LyricsProvider>();
	private String filename;
	private int size;
	private boolean local;
	
	public boolean equals(MySong song) {
		if (song.id == this.id
		 && song.index == this.index) {
			return true;
	} else {
			return false;
		}
	}
	
	@SuppressLint("DefaultLocale")
	public boolean contains(String constraint) {
		String cs = constraint.toLowerCase();
		return (title.toLowerCase().contains(cs) ||
				 artist.toLowerCase().contains(cs) ||
				 album.toLowerCase().contains(cs) ||
				 albumartist.toLowerCase().contains(cs) ||
				 genre.toLowerCase().contains(cs) ||
				 year.toLowerCase().contains(cs));
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(artist);
		sb.append(" - ");
		sb.append(title);
		return sb.toString();
	}
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public int getIndex() {
		return index;
	}
	public void setIndex(int index) {
		this.index = index;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getArtist() {
		return artist;
	}
	public void setArtist(String artist) {
		this.artist = artist;
	}
	public String getAlbum() {
		return album;
	}
	public void setAlbum(String album) {
		this.album = album;
	}
	public String getAlbumartist() {
		return albumartist;
	}
	public void setAlbumartist(String albumartist) {
		this.albumartist = albumartist;
	}
	public String getPrettyLength() {
		return prettyLength;
	}
	public void setPrettyLength(String prettyLength) {
		this.prettyLength = prettyLength;
	}
	public int getLength() {
		return length;
	}
	public void setLength(int length) {
		this.length = length;
	}
	public String getGenre() {
		return genre;
	}
	public void setGenre(String genre) {
		this.genre = genre;
	}
	public String getYear() {
		return year;
	}
	public void setYear(String year) {
		this.year = year;
	}
	public int getTrack() {
		return track;
	}
	public void setTrack(int track) {
		this.track = track;
	}
	public int getDisc() {
		return disc;
	}
	public void setDisc(int disc) {
		this.disc = disc;
	}
	public int getPlaycount() {
		return playcount;
	}
	public void setPlaycount(int playcount) {
		this.playcount = playcount;
	}
	public Bitmap getArt() {
		if (art == null)
			return null;
		else 
			return BitmapFactory.decodeByteArray(art, 0, art.length);
	}
	public void setArt(ByteString byteString) {
		this.art = byteString.toByteArray();
	}

	public boolean isLoved() {
		return loved;
	}

	public void setLoved(boolean loved) {
		this.loved = loved;
	}

	public List<LyricsProvider> getLyricsProvider() {
		return mLyricsProvider;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public boolean isLocal() {
		return local;
	}

	public void setLocal(boolean local) {
		this.local = local;
	}
}
