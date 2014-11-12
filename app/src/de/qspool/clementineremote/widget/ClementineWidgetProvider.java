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
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.receivers.ClementineBroadcastReceiver;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineWidgetProvider extends AppWidgetProvider {

    private WidgetIntent.ClementineAction mCurrentClementineAction;

    private ClementinePlayerConnection.ConnectionStatus mCurrentConnectionStatus;

    @Override
    public void onReceive(Context context, Intent intent) {
        mCurrentClementineAction = WidgetIntent.ClementineAction.DEFAULT;
        mCurrentConnectionStatus = ClementinePlayerConnection.ConnectionStatus.IDLE;

        String action = intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                int idAction = extras.getInt(WidgetIntent.EXTRA_CLEMENTINE_ACTION);
                int idState = extras.getInt(WidgetIntent.EXTRA_CLEMENTINE_CONNECTION_STATE);

                mCurrentClementineAction = WidgetIntent.ClementineAction.values()[idAction];
                mCurrentConnectionStatus = ClementinePlayerConnection.ConnectionStatus
                        .values()[idState];
            }
        }

        // Call this last. In AppWidgetProvider it calls onUpdate and other methods for the Widget
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int N = appWidgetIds.length;

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i = 0; i < N; i++) {
            int appWidgetId = appWidgetIds[i];
            appWidgetManager.getAppWidgetInfo(appWidgetId);

            // Get the layout for the App Widget and update fields
            RemoteViews views = new RemoteViews(context.getPackageName(),
                    R.layout.widget_clementine);

            switch (mCurrentClementineAction) {
                case DEFAULT:
                case CONNECTION_STATUS:
                    updateViewsOnConnectionStatusChange(context, views);
                    break;
                case STATE_CHANGE:
                    updateViewsOnStateChange(context, views);
                    break;
            }

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private void updateViewsOnConnectionStatusChange(Context context, RemoteViews views) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean canConnect = prefs.contains(SharedPreferencesKeys.SP_KEY_IP);

        views.setBoolean(R.id.widget_btn_play_pause, "setEnabled", false);
        views.setBoolean(R.id.widget_btn_next, "setEnabled", false);

        switch (mCurrentConnectionStatus) {
            case IDLE:
            case DISCONNECTED:
                // Reset play button
                views.setImageViewResource(R.id.widget_btn_play_pause,
                        R.drawable.ic_media_play_light);

                if (canConnect) {
                    // Textviews
                    views.setTextViewText(R.id.widget_subtitle, prefs.getString(
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
                    views.setTextViewText(R.id.widget_subtitle,
                            context.getString(R.string.widget_open_clementine));
                    views.setTextViewText(R.id.widget_title,
                            context.getString(R.string.widget_not_connected));

                    // Start Clementine Remote
                    views.setOnClickPendingIntent(R.id.widget_layout,
                            Utilities.getClementineRemotePendingIntent(context));
                }
                break;
            case CONNECTING:
                views.setTextViewText(R.id.widget_subtitle, "");
                views.setTextViewText(R.id.widget_title,
                        context.getString(R.string.connectdialog_connecting));
                break;
            case NO_CONNECTION:
                views.setTextViewText(R.id.widget_subtitle,
                        context.getString(R.string.widget_open_clementine));
                views.setTextViewText(R.id.widget_title,
                        context.getString(R.string.widget_couldnt_connect));
                // Start Clementine Remote
                views.setOnClickPendingIntent(R.id.widget_layout,
                        Utilities.getClementineRemotePendingIntent(context));
                break;
            case CONNECTED:
                views.setBoolean(R.id.widget_btn_play_pause, "setEnabled", true);
                views.setBoolean(R.id.widget_btn_next, "setEnabled", true);
                break;
        }
    }

    private void updateViewsOnStateChange(Context context, RemoteViews views) {
        MySong currentSong = App.mClementine.getCurrentSong();

        // Textviews
        if (currentSong == null) {
            views.setTextViewText(R.id.widget_subtitle, "");
            views.setTextViewText(R.id.widget_title,
                    context.getString(R.string.player_nosong));
        } else {
            views.setTextViewText(R.id.widget_title, currentSong.getTitle());
            views.setTextViewText(R.id.widget_subtitle,
                    currentSong.getArtist() + " / " + currentSong.getAlbum());
        }

        // Play or pause?
        Intent intentPlayPause = new Intent(context, ClementineBroadcastReceiver.class);

        if (App.mClementine.getState() == Clementine.State.PLAY) {
            views.setImageViewResource(R.id.widget_btn_play_pause,
                    R.drawable.ic_media_pause_light);
            intentPlayPause.setAction(ClementineBroadcastReceiver.PAUSE);
        } else {
            views.setImageViewResource(R.id.widget_btn_play_pause,
                    R.drawable.ic_media_play_light);
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
    }
}
