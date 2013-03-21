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
import de.qspool.clementineremote.backend.elements.Disconnected;
import de.qspool.clementineremote.backend.elements.NoConnection;
import de.qspool.clementineremote.backend.elements.Reload;
import de.qspool.clementineremote.backend.elements.ReloadMetadataChanged;
import de.qspool.clementineremote.backend.elements.ReloadPlaylistSongs;
import de.qspool.clementineremote.backend.elements.ReloadPlaylists;

/**
 * This class is used to handle the messages sent from the
 * connection thread
 */
public class PlayerHandler extends Handler {	
	WeakReference<Player> mDialog;
	
	PlayerHandler(Player playerDialog) {
		mDialog = new WeakReference<Player>(playerDialog);
	}
	
	@Override
	public void handleMessage(Message msg) {
		Player pd = mDialog.get();
		
		if (msg.obj instanceof NoConnection) {
			pd.disconnect();
		} else if (msg.obj instanceof Disconnected) {
			pd.disconnect();
		} else if (msg.obj instanceof ReloadMetadataChanged
				 || msg.obj instanceof ReloadPlaylistSongs
				 || msg.obj instanceof ReloadPlaylists) {
			pd.reloadInfo();
			pd.reloadPlaylist();
		} else if (msg.obj instanceof Reload) {
			pd.reloadInfo();
		}
	}
}
