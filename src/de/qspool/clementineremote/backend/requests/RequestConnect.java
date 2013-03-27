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

package de.qspool.clementineremote.backend.requests;

public class RequestConnect extends RequestToThread {

	private String mIp;
	private int mPort;
	private int mAuthCode;
	private boolean mRequestPlaylistSongs;
	
	public RequestConnect(String ip, int port, int authCode, boolean requestPlaylistSongs) {
		super();
		mIp = ip;
		mPort = port;
		mAuthCode = authCode;
		mRequestPlaylistSongs = requestPlaylistSongs;
	}

	public String getIp() {
		return mIp;
	}

	public int getPort() {
		return mPort;
	}

	public int getAuthCode() {
		return mAuthCode;
	}
	
	public boolean getRequestPlaylistSongs() {
		return mRequestPlaylistSongs;
	}
	
	public void setRequestPlaylistSongs(boolean requestFirstData) {
		mRequestPlaylistSongs = requestFirstData;
	}
}
