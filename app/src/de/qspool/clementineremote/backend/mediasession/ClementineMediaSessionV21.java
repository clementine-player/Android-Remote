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
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Message;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.receivers.ClementineMediaButtonEventReceiver;

@TargetApi(21)
public class ClementineMediaSessionV21 extends ClementineMediaSession {

    private final String TAG = getClass().getSimpleName();

    private MediaSession mMediaSession;

    public ClementineMediaSessionV21(Context context) {
        super(context);
    }

    @Override
    public void registerSession() {
        mMediaSession = new MediaSession(mContext, TAG);
        mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setActive(true);

        mMediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                Message msg = Message.obtain();
                msg.obj = ClementineMessage.getMessage(MsgType.PLAY);
                App.ClementineConnection.mHandler.sendMessage(msg);
            }

            @Override
            public void onPause() {
                Message msg = Message.obtain();
                msg.obj = ClementineMessage.getMessage(MsgType.PAUSE);
                App.ClementineConnection.mHandler.sendMessage(msg);
            }

            @Override
            public void onSkipToNext() {
                Message msg = Message.obtain();
                msg.obj = ClementineMessage.getMessage(MsgType.NEXT);
                App.ClementineConnection.mHandler.sendMessage(msg);
            }

            @Override
            public void onSkipToPrevious() {
                Message msg = Message.obtain();
                msg.obj = ClementineMessage.getMessage(MsgType.PREVIOUS);
                App.ClementineConnection.mHandler.sendMessage(msg);
            }

            @Override
            public void onStop() {
                Message msg = Message.obtain();
                msg.obj = ClementineMessage.getMessage(MsgType.STOP);
                App.ClementineConnection.mHandler.sendMessage(msg);
            }

            @Override
            public void onSeekTo(long pos) {
                Message msg = Message.obtain();
                msg.obj = ClementineMessageFactory.buildTrackPosition((int) pos);
                App.ClementineConnection.mHandler.sendMessage(msg);
            }

            @Override
            public void onSetRating(Rating rating) {
                Message msg = Message.obtain();
                msg.obj = ClementineMessageFactory.buildRateTrack(rating.getStarRating());
                App.ClementineConnection.mHandler.sendMessage(msg);
            }
        });

        // Create the intent
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(new ComponentName(mContext.getPackageName(),
                ClementineMediaButtonEventReceiver.class.getName()));
        PendingIntent mediaPendingIntent = PendingIntent
                .getBroadcast(mContext,
                        0,
                        mediaButtonIntent,
                        0);
        mMediaSession.setMediaButtonReceiver(mediaPendingIntent);
    }

    @Override
    public void unregisterSession() {
        mMediaSession.setActive(false);
        mMediaSession.release();
    }

    @Override
    public void updateSession() {
        // Update playstate
        updatePlayState();

        // Change the data
        updateMetaData();
    }

    @Override
    public MediaSession getMediaSession() {
        return mMediaSession;
    }

    private void updatePlayState() {
        PlaybackState.Builder builder = new PlaybackState.Builder();
        switch (App.Clementine.getState()) {
            case PLAY:
                builder.setState(PlaybackState.STATE_PLAYING, App.Clementine.getSongPosition(),
                        1.0f);
                break;
            case PAUSE:
                builder.setState(PlaybackState.STATE_PAUSED, App.Clementine.getSongPosition(),
                        1.0f);
                break;
            case STOP:
                builder.setState(PlaybackState.STATE_STOPPED, 0, 1.0f);
                break;
            default:
                break;
        }

        mMediaSession.setPlaybackState(builder.build());
    }

    private void updateMetaData() {
        MySong song = App.Clementine.getCurrentSong();
        if (song != null && song.getArt() != null) {
            MediaMetadata.Builder builder = new MediaMetadata.Builder();
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, song.getArt());
            builder.putString(MediaMetadata.METADATA_KEY_ALBUM, song.getAlbum());
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, song.getTitle());
            builder.putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, song.getAlbumartist());
            builder.putString(MediaMetadata.METADATA_KEY_ARTIST, song.getArtist());

            mMediaSession.setMetadata(builder.build());
        }
    }
}
