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

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
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
import de.qspool.clementineremote.widget.ClementineWidgetProvider;
import de.qspool.clementineremote.widget.WidgetIntent;

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
            public void onConnectionStatusChanged(
                    ClementinePlayerConnection.ConnectionStatus status) {
                switch (status) {
                    case IDLE:
                        break;
                    case CONNECTING:
                        break;
                    case NO_CONNECTION:
                        break;
                    case CONNECTED:
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
                        break;
                    case DISCONNECTED:
                        mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
                        mContext.unregisterReceiver(mMediaButtonBroadcastReceiver);

                        mClementineMediaSession.unregisterSession();
                        mMediaSessionNotification.unregisterSession();
                        break;
                }
                sendWidgetUpdateIntent(WidgetIntent.ClementineAction.CONNECTION_STATUS, status);
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
                        sendWidgetUpdateIntent(WidgetIntent.ClementineAction.STATE_CHANGE,
                                ClementinePlayerConnection.ConnectionStatus.CONNECTED);
                        break;
                    case PLAY:
                    case PAUSE:
                    case STOP:
                        mClementineMediaSession.updateSession();
                        mMediaSessionNotification.updateSession();
                        sendMetachangedIntent(PLAYSTATE_CHANGED);
                        sendWidgetUpdateIntent(WidgetIntent.ClementineAction.STATE_CHANGE,
                                ClementinePlayerConnection.ConnectionStatus.CONNECTED);
                        break;
                    case FIRST_DATA_SENT_COMPLETE:
                        sendWidgetUpdateIntent(WidgetIntent.ClementineAction.STATE_CHANGE,
                                ClementinePlayerConnection.ConnectionStatus.CONNECTED);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void sendMetachangedIntent(String what) {
        MySong currentSong = App.Clementine.getCurrentSong();
        Intent i = new Intent(what);
        i.putExtra("playing", App.Clementine.getState() == Clementine.State.PLAY);
        if (null != currentSong) {
            i.putExtra("id", Long.valueOf(currentSong.getId()));
            i.putExtra("artist", currentSong.getArtist());
            i.putExtra("album", currentSong.getAlbum());
            i.putExtra("track", currentSong.getTitle());
        }

        mContext.sendBroadcast(i);
    }

    private void sendWidgetUpdateIntent(WidgetIntent.ClementineAction action,
            ClementinePlayerConnection.ConnectionStatus connectionStatus) {
        // Get widget ids
        ComponentName widgetComponent = new ComponentName(mContext.getPackageName(),
                ClementineWidgetProvider.class.getName());
        int[] widgetIds = AppWidgetManager.getInstance(mContext).getAppWidgetIds(widgetComponent);

        if (widgetIds.length > 0) {
            Intent intent = new Intent(mContext, ClementineWidgetProvider.class);
            intent.setAction(WidgetIntent.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(WidgetIntent.EXTRA_APPWIDGET_IDS, widgetIds);
            intent.putExtra(WidgetIntent.EXTRA_CLEMENTINE_ACTION, action.ordinal());
            intent.putExtra(WidgetIntent.EXTRA_CLEMENTINE_CONNECTION_STATE,
                    connectionStatus.ordinal());

            mContext.sendBroadcast(intent);
        }
    }

    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
        }
    };
}
