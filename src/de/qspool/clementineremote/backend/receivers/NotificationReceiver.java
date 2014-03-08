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

package de.qspool.clementineremote.backend.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Message;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;

public class NotificationReceiver extends BroadcastReceiver {

    public static final String PLAYPAUSE = "de.qspool.clementineremote.playpause";

    public static final String NEXT = "de.qspool.clementineremote.next";

    @Override
    public void onReceive(Context context, Intent intent) {
        Message msg = Message.obtain();

        // Check which key was pressed
        if (intent.getAction().equals(PLAYPAUSE)) {
            msg.obj = ClementineMessage.getMessage(MsgType.PLAYPAUSE);
        } else if (intent.getAction().equals(NEXT)) {
            msg.obj = ClementineMessage.getMessage(MsgType.NEXT);
        }

        // Now send the message
        if (msg != null
                && App.mClementineConnection != null) {
            App.mClementineConnection.mHandler.sendMessage(msg);
        }
    }
}
