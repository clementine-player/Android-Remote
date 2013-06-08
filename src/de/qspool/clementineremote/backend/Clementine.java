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

package de.qspool.clementineremote.backend;

import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.backend.player.MySong;
import android.util.SparseArray;

/**
 * This Class stores the attributes of Clementine, like the version,
 * the current player state, current song, etc. 
 */
public class Clementine {
	public static enum State {PLAY, PAUSE, STOP};
	public static enum RepeatMode {OFF, TRACK, ALBUM, PLAYLIST};
	public static enum ShuffleMode {OFF, ALL, INSIDE_ALBUM, ALBUMS};
	public final static int DefaultPort = 5500; // Change also strings.xml! 
	public final static String DefaultCallVolume = "20";
	
	private String mVersion;
	private MySong mCurrentSong;
	private boolean mIsConnected;
	private int mVolume;
	private State mState;
	private RepeatMode mRepeatMode;
	private ShuffleMode mShuffleMode;
	private int mSongPosition;
	private SparseArray<MyPlaylist> mPlaylists = new SparseArray<MyPlaylist>();
	private int mActivePlaylist;

	public int getSongPosition() {
		return mSongPosition;
	}

	public void setSongPosition(int songPosition) {
		this.mSongPosition = songPosition;
	}


	public Clementine() {
		mVersion = "";
		mCurrentSong = null;
		mVolume = 100;
		mState = State.STOP;
	}
	
	
	public String getVersion() {
		return mVersion;
	}

	public void setVersion(String version) {
		mVersion = version;
	}

	public MySong getCurrentSong() {
		return mCurrentSong;
	}

	public void setCurrentSong(MySong currentSong) {
		this.mCurrentSong = currentSong;
	}

	public boolean isConnected() {
		return mIsConnected;
	}

	public void setConnected(boolean isConnected) {
		this.mIsConnected = isConnected;
	}
	
	public int getVolume() {
		return mVolume;
	}

	public void setVolume(int volume) {
		this.mVolume = volume;
	}


	public State getState() {
		return mState;
	}

	public void setState(State state) {
		this.mState = state;
	}

	public RepeatMode getRepeatMode() {
		return mRepeatMode;
	}

	public void setRepeatMode(RepeatMode mRepeatMode) {
		this.mRepeatMode = mRepeatMode;
	}
	
	public void nextRepeatMode() {
		switch (mRepeatMode) {
		case OFF: 		mRepeatMode = RepeatMode.TRACK;
						break;
		case TRACK:		mRepeatMode = RepeatMode.ALBUM;
						break;
		case ALBUM:		mRepeatMode = RepeatMode.PLAYLIST;
						break;
		case PLAYLIST:	mRepeatMode = RepeatMode.OFF;
						break;
		}
	}

	public ShuffleMode getShuffleMode() {
		return mShuffleMode;
	}

	public void setShuffleMode(ShuffleMode mShuffleMode) {
		this.mShuffleMode = mShuffleMode;
	}
	
	public void nextShuffleMode() {
		switch (mShuffleMode) {
		case OFF: 		mShuffleMode = ShuffleMode.ALL;
						break;
		case ALL:		mShuffleMode = ShuffleMode.INSIDE_ALBUM;
						break;
		case INSIDE_ALBUM:	mShuffleMode = ShuffleMode.ALBUMS;
							break;
		case ALBUMS:	mShuffleMode = ShuffleMode.OFF;
						break;
		}
	}

	public SparseArray<MyPlaylist> getPlaylists() {
		return mPlaylists;
	}

	public void addPlaylist(MyPlaylist playlist) {
		mPlaylists.append(playlist.getId(), playlist);
		// Check if we have the active playlist
		if (playlist.isActive()) {
			mActivePlaylist = playlist.getId();
		}
	}

	public MyPlaylist getActivePlaylist() {
		return mPlaylists.get(mActivePlaylist);
	}
	
	public void setActivePlaylistId(int id) {
		mActivePlaylist = id;
	}
}
