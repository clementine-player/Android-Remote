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

import java.util.Collection;
import java.util.LinkedList;

/**
 * Representation of a playlist
 */
public class MyPlaylist {
	private int mId;
	private String mName;
	private int mItemCount;
	private boolean mActive;
	private LinkedList<MySong> mPlaylistSongs = new LinkedList<MySong>();
	private boolean mClosed;
	
	public MyPlaylist() {
	}
	
	public int getId() {
		return mId;
	}
	public void setId(int mId) {
		this.mId = mId;
	}
	public String getName() {
		return mName;
	}
	public void setName(String mName) {
		this.mName = mName;
	}
	public int getItemCount() {
		return mItemCount;
	}
	public void setItemCount(int mItemCount) {
		this.mItemCount = mItemCount;
	}
	public boolean isActive() {
		return mActive;
	}
	public void setActive(boolean mActive) {
		this.mActive = mActive;
	}

	public LinkedList<MySong> getPlaylistSongs() {
		return new LinkedList<MySong>(mPlaylistSongs);
	}
	
	public boolean hasSongs() {
		return !mPlaylistSongs.isEmpty();
	}
	
	/**
	 * Set the songs the current playlist has. This clears the 
	 * current List of songs!
	 * @param songs The songs from the playlist
	 */
	public void setSongs(Collection<MySong> songs) {
		mPlaylistSongs.clear();
		mPlaylistSongs.addAll(songs);
	}

	public boolean isClosed() {
		return mClosed;
	}

	public void setClosed(boolean mClosed) {
		this.mClosed = mClosed;
	}
}
