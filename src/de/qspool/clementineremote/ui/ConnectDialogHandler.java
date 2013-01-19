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
import android.widget.Toast;
import de.qspool.clementineremote.ClementineRemoteControlActivity;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.elements.Connected;
import de.qspool.clementineremote.backend.elements.NoConnection;

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
			Toast.makeText(cd, R.string.connectdialog_connected, Toast.LENGTH_SHORT).show();
			cd.setResult(ClementineRemoteControlActivity.RESULT_CONNECT);
			cd.finish();
		} else if (msg.obj instanceof NoConnection) {
			Toast.makeText(cd, R.string.connectdialog_error, Toast.LENGTH_SHORT).show();
		}
	}
}
