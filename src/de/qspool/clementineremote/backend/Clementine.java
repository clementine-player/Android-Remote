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

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.backend.player.MySong;
import android.content.Context;
import android.util.SparseArray;
import android.widget.Toast;


/**
 * This Class stores the attributes of Clementine, like the version,
 * the current player state, current song, etc. 
 */
public class Clementine {
	public static enum State {PLAY, PAUSE, STOP};
	public static enum RepeatMode {OFF, TRACK, ALBUM, PLAYLIST};
	public static enum RandomMode {OFF, ALL, INSIDE_ALBUM, ALBUMS};
	public static int DefaultPort = 5500; // Change also strings.xml! 
	
	private String mVersion;
	private MySong mCurrentSong;
	private boolean mIsConnected;
	private int mVolume;
	private State mState;
	private RepeatMode mRepeatMode;
	private RandomMode mRandomMode;
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
	
	public void nextRepeatMode(boolean showToast, Context context) {
		switch (mRepeatMode) {
		case OFF: 		mRepeatMode = RepeatMode.TRACK;
						if (showToast) {
							Toast.makeText(context, R.string.repeat_track, Toast.LENGTH_SHORT).show();
						}
						break;
		case TRACK:		mRepeatMode = RepeatMode.ALBUM;
						if (showToast) {
							Toast.makeText(context, R.string.repeat_album, Toast.LENGTH_SHORT).show();
						}
						break;
		case ALBUM:		mRepeatMode = RepeatMode.PLAYLIST;
						if (showToast) {
							Toast.makeText(context, R.string.repeat_playlist, Toast.LENGTH_SHORT).show();
						}
						break;
		case PLAYLIST:	mRepeatMode = RepeatMode.OFF;
						if (showToast) {
							Toast.makeText(context, R.string.repeat_off, Toast.LENGTH_SHORT).show();
						}
						break;
		}
	}

	public RandomMode getRandomMode() {
		return mRandomMode;
	}

	public void setRandomMode(RandomMode mRandomMode) {
		this.mRandomMode = mRandomMode;
	}
	
	public void nextRandomMode(boolean showToast, Context context) {
		switch (mRandomMode) {
		case OFF: 		mRandomMode = RandomMode.ALL;
						if (showToast) {
							Toast.makeText(context, R.string.random_all, Toast.LENGTH_SHORT).show();
						}
						break;
		case ALL:		mRandomMode = RandomMode.INSIDE_ALBUM;
						if (showToast) {
							Toast.makeText(context, R.string.random_inside_album, Toast.LENGTH_SHORT).show();
						}
						break;
		case INSIDE_ALBUM:	mRandomMode = RandomMode.ALBUMS;
							if (showToast) {
								Toast.makeText(context, R.string.random_albums, Toast.LENGTH_SHORT).show();
							}
							break;
		case ALBUMS:	mRandomMode = RandomMode.OFF;
						if (showToast) {
							Toast.makeText(context, R.string.random_off, Toast.LENGTH_SHORT).show();
						}
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
}
