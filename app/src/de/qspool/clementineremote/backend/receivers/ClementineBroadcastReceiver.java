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
import android.content.SharedPreferences;
import android.os.Message;
import android.preference.PreferenceManager;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementineService;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;

public class ClementineBroadcastReceiver extends BroadcastReceiver {

    public static final String CONNECT = "de.qspool.clementineremote.connect";

    public static final String DISCONNECT = "de.qspool.clementineremote.disconnect";

    public static final String PLAYPAUSE = "de.qspool.clementineremote.playpause";

    public static final String PLAY = "de.qspool.clementineremote.play";

    public static final String PAUSE = "de.qspool.clementineremote.pause";

    public static final String NEXT = "de.qspool.clementineremote.next";

    @Override
    public void onReceive(Context context, Intent intent) {
        Message msg = Message.obtain();

        // Check which key was pressed
        switch (intent.getAction()) {
            case CONNECT:
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String ip = prefs.getString(SharedPreferencesKeys.SP_KEY_IP, "");
                int port = Integer.valueOf(
                        prefs.getString(SharedPreferencesKeys.SP_KEY_PORT,
                                String.valueOf(Clementine.DefaultPort)));
                int auth = prefs.getInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, 0);

                // Start the background service
                Intent serviceIntent = new Intent(context, ClementineService.class);
                serviceIntent.putExtra(ClementineService.SERVICE_ID,
                        ClementineService.SERVICE_START);
                serviceIntent.putExtra(ClementineService.EXTRA_STRING_IP, ip);
                serviceIntent.putExtra(ClementineService.EXTRA_INT_PORT, port);
                serviceIntent.putExtra(ClementineService.EXTRA_INT_AUTH, auth);
                context.startService(serviceIntent);
                break;
            case DISCONNECT:
                msg.obj = ClementineMessage.getMessage(MsgType.DISCONNECT);
                break;
            case PLAYPAUSE:
                msg.obj = ClementineMessage.getMessage(MsgType.PLAYPAUSE);
                break;
            case PLAY:
                msg.obj = ClementineMessage.getMessage(MsgType.PLAY);
                break;
            case PAUSE:
                msg.obj = ClementineMessage.getMessage(MsgType.PAUSE);
                break;
            case NEXT:
                msg.obj = ClementineMessage.getMessage(MsgType.NEXT);
                break;
        }

        // Now send the message
        if (msg != null && msg.obj != null
                && App.mClementineConnection != null) {
            App.mClementineConnection.mHandler.sendMessage(msg);
        }
    }
}
