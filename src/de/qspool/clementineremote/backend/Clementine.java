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

import de.qspool.clementineremote.backend.player.*;

/**
 * This Class stores the attributes of Clementine, like the version,
 * the current player state, current song, etc. 
 */
public class Clementine {
	public static enum State {PLAY, PAUSE, STOP};
	public static int DefaultPort = 5500; // Change also strings.xml! 
	
	private String Version;
	private Song currentSong;
	private boolean isConnected;
	private int volume;
	private State state;

	public Clementine() {
		Version = "";
		currentSong = null;
		volume = 100;
		state = State.STOP;
	}
	
	
	public String getVersion() {
		return Version;
	}

	public void setVersion(String version) {
		Version = version;
	}

	public Song getCurrentSong() {
		return currentSong;
	}

	public void setCurrentSong(Song currentSong) {
		this.currentSong = currentSong;
	}

	public boolean isConnected() {
		return isConnected;
	}

	public void setConnected(boolean isConnected) {
		this.isConnected = isConnected;
	}
	
	public int getVolume() {
		return volume;
	}

	public void setVolume(int volume) {
		this.volume = volume;
	}


	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

}
