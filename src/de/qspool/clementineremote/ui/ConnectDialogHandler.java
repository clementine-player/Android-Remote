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
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.elements.ServiceFound;
import de.qspool.clementineremote.backend.pb.ClementineMessage;

/**
 * This class is used to handle the messages sent from the
 * connection thread
 */
public class ConnectDialogHandler extends Handler {	
	WeakReference<ConnectDialog> mDialog;
	
	ConnectDialogHandler(ConnectDialog connectDialog) {
		mDialog = new WeakReference<ConnectDialog>(connectDialog);
	}
	
	@Override
	public void handleMessage(Message msg) {
		ConnectDialog cd = mDialog.get();
		if (cd != null) {
			if (msg.obj instanceof ClementineMessage) {
				ClementineMessage clementineMessage = (ClementineMessage) msg.obj;
				
				if (clementineMessage.isErrorMessage()) {
					// We have got an error
					switch (clementineMessage.getErrorMessage()) {
					case NO_CONNECTION:
						cd.mPdConnect.dismiss();
						cd.noConnection();
						break;
					case OLD_PROTO:
						cd.mPdConnect.dismiss();
						cd.oldProtoVersion();
						break;
					default:
						cd.mPdConnect.dismiss();
						cd.noConnection();
						break;
					}
				} else {
					// Okay, normal message
					switch (clementineMessage.getMessageType()) {
					case INFO:
						cd.mPdConnect.setMessage(cd.getString(R.string.connectdialog_download_data));
						break;
					case FIRST_DATA_SENT_COMPLETE:
						cd.mPdConnect.dismiss();
						cd.showPlayerDialog();
						break;
					case DISCONNECT:
						cd.mPdConnect.dismiss();
						cd.disconnected(clementineMessage);
						break;
					default:
						break;
					}
				}
			} else if (msg.obj instanceof ServiceFound) {
				cd.serviceFound();
			}
		}
	}
}
