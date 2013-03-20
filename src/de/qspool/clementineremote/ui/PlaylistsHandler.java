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

package de.qspool.clementineremote.ui;

import java.lang.ref.WeakReference;

import android.os.Message;
import android.os.Handler;
import de.qspool.clementineremote.backend.elements.ReloadMetadataChanged;
import de.qspool.clementineremote.backend.elements.ReloadPlaylistSongs;
import de.qspool.clementineremote.backend.elements.ReloadPlaylists;

/**
 * This class is used to handle the messages sent from the
 * connection thread
 */
public class PlaylistsHandler extends Handler {	
	WeakReference<Playlists> mDialog;
	
	PlaylistsHandler(Playlists playlistsDialog) {
		mDialog = new WeakReference<Playlists>(playlistsDialog);
	}
	
	@Override
	public void handleMessage(Message msg) {
		Playlists pd = mDialog.get();
		if (msg.obj instanceof ReloadMetadataChanged) {
			pd.reloadInfo();
		} else if (msg.obj instanceof ReloadPlaylistSongs) {
			pd.checkGotAllPlaylists();
			pd.reloadInfo();
		} else if (msg.obj instanceof ReloadPlaylists) {
			pd.getPlaylists();
		}
	}
}
