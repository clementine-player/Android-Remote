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

import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.downloader.DownloadManager;

public class App extends Application {

    public static ClementinePlayerConnection ClementineConnection = null;

    public static Clementine Clementine = new Clementine();

    private static App mApp;

    public final static String notificationChannel = "CR_NOT_CH_1";

    public App() {
        mApp = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Register new default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(new ClementineExceptionHandler(this));

        // Create a new downloadmanager instance
        DownloadManager.getInstance(this);
    }

    public static App getApp() {
        return mApp;
    }
}
