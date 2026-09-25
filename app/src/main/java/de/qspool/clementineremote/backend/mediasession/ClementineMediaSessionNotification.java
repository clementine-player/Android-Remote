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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.DrawableRes;
import androidx.annotation.OptIn;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaStyleNotificationHelper;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.receivers.ClementineBroadcastReceiver;
import de.qspool.clementineremote.utils.Utilities;

/**
 * The player notification: a media notification for the media session, which Android shows
 * with the session's controls and artwork (on Android 13+ also on the lockscreen and in quick
 * settings). It replaces the service's "Connecting…" notification, using the same id.
 */
@OptIn(markerClass = UnstableApi.class)
public class ClementineMediaSessionNotification {

    public final static int NOTIFIFCATION_ID = 78923748;

    public final static String EXTRA_NOTIFICATION_ID = "NotificationID";

    private final Context mContext;

    private final NotificationManager mNotificationManager;

    public ClementineMediaSessionNotification(Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
    }

    public void update(MediaSession session) {
        MySong song = App.Clementine.getCurrentSong();
        boolean playing = App.Clementine.getState() == Clementine.State.PLAY;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext,
                App.notificationChannel)
                .setSmallIcon(R.drawable.notification)
                .setContentIntent(Utilities.getClementineRemotePendingIntent(mContext))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                // Before Android 13 the notification's own actions are the controls.
                .addAction(action(R.drawable.ic_media_previous, R.string.notification_previous,
                        ClementineBroadcastReceiver.PREVIOUS))
                .addAction(playing
                        ? action(R.drawable.ic_media_pause, R.string.notification_pause,
                                ClementineBroadcastReceiver.PAUSE)
                        : action(R.drawable.ic_media_play, R.string.notification_play,
                                ClementineBroadcastReceiver.PLAY))
                .addAction(action(R.drawable.ic_media_next, R.string.notification_next,
                        ClementineBroadcastReceiver.NEXT))
                .setStyle(new MediaStyleNotificationHelper.MediaStyle(session)
                        .setShowActionsInCompactView(0, 1, 2));

        if (song != null) {
            builder.setContentTitle(song.getTitle())
                    .setContentText(song.getArtist() + " / " + song.getAlbum())
                    .setLargeIcon(song.getArt());
        } else {
            builder.setContentTitle(mContext.getString(R.string.app_name))
                    .setContentText(mContext.getString(R.string.player_nosong));
        }

        Notification notification = builder.build();
        mNotificationManager.notify(NOTIFIFCATION_ID, notification);
    }

    public void cancel() {
        mNotificationManager.cancel(NOTIFIFCATION_ID);
    }

    private NotificationCompat.Action action(@DrawableRes int icon, @StringRes int title,
            String broadcastAction) {
        Intent intent = new Intent(mContext, ClementineBroadcastReceiver.class)
                .setAction(broadcastAction);
        // One request code per action, so the actions' intents don't replace each other.
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext,
                broadcastAction.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(icon, mContext.getString(title), pendingIntent);
    }
}
