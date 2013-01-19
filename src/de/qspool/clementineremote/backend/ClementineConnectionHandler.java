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

import java.lang.ref.WeakReference;

import android.os.Handler;
import android.os.Message;
import de.qspool.clementineremote.backend.requests.CheckForData;
import de.qspool.clementineremote.backend.requests.RequestConnect;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.backend.requests.RequestToThread;

/**
 * This class receives the handler messages from the ui thread
 */
public class ClementineConnectionHandler extends Handler {
	WeakReference<ClementineConnection> mClementineConnection;
	
	public ClementineConnectionHandler(ClementineConnection c) {
		mClementineConnection = new WeakReference<ClementineConnection>(c);
	}

	@Override
	public void handleMessage(Message msg) {
		ClementineConnection myClementineConnection = mClementineConnection.get();
		
        // Act on the message
    	RequestToThread r = (RequestToThread) msg.obj;
    	if (r instanceof CheckForData) {
    		myClementineConnection.checkForData();
    	} else if (r instanceof RequestConnect) {
    		myClementineConnection.createConnection((RequestConnect) r);
    	} else if (r instanceof RequestDisconnect) {
    		myClementineConnection.disconnect(r);
    	} else {
    		myClementineConnection.sendRequest(r);
    	}
    }
}
