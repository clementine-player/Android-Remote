/* This file is part of the Android Clementine Remote.
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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.StarRating;
import androidx.media3.common.util.UnstableApi;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.MySong;

/**
 * Clementine, seen as a Media3 player: its state is what Clementine last reported
 * ({@link App#Clementine}), and its commands are sent to Clementine. The media session, and
 * through it the notification, lockscreen, Bluetooth and watch controls, read and drive it.
 *
 * <p>Only Clementine's current song is exposed, so skipping is done by {@link #asSessionPlayer()}.
 * All methods run on the main thread; {@link #invalidate()} may be called from any thread.
 */
@OptIn(markerClass = UnstableApi.class)
public class ClementinePlayer extends SimpleBasePlayer {

    /** Where commands for Clementine go. */
    public interface Sender {

        void send(ClementineMessage message);
    }

    private static final Commands COMMANDS_WITHOUT_SONG = new Commands.Builder()
            .addAll(COMMAND_PLAY_PAUSE, COMMAND_STOP, COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_PREVIOUS, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA,
                    COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE)
            .build();

    private static final Commands COMMANDS = COMMANDS_WITHOUT_SONG.buildUpon()
            .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
            .build();

    private final Sender mSender;

    private final Handler mHandler;

    private boolean mConnected;

    public ClementinePlayer(Looper looper) {
        this(looper, ClementinePlayer::sendToClementine);
    }

    @VisibleForTesting
    ClementinePlayer(Looper looper, Sender sender) {
        super(looper);
        mSender = sender;
        mHandler = new Handler(looper);
    }

    private static void sendToClementine(ClementineMessage message) {
        if (App.ClementineConnection != null) {
            Message msg = Message.obtain();
            msg.obj = message;
            App.ClementineConnection.mHandler.sendMessage(msg);
        }
    }

    /** Re-reads Clementine's state, after a message from Clementine changed it. */
    public void invalidate() {
        mHandler.post(this::invalidateState);
    }

    /** Only a connected player has anything to show or control. */
    public void setConnected(boolean connected) {
        mConnected = connected;
        invalidate();
    }

    @Override
    protected State getState() {
        State.Builder state = new State.Builder();
        if (!mConnected) {
            return state.setPlaybackState(STATE_IDLE).build();
        }

        Clementine clementine = App.Clementine;
        MySong song = clementine.getCurrentSong();
        boolean playing = clementine.getState() == Clementine.State.PLAY;
        state.setPlayWhenReady(playing, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                .setRepeatMode(repeatMode(clementine.getRepeatMode()))
                .setShuffleModeEnabled(clementine.getShuffleMode() != Clementine.ShuffleMode.OFF);

        if (song == null) {
            return state.setAvailableCommands(COMMANDS_WITHOUT_SONG)
                    .setPlaybackState(STATE_IDLE)
                    .build();
        }

        Commands.Builder commands = COMMANDS.buildUpon();
        if (song.getLength() > 0) {
            commands.add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
        }
        long positionMs = clementine.getSongPosition() * 1000L;
        return state.setAvailableCommands(commands.build())
                .setPlaybackState(clementine.getState() == Clementine.State.STOP
                        ? STATE_IDLE : STATE_READY)
                .setPlaylist(ImmutableList.of(mediaItem(song)))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(playing
                        ? PositionSupplier.getExtrapolating(positionMs, 1f)
                        : PositionSupplier.getConstant(positionMs))
                .build();
    }

    private static MediaItemData mediaItem(MySong song) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(song.getTitle())
                .setArtist(song.getArtist())
                .setAlbumTitle(song.getAlbum())
                .setAlbumArtist(song.getAlbumartist())
                .setGenre(song.getGenre())
                .setUserRating(song.getRating() >= 0 && song.getRating() <= 1
                        ? new StarRating(5, song.getRating() * 5) : new StarRating(5))
                .setIsPlayable(true)
                .setIsBrowsable(false);
        if (song.getTrack() > 0) {
            metadata.setTrackNumber(song.getTrack());
        }
        if (song.getLength() > 0) {
            metadata.setDurationMs(song.getLength() * 1000L);
        }
        byte[] art = song.getArtData();
        if (art != null) {
            metadata.setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
        }

        MediaItem item = new MediaItem.Builder()
                .setMediaId(String.valueOf(song.getId()))
                .setMediaMetadata(metadata.build())
                .build();
        // A new uid per song, so a change of song is a transition to a new item.
        return new MediaItemData.Builder(song.getId() + ":" + song.getIndex())
                .setMediaItem(item)
                .setDurationUs(song.getLength() > 0 ? song.getLength() * 1_000_000L : C.TIME_UNSET)
                .setIsSeekable(song.getLength() > 0)
                .build();
    }

    private static @RepeatMode int repeatMode(Clementine.RepeatMode mode) {
        switch (mode) {
            case TRACK:
                return REPEAT_MODE_ONE;
            case ALBUM:
            case PLAYLIST:
                return REPEAT_MODE_ALL;
            default:
                return REPEAT_MODE_OFF;
        }
    }

    private ListenableFuture<?> send(ClementineMessage message) {
        mSender.send(message);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        return send(ClementineMessage.getMessage(playWhenReady ? MsgType.PLAY : MsgType.PAUSE));
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        return send(ClementineMessage.getMessage(MsgType.STOP));
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs,
            @Player.Command int seekCommand) {
        if (seekCommand == COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM && positionMs != C.TIME_UNSET) {
            return send(ClementineMessageFactory.buildTrackPosition((int) (positionMs / 1000)));
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(@RepeatMode int repeatMode) {
        switch (repeatMode) {
            case REPEAT_MODE_ONE:
                App.Clementine.setRepeatMode(Clementine.RepeatMode.TRACK);
                break;
            case REPEAT_MODE_ALL:
                App.Clementine.setRepeatMode(Clementine.RepeatMode.PLAYLIST);
                break;
            default:
                App.Clementine.setRepeatMode(Clementine.RepeatMode.OFF);
                break;
        }
        return send(ClementineMessageFactory.buildRepeat());
    }

    @Override
    protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
        App.Clementine.setShuffleMode(shuffleModeEnabled
                ? Clementine.ShuffleMode.ALL : Clementine.ShuffleMode.OFF);
        return send(ClementineMessageFactory.buildShuffle());
    }

    /** Rates the current song, from 0 to 5 stars. */
    public void rate(float stars) {
        mSender.send(ClementineMessageFactory.buildRateTrack(stars / 5));
    }

    /**
     * This player for the media session. The player only holds Clementine's current song, so
     * next and previous would have nowhere to go; here they ask Clementine instead.
     */
    public Player asSessionPlayer() {
        return new ForwardingPlayer(this) {
            @Override
            public void seekToNext() {
                mSender.send(ClementineMessage.getMessage(MsgType.NEXT));
            }

            @Override
            public void seekToNextMediaItem() {
                seekToNext();
            }

            @Override
            public void seekToPrevious() {
                mSender.send(ClementineMessage.getMessage(MsgType.PREVIOUS));
            }

            @Override
            public void seekToPreviousMediaItem() {
                seekToPrevious();
            }
        };
    }
}
