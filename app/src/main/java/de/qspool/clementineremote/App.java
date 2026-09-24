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

package de.qspool.clementineremote;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.StrictMode;
import android.preference.PreferenceManager;

import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.downloader.DownloadManager;
import de.qspool.clementineremote.utils.StrictModePolicies;

public class App extends Application {

    public static ClementinePlayerConnection ClementineConnection = null;

    public static Clementine Clementine = new Clementine();

    private static App mApp;

    private static SharedPreferences sPreferences;

    public final static String notificationChannel = "CR_NOT_CH_1";

    public App() {
        mApp = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            StrictModePolicies.enableLogging();
        }

        loadPreferences();

        createNotificationChannel();

        // Create a new downloadmanager instance
        DownloadManager.getInstance(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notificationChannel, "Default",
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public static App getApp() {
        return mApp;
    }

    /**
     * The app's settings. Loaded once at startup, so screens and services never read (or, on
     * first launch, create) the preferences file on the main thread.
     */
    public static SharedPreferences getPreferences() {
        return sPreferences;
    }

    private void loadPreferences() {
        // The one deliberate settings read on the main thread: before any screen needs them.
        StrictMode.ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
        try {
            sPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            // Wait for the file to be parsed, so later reads never block on it.
            sPreferences.getAll();
        } finally {
            StrictMode.setThreadPolicy(policy);
        }
    }
}
