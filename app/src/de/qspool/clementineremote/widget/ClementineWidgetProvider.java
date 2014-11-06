/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2014, Andreas Muttscheller <asfa194@gmail.com>
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

package de.qspool.clementineremote.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.receivers.ClementineBroadcastReceiver;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineWidgetProvider extends AppWidgetProvider {

    private boolean mClementineConnected = false;

    private MySong mCurrentSong;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int N = appWidgetIds.length;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean canConnect = prefs.contains(SharedPreferencesKeys.SP_KEY_IP);

        getClementineStatus();

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i = 0; i < N; i++) {
            int appWidgetId = appWidgetIds[i];

            // Get the layout for the App Widget and update fields
            RemoteViews views = new RemoteViews(context.getPackageName(),
                    R.layout.widget_clementine);

            if (mClementineConnected) {
                // Textviews
                if (mCurrentSong == null) {
                    views.setTextViewText(R.id.widget_artist, "");
                    views.setTextViewText(R.id.widget_title,
                            context.getString(R.string.player_nosong));
                } else {
                    views.setTextViewText(R.id.widget_artist, mCurrentSong.getArtist());
                    views.setTextViewText(R.id.widget_title, mCurrentSong.getTitle());
                }

                // Play or pause?
                Intent intentPlayPause = new Intent(context, ClementineBroadcastReceiver.class);

                if (App.mClementine.getState() == Clementine.State.PLAY) {
                    views.setImageViewResource(R.id.widget_btn_play_pause,
                            R.drawable.ic_media_pause);
                    intentPlayPause.setAction(ClementineBroadcastReceiver.PAUSE);
                } else {
                    views.setImageViewResource(R.id.widget_btn_play_pause,
                            R.drawable.ic_media_play);
                    intentPlayPause.setAction(ClementineBroadcastReceiver.PLAY);
                }
                views.setOnClickPendingIntent(R.id.widget_btn_play_pause,
                        PendingIntent
                                .getBroadcast(context, 0, intentPlayPause,
                                        PendingIntent.FLAG_ONE_SHOT));

                // Next track
                Intent intentNext = new Intent(context, ClementineBroadcastReceiver.class);
                intentNext.setAction(ClementineBroadcastReceiver.NEXT);

                views.setOnClickPendingIntent(R.id.widget_btn_next,
                        PendingIntent
                                .getBroadcast(context, 0, intentNext, PendingIntent.FLAG_ONE_SHOT));

                // When connected, user can start the app by touching anywhere
                views.setOnClickPendingIntent(R.id.widget_layout,
                        Utilities.getClementineRemotePendingIntent(context));
            } else {
                // Reset play button
                views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_media_play);

                if (canConnect) {
                    // Textviews
                    views.setTextViewText(R.id.widget_artist, prefs.getString(
                            SharedPreferencesKeys.SP_KEY_IP, ""));
                    views.setTextViewText(R.id.widget_title,
                            context.getString(R.string.widget_connect_to));

                    // Start an intent to connect to Clemetine
                    Intent intentConnect = new Intent(context, ClementineBroadcastReceiver.class);
                    intentConnect.setAction(ClementineBroadcastReceiver.CONNECT);
                    views.setOnClickPendingIntent(R.id.widget_layout, PendingIntent
                            .getBroadcast(context, 0, intentConnect, PendingIntent.FLAG_ONE_SHOT));
                } else {
                    // Textviews
                    views.setTextViewText(R.id.widget_artist,
                            context.getString(R.string.widget_no_ip));
                    views.setTextViewText(R.id.widget_title,
                            context.getString(R.string.widget_not_connected));

                    // Start Clementine Remote
                    views.setOnClickPendingIntent(R.id.widget_layout,
                            Utilities.getClementineRemotePendingIntent(context));
                }
            }

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private void getClementineStatus() {
        mCurrentSong = App.mClementine.getCurrentSong();
        mClementineConnected = (App.mClementineConnection != null
                && App.mClementineConnection.isConnected());
    }
}
