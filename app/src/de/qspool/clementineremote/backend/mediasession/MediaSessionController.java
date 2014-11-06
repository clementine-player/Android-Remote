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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.receivers.ClementineMediaButtonEventReceiver;

public class MediaSessionController {

    private final String PLAYSTATE_CHANGED = "com.android.music.playstatechanged";

    private final String META_CHANGED = "com.android.music.metachanged";

    private Context mContext;

    private ClementinePlayerConnection mClementinePlayerConnection;

    private ClementineMediaSession mClementineMediaSession;

    private ClementineMediaSessionNotification mMediaSessionNotification;

    private AudioManager mAudioManager;

    private BroadcastReceiver mMediaButtonBroadcastReceiver;

    public MediaSessionController(Context context,
            ClementinePlayerConnection clementinePlayerConnection) {
        mContext = context;
        mClementinePlayerConnection = clementinePlayerConnection;

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mMediaButtonBroadcastReceiver = new ClementineMediaButtonEventReceiver();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mClementineMediaSession = new ClementineMediaSessionV20(mContext);
        } else {
            mClementineMediaSession = new ClementineMediaSessionV21(mContext);
        }
        mMediaSessionNotification = new ClementineMediaSessionNotification(mContext);
    }

    public void registerMediaSession() {
        mClementinePlayerConnection.addPlayerConnectionListener(new PlayerConnectionListener() {
            @Override
            public void onThreadStarted() {
            }

            @Override
            public void onConnected() {
                // Request AudioFocus, so the widget is shown on the lock-screen
                mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);

                // Register MediaButtonReceiver
                IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
                mContext.registerReceiver(mMediaButtonBroadcastReceiver, filter);

                mClementineMediaSession.registerSession();
                mMediaSessionNotification.registerSession();
                mMediaSessionNotification.setMediaSessionCompat(
                        mClementineMediaSession.getMediaSession());
            }

            @Override
            public void onConnectionClosed(ClementineMessage clementineMessage) {
                mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
                mContext.unregisterReceiver(mMediaButtonBroadcastReceiver);

                mClementineMediaSession.unregisterSession();
                mMediaSessionNotification.unregisterSession();
            }

            @Override
            public void onClementineMessageReceived(ClementineMessage clementineMessage) {
                if (clementineMessage.isErrorMessage()) {
                    return;
                }

                switch (clementineMessage.getMessageType()) {
                    case CURRENT_METAINFO:
                        mClementineMediaSession.updateSession();
                        mMediaSessionNotification.updateSession();
                        sendMetachangedIntent(META_CHANGED);
                        break;
                    case PLAY:
                    case PAUSE:
                    case STOP:
                        mClementineMediaSession.updateSession();
                        mMediaSessionNotification.updateSession();
                        sendMetachangedIntent(PLAYSTATE_CHANGED);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void sendMetachangedIntent(String what) {
        MySong currentSong = App.mClementine.getCurrentSong();
        Intent i = new Intent(what);
        i.putExtra("playing", App.mClementine.getState() == Clementine.State.PLAY);
        if (null != currentSong) {
            i.putExtra("id", Long.valueOf(currentSong.getId()));
            i.putExtra("artist", currentSong.getArtist());
            i.putExtra("album", currentSong.getAlbum());
            i.putExtra("track", currentSong.getTitle());
        }

        mContext.sendBroadcast(i);
    }

    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
        }
    };
}
