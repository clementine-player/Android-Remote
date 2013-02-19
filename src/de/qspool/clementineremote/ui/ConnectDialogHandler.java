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
import de.qspool.clementineremote.backend.elements.Connected;
import de.qspool.clementineremote.backend.elements.Disconnected;
import de.qspool.clementineremote.backend.elements.NoConnection;
import de.qspool.clementineremote.backend.elements.OldProtoVersion;
import de.qspool.clementineremote.backend.elements.ServiceFound;

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
		cd.mPdConnect.dismiss();
		if (msg.obj instanceof Connected) {
			cd.connected();
		} else if (msg.obj instanceof NoConnection) {
			cd.noConnection();
		} else if (msg.obj instanceof OldProtoVersion) {
			cd.oldProtoVersion();
		} else if (msg.obj instanceof Disconnected) {
			cd.disconnected((Disconnected) msg.obj);
		} else if (msg.obj instanceof ServiceFound) {
			cd.serviceFound();
		}
	}
}
