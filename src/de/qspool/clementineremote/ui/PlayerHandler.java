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

import android.os.Handler;
import android.os.Message;
import de.qspool.clementineremote.backend.pb.ClementineMessage;

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
		
		if (msg.obj instanceof ClementineMessage) {
			ClementineMessage clementineMessage = (ClementineMessage) msg.obj;
			
			if (clementineMessage.isErrorMessage()) {
				// We have got an error
				switch (clementineMessage.getErrorMessage()) {
				case NO_CONNECTION:
					pd.disconnect();
					break;
				default:
					pd.disconnect();
					break;
				}
			} else {
				// Okay, normal message
				switch (clementineMessage.getMessageType()) {
				case DISCONNECT:
					pd.disconnect();
					break;
				default:
					pd.MessageFromClementine(clementineMessage);
					break;
				}
			}
		}
	}
}
