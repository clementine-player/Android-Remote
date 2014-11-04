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

package de.qspool.clementineremote.backend.pebble;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;

public class Pebble {

    private LinkedList<String> mPebbles = new LinkedList<String>();

    private boolean mUsePebble;

    private ConnectedBroadcastReceiver mConnectedBroadcastReceiver;

    private DisconnectedBroadcastReceiver mDisconnectedBroadcastReceiver;

    public Pebble() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());

        // Get the pebble settings
        mUsePebble = prefs.getBoolean(SharedPreferencesKeys.SP_USE_PEBBLE, true);

        // If we use Pebble, register the broadcast receivers
        if (mUsePebble) {
            registerBroadcastReceiver();
        }

        // Register a preference change listener
        prefs.registerOnSharedPreferenceChangeListener(onPrefChangedListener);
    }

    /**
     * Send the current Song (App.mClementine.getCurrentSong()
     * to the pebble
     */
    public void sendMusicUpdateToPebble() {
        if (mUsePebble && App.mClementine.getCurrentSong() != null) {
            final Intent i = new Intent("com.getpebble.action.NOW_PLAYING");
            i.putExtra("artist", App.mClementine.getCurrentSong().getArtist());
            i.putExtra("album", App.mClementine.getCurrentSong().getAlbum());
            i.putExtra("track", App.mClementine.getCurrentSong().getTitle());

            App.getApp().sendBroadcast(i);
        }
    }

    /**
     * Register the Pebble broadcast receivers
     */
    private void registerBroadcastReceiver() {
        IntentFilter connectedBroadcastFilter = new IntentFilter(
                "com.getpebble.action.PEBBLE_CONNECTED");
        mConnectedBroadcastReceiver = new ConnectedBroadcastReceiver();
        App.getApp().registerReceiver(mConnectedBroadcastReceiver, connectedBroadcastFilter);

        IntentFilter disconnectedBroadcastFilter = new IntentFilter(
                "com.getpebble.action.PEBBLE_DISCONNECTED");
        mDisconnectedBroadcastReceiver = new DisconnectedBroadcastReceiver();
        App.getApp().registerReceiver(mDisconnectedBroadcastReceiver, disconnectedBroadcastFilter);
    }

    private void unregisterBroadcastReceivers() {
        App.getApp().unregisterReceiver(mConnectedBroadcastReceiver);
        App.getApp().unregisterReceiver(mDisconnectedBroadcastReceiver);
    }

    private OnSharedPreferenceChangeListener onPrefChangedListener
            = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            // Check only for pebble setting
            if (key.equals(SharedPreferencesKeys.SP_USE_PEBBLE)) {
                if (sharedPreferences.getBoolean(key, true)) {
                    registerBroadcastReceiver();
                } else {
                    unregisterBroadcastReceivers();
                }
            }
        }
    };

    private class ConnectedBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String address = intent.getStringExtra("address");
            mPebbles.add(address);
        }
    }

    private class DisconnectedBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String address = intent.getStringExtra("address");
            mPebbles.remove(address);
        }
    }

}
