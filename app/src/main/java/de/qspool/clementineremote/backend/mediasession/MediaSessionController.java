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

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.utils.Utilities;
import de.qspool.clementineremote.widget.ClementineWidget;

/**
 * Publishes Clementine's playback to the rest of the system while connected: a Media3 media
 * session (lockscreen, Bluetooth, watches, other apps' media controllers), the player
 * notification, the home-screen widget, and the "music changed" broadcasts scrobblers read.
 *
 * <p>The connection reports on its own thread; the session lives on the main thread.
 */
@OptIn(markerClass = UnstableApi.class)
public class MediaSessionController {

    private final String PLAYSTATE_CHANGED = "com.android.music.playstatechanged";

    private final String META_CHANGED = "com.android.music.metachanged";

    private final Context mContext;

    private final ClementinePlayerConnection mClementinePlayerConnection;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final ClementinePlayer mPlayer;

    private final ClementineMediaSessionNotification mNotification;

    @Nullable
    private MediaSession mSession;

    /** The media session while connected, for the app's screens to hand the volume keys to. */
    @Nullable
    private static volatile android.media.session.MediaSession.Token sPlatformToken;

    /** Set when the connection was lost, so the service's "connection lost" notice stays. */
    private volatile boolean mLostConnection;

    public MediaSessionController(Context context,
            ClementinePlayerConnection clementinePlayerConnection) {
        mContext = context;
        mClementinePlayerConnection = clementinePlayerConnection;
        mPlayer = new ClementinePlayer(Looper.getMainLooper());
        mNotification = new ClementineMediaSessionNotification(mContext);
    }

    public void registerMediaSession() {
        mClementinePlayerConnection.addPlayerConnectionListener(new PlayerConnectionListener() {
            @Override
            public void onConnectionStatusChanged(
                    ClementinePlayerConnection.ConnectionStatus status) {
                switch (status) {
                    case CONNECTED:
                        mLostConnection = false;
                        mMainHandler.post(MediaSessionController.this::startSession);
                        break;
                    case LOST_CONNECTION:
                        mLostConnection = true;
                        break;
                    case DISCONNECTED:
                        boolean keepNotification = mLostConnection;
                        mMainHandler.post(() -> stopSession(keepNotification));
                        break;
                    default:
                        break;
                }
                ClementineWidget.update(mContext);
            }

            @Override
            public void onClementineMessageReceived(ClementineMessage clementineMessage) {
                if (clementineMessage.isErrorMessage()) {
                    return;
                }

                switch (clementineMessage.getMessageType()) {
                    case CURRENT_METAINFO:
                        mPlayer.invalidate();
                        mMainHandler.post(MediaSessionController.this::updateNotification);
                        sendMetachangedIntent(META_CHANGED);
                        ClementineWidget.update(mContext);
                        break;
                    case PLAY:
                    case PAUSE:
                    case STOP:
                        mPlayer.invalidate();
                        mMainHandler.post(MediaSessionController.this::updateNotification);
                        sendMetachangedIntent(PLAYSTATE_CHANGED);
                        ClementineWidget.update(mContext);
                        break;
                    case UPDATE_TRACK_POSITION:
                    case REPEAT:
                    case SHUFFLE:
                    case SET_VOLUME:
                        mPlayer.invalidate();
                        break;
                    case FIRST_DATA_SENT_COMPLETE:
                        mPlayer.invalidate();
                        ClementineWidget.update(mContext);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void startSession() {
        if (mSession == null) {
            mSession = new MediaSession.Builder(mContext, mPlayer.asSessionPlayer())
                    .setId("clementine")
                    .setSessionActivity(Utilities.getClementineRemotePendingIntent(mContext))
                    .setCallback(new MediaSession.Callback() {
                        @Override
                        public ListenableFuture<SessionResult> onSetRating(
                                MediaSession session, MediaSession.ControllerInfo controller,
                                Rating rating) {
                            if (!(rating instanceof StarRating) || !rating.isRated()) {
                                return Futures.immediateFuture(
                                        new SessionResult(SessionError.ERROR_BAD_VALUE));
                            }
                            StarRating stars = (StarRating) rating;
                            mPlayer.rate(stars.getStarRating() * 5 / stars.getMaxStars());
                            return Futures.immediateFuture(
                                    new SessionResult(SessionResult.RESULT_SUCCESS));
                        }
                    })
                    .build();
            sPlatformToken = mSession.getPlatformToken();
        }
        mPlayer.setConnected(true);
    }

    private void stopSession(boolean keepNotification) {
        mPlayer.setConnected(false);
        if (mSession != null) {
            sPlatformToken = null;
            mSession.release();
            mSession = null;
        }
        if (!keepNotification) {
            mNotification.cancel();
        }
    }

    /**
     * The media session while connected to Clementine, as Android's own session token: a screen
     * given a controller for it ({@code Activity#setMediaController}) sends the volume keys to
     * Clementine, and Android shows its volume panel for them. Null while not connected.
     */
    @Nullable
    public static android.media.session.MediaSession.Token getPlatformToken() {
        return sPlatformToken;
    }

    private void updateNotification() {
        if (mSession != null) {
            mNotification.update(mSession);
        }
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
}
