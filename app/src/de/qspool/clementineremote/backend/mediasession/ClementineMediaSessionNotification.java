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

package de.qspool.clementineremote.backend.mediasession;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.os.Build;
import android.support.v4.app.TaskStackBuilder;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.receivers.NotificationReceiver;
import de.qspool.clementineremote.ui.MainActivity;

public class ClementineMediaSessionNotification extends ClementineMediaSession {

    private NotificationManager mNotificationManager;

    private Notification.Builder mNotificationBuilder;

    private int mNotificationWidth;

    private int mNotificationHeight;

    public ClementineMediaSessionNotification(Context context) {
        super(context);
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);

    }

    @Override
    public void registerSession() {
        Resources res = mContext.getResources();
        mNotificationHeight = (int) res
                .getDimension(android.R.dimen.notification_large_icon_height);
        mNotificationWidth = (int) res.getDimension(android.R.dimen.notification_large_icon_width);

        mNotificationBuilder = new Notification.Builder(App.mApp);
        mNotificationBuilder.setSmallIcon(R.drawable.notification);
        mNotificationBuilder.setOngoing(true);

        // Set the result intent
        Intent resultIntent = new Intent(App.mApp, MainActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resultIntent.putExtra(App.NOTIFICATION_ID, -1);

        // Create a TaskStack, so the app navigates correctly backwards
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(App.mApp);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingintent = stackBuilder
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mNotificationBuilder.setContentIntent(resultPendingintent);

        // Create intents for buttons
        Intent playIntent = new Intent(NotificationReceiver.PLAYPAUSE);
        Intent nextIntent = new Intent(NotificationReceiver.NEXT);

        PendingIntent piPlay = PendingIntent.getBroadcast(App.mApp, 0, playIntent, 0);
        PendingIntent piNext = PendingIntent.getBroadcast(App.mApp, 0, nextIntent, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mNotificationBuilder.addAction(R.drawable.ic_media_pause_resume,
                    App.mApp.getString(R.string.notification_action_playpause), piPlay);
            mNotificationBuilder.addAction(R.drawable.ic_media_next_not,
                    App.mApp.getString(R.string.notification_action_next), piNext);

            mNotificationBuilder.setPriority(1);
        }
    }

    @Override
    public void unregisterSession() {
        mNotificationManager.cancel(App.NOTIFY_ID);
    }

    @Override
    public void updateSession() {
        MySong song = App.mClementine.getCurrentSong();
        if (song != null) {
            Bitmap scaledArt = Bitmap.createScaledBitmap(song.getArt(),
                    mNotificationWidth,
                    mNotificationHeight,
                    false);
            mNotificationBuilder.setLargeIcon(scaledArt);
            mNotificationBuilder.setContentTitle(song.getArtist());
            mNotificationBuilder.setContentText(song.getTitle() +
                    " / " +
                    song.getAlbum());
        } else {
            mNotificationBuilder.setContentTitle(App.mApp.getString(R.string.app_name));
            mNotificationBuilder.setContentText(App.mApp.getString(R.string.player_nosong));
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            //noinspection deprecation
            mNotificationManager.notify(App.NOTIFY_ID, mNotificationBuilder.getNotification());
        } else {
            mNotificationManager.notify(App.NOTIFY_ID, mNotificationBuilder.build());
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setMediaSessionCompat(MediaSession mediaSession) {
        if (mediaSession == null) {
            return;
        }

        mNotificationBuilder.setStyle(new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken()));
    }
}
