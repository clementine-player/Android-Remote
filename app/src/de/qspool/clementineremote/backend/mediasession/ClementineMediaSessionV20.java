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
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.receivers.ClementineMediaButtonEventReceiver;

@SuppressWarnings("deprecation")
@TargetApi(20)
public class ClementineMediaSessionV20 extends ClementineMediaSession {

    private AudioManager mAudioManager;

    private ComponentName mClementineMediaButtonEventReceiver;

    private RemoteControlClient mRcClient;

    public ClementineMediaSessionV20(Context context) {
        super(context);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mClementineMediaButtonEventReceiver = new ComponentName(mContext.getPackageName(),
                ClementineMediaButtonEventReceiver.class.getName());
    }

    @Override
    public void registerSession() {
        mAudioManager.registerMediaButtonEventReceiver(mClementineMediaButtonEventReceiver);

        // Create the intent
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mClementineMediaButtonEventReceiver);
        PendingIntent mediaPendingIntent = PendingIntent
                .getBroadcast(mContext,
                        0,
                        mediaButtonIntent,
                        0);

        // Create the client
        mRcClient = new RemoteControlClient(mediaPendingIntent);
        if (App.mClementine.getState() == Clementine.State.PLAY) {
            mRcClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
        } else {
            mRcClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
        }
        mRcClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
                RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
                RemoteControlClient.FLAG_KEY_MEDIA_PAUSE);
        mAudioManager.registerRemoteControlClient(mRcClient);
    }

    @Override
    public void unregisterSession() {
        // Disconnect EventReceiver and RemoteControlClient
        mAudioManager.unregisterMediaButtonEventReceiver(mClementineMediaButtonEventReceiver);

        if (mRcClient != null) {
            mAudioManager.unregisterRemoteControlClient(mRcClient);
        }
    }

    @Override
    public void updateSession() {
        // Update playstate
        if (App.mClementine.getState() == Clementine.State.PLAY) {
            mRcClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
        } else {
            mRcClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
        }

        // Change the data
        MySong song = App.mClementine.getCurrentSong();
        if (song != null && song.getArt() != null) {
            RemoteControlClient.MetadataEditor editor = mRcClient.editMetadata(false);
            editor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, song.getArt());

            // The RemoteControlClients displays the following info:
            // METADATA_KEY_TITLE (white) - METADATA_KEY_ALBUMARTIST (grey) - METADATA_KEY_ALBUM (grey)
            //
            // So I put the metadata not in the "correct" fields to display artist, track and album
            editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, song.getAlbum());
            editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, song.getArtist());
            editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, song.getTitle());
            editor.apply();
        }
    }
}
