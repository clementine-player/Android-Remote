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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import androidx.core.content.ContextCompat;
import android.telephony.TelephonyManager;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer;

public class ClementinePhoneStateCheck extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (App.getApp() == null || intent == null
                || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }

        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (!TelephonyManager.EXTRA_STATE_IDLE.equals(state)
                && !TelephonyManager.EXTRA_STATE_RINGING.equals(state)
                && !TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            return;
        }

        // Always reset persisted call state when IDLE arrives even if currently disconnected, so a
        // call that ended offline does not keep blocking volume lowering on the next call.
        ClementinePlayerConnection connection = App.ClementineConnection;
        if (connection == null || App.Clementine == null || !connection.isConnected()) {
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                LastClementineState.clearCallState();
            }
            return;
        }

        // Check if we need to change the volume
        SharedPreferences prefs = App.getPreferences();
        int volume;
        try {
            volume = Integer.parseInt(prefs.getString(
                    SharedPreferencesKeys.SP_CALL_VOLUME,
                    Clementine.DefaultCallVolume).trim());
        } catch (NumberFormatException e) {
            try {
                volume = Integer.parseInt(Clementine.DefaultCallVolume);
            } catch (NumberFormatException fallback) {
                volume = 20;
            }
        }

        // Get the phone volume lowering preference
        if (!prefs.getBoolean(SharedPreferencesKeys.SP_LOWER_VOLUME, true)) {
            // Clear saved call state if the setting was turned off mid-call.
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                LastClementineState.clearCallState();
            }
            return;
        }

        LastClementineState lastClementineState = new LastClementineState();
        lastClementineState.load();

        // On Lollipop+, the state is broadcast twice. Only process new states.
        if (lastClementineState.phoneState.equals(state)) {
            return;
        }

        Message msg = Message.obtain();

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)
                || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {

            // Only lower the volume once. When receiving a call, the state is RINGING. On pickup
            // OFFHOOK is broadcasted. So we only need to take action when we previously had the
            // IDLE state.
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(lastClementineState.phoneState)) {
                lastClementineState.volume = App.Clementine.getVolume();
                lastClementineState.state = App.Clementine.getState();
                lastClementineState.phoneState = state;
                lastClementineState.didModify = true;
                lastClementineState.save();

                if (volume >= 0) {
                    msg.obj = ClementineMessageFactory.buildVolumeMessage(Math.min(volume, 100));
                } else {
                    msg.obj = ClementineMessage.getMessage(
                            ClementineRemoteProtocolBuffer.MsgType.PAUSE);
                }
            } else {
                lastClementineState.savePhoneState(state);
            }
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            // Only restore if this session actually lowered volume/paused on
            // call start (e.g. skip if we connected mid-call).
            if (lastClementineState.didModify) {
                if (volume >= 0) {
                    msg.obj = ClementineMessageFactory.buildVolumeMessage(lastClementineState.volume);
                } else {
                    if (lastClementineState.state == Clementine.State.PLAY) {
                        msg.obj = ClementineMessage.getMessage(
                                ClementineRemoteProtocolBuffer.MsgType.PLAY);
                    }
                }
            }
            LastClementineState.clearCallState();
        }

        // Now send the message
        Handler handler = connection.mHandler;
        if (msg.obj != null && handler != null) {
            handler.sendMessage(msg);
        }
    }

    // Persist call tracking in SharedPreferences keyed by PID rather than a
    // static field so receiver recreation keeps state across a live session
    // while stale RINGING/OFFHOOK from a dead process is automatically ignored.
    private static class LastClementineState {
        private final static String KEY_LAST_VOLUME = "phone_last_volume";
        private final static String KEY_LAST_STATE  = "phone_last_state";
        private final static String KEY_LAST_PHONE_STATE = "phone_last_phone_state";
        private final static String KEY_DID_MODIFY = "phone_last_did_modify";
        private final static String KEY_SAVED_PID = "phone_last_pid";

        public int volume;
        public Clementine.State state;
        public String phoneState;
        public boolean didModify;

        public void load() {
            SharedPreferences prefs = App.getPreferences();
            Clementine.State fallbackState = App.Clementine.getState() != null
                    ? App.Clementine.getState()
                    : Clementine.State.STOP;
            int savedPid = prefs.getInt(KEY_SAVED_PID, 0);
            int currentPid = android.os.Process.myPid();
            if (savedPid != currentPid) {
                // Process died mid-call: any saved RINGING/OFFHOOK belongs to a dead session.
                phoneState = TelephonyManager.EXTRA_STATE_IDLE;
                didModify = false;
                volume = Math.max(0, Math.min(100, App.Clementine.getVolume()));
                state = fallbackState;
                return;
            }
            volume = Math.max(0, Math.min(100, prefs.getInt(KEY_LAST_VOLUME, App.Clementine.getVolume())));
            int stateOrd = prefs.getInt(KEY_LAST_STATE, fallbackState.ordinal());
            Clementine.State[] values = Clementine.State.values();
            state = (stateOrd >= 0 && stateOrd < values.length) ? values[stateOrd] : fallbackState;
            String savedPhone = prefs.getString(KEY_LAST_PHONE_STATE, TelephonyManager.EXTRA_STATE_IDLE);
            phoneState = savedPhone != null ? savedPhone : TelephonyManager.EXTRA_STATE_IDLE;
            didModify = prefs.getBoolean(KEY_DID_MODIFY, false);
        }

        public void save() {
            SharedPreferences prefs = App.getPreferences();
            SharedPreferences.Editor editor = prefs.edit();

            editor.putInt(KEY_LAST_VOLUME, volume);
            editor.putInt(KEY_LAST_STATE, state.ordinal());
            editor.putString(KEY_LAST_PHONE_STATE, phoneState);
            editor.putBoolean(KEY_DID_MODIFY, didModify);
            editor.putInt(KEY_SAVED_PID, android.os.Process.myPid());

            editor.apply();
        }

        public void savePhoneState(String state) {
            App.getPreferences().edit()
                    .putString(KEY_LAST_PHONE_STATE, state)
                    .putInt(KEY_SAVED_PID, android.os.Process.myPid())
                    .apply();
        }

        public static void clearCallState() {
            App.getPreferences().edit()
                    .putString(KEY_LAST_PHONE_STATE, TelephonyManager.EXTRA_STATE_IDLE)
                    .putBoolean(KEY_DID_MODIFY, false)
                    .putInt(KEY_SAVED_PID, android.os.Process.myPid())
                    .apply();
        }
    }

}
