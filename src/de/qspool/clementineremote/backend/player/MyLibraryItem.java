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

public class MyLibraryItem {
	public enum Level {ARTIST, ALBUM, TITLE};
	
	private String mText = new String();
	private String mSubtext = new String();
	private String mUrl = new String();
	
	private String mArtist = new String();
	private String mAlbum = new String();
	private String mTitle = new String();
	
	private Level mLevel;
	
	public String getText() {
		return mText;
	}
	public void setText(String mText) {
		this.mText = mText;
	}
	public String getSubtext() {
		return mSubtext;
	}
	public void setSubtext(String mSubtext) {
		this.mSubtext = mSubtext;
	}
	public String getUrl() {
		return mUrl;
	}
	public void setUrl(String mUrl) {
		this.mUrl = mUrl;
	}
	public Level getLevel() {
		return mLevel;
	}
	public void setLevel(Level mLevel) {
		this.mLevel = mLevel;
	}
	public String getArtist() {
		return mArtist;
	}
	public void setArtist(String mArtist) {
		this.mArtist = mArtist;
	}
	public String getAlbum() {
		return mAlbum;
	}
	public void setAlbum(String mAlbum) {
		this.mAlbum = mAlbum;
	}
	public String getTitle() {
		return mTitle;
	}
	public void setTitle(String mTitle) {
		this.mTitle = mTitle;
	}
	
}
