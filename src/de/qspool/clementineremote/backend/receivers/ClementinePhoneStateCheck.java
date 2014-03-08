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
import android.telephony.TelephonyManager;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;

public class ClementinePhoneStateCheck extends BroadcastReceiver {

    static int mLastVolume = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (App.mApp == null
                || App.mClementineConnection == null
                || App.mClementine == null
                || !App.mClementineConnection.isConnected()) {
            return;
        }

        // Check if we need to change the volume
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.mApp);

        // Get the pebble settings
        if (prefs.getBoolean(App.SP_LOWER_VOLUME, true)) {
            // Get the current state of the telephone
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            Message msg = Message.obtain();

            if (state.equals(TelephonyManager.EXTRA_STATE_IDLE) && mLastVolume != -1) {
                msg.obj = ClementineMessageFactory.buildVolumeMessage(mLastVolume);
                mLastVolume = -1;
            } else if (!state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                mLastVolume = App.mClementine.getVolume();
                String volumeString = prefs
                        .getString(App.SP_CALL_VOLUME, Clementine.DefaultCallVolume);
                msg.obj = ClementineMessageFactory
                        .buildVolumeMessage(Integer.parseInt(volumeString));
            } else {
                msg = null;
            }

            // Now send the message
            if (msg != null
                    && App.mClementineConnection != null) {
                App.mClementineConnection.mHandler.sendMessage(msg);
            }

        }
    }

}
