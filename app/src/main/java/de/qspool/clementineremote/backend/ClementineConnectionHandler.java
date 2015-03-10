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

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

import de.qspool.clementineremote.backend.pb.ClementineMessage;

/**
 * This class receives the handler messages from the ui thread
 */
public class ClementineConnectionHandler extends Handler {

    WeakReference<ClementinePlayerConnection> mClementineConnection;

    public ClementineConnectionHandler(ClementinePlayerConnection c) {
        mClementineConnection = new WeakReference<>(c);
    }

    @Override
    public void handleMessage(Message msg) {
        ClementinePlayerConnection myClementineConnection = mClementineConnection.get();

        if (msg.arg1 == ClementinePlayerConnection.PROCESS_PROTOC) {
            myClementineConnection.processProtocolBuffer((ClementineMessage) msg.obj);
        } else {
            // Act on the message
            ClementineMessage message = (ClementineMessage) msg.obj;
            if (message.isErrorMessage()) {
                myClementineConnection.disconnect(message);
            } else {
                switch (message.getMessageType()) {
                    case CONNECT:
                        if (!myClementineConnection.isConnected()) {
                            myClementineConnection.createConnection(message);
                        }
                        break;
                    case DISCONNECT:
                        myClementineConnection.disconnect(message);
                        break;
                    default:
                        myClementineConnection.sendRequest(message);
                        break;
                }
            }
        }
    }
}
