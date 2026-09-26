package de.qspool.clementineremote.backend.mediasession;

import android.os.Looper;

import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Player;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.MySong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/** The media session's view of Clementine, and the commands it sends back. */
@RunWith(RobolectricTestRunner.class)
public class ClementinePlayerTest {

    private final List<ClementineMessage> mSent = new ArrayList<>();

    private ClementinePlayer mPlayer;

    @Before
    public void setUp() {
        App.Clementine = new Clementine();
        MySong song = new MySong();
        song.setId(7);
        song.setTitle("Clair de lune");
        song.setArtist("Claude Debussy");
        song.setAlbum("Suite bergamasque");
        song.setLength(300);
        App.Clementine.setCurrentSong(song);
        App.Clementine.setSongPosition(30);
        App.Clementine.setState(Clementine.State.PLAY);

        mPlayer = new ClementinePlayer(Looper.getMainLooper(), mSent::add);
        mPlayer.setConnected(true);
        idle();
    }

    @After
    public void tearDown() {
        mPlayer.release();
        App.Clementine = new Clementine();
        App.getPreferences().edit().clear().commit();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private MsgType lastSent() {
        return mSent.get(mSent.size() - 1).getMessageType();
    }

    @Test
    public void showsTheCurrentSong() {
        assertEquals("Clair de lune", mPlayer.getMediaMetadata().title.toString());
        assertEquals("Claude Debussy", mPlayer.getMediaMetadata().artist.toString());
        assertEquals("Suite bergamasque", mPlayer.getMediaMetadata().albumTitle.toString());
        assertEquals(300_000, mPlayer.getDuration());
        assertTrue(mPlayer.getCurrentPosition() >= 30_000);
        assertTrue(mPlayer.isPlaying());
        assertTrue(mPlayer.isCurrentMediaItemSeekable());
    }

    @Test
    public void followsClementine() {
        App.Clementine.setState(Clementine.State.PAUSE);
        App.Clementine.setSongPosition(95);
        mPlayer.invalidate();
        idle();

        assertFalse(mPlayer.isPlaying());
        assertEquals(Player.STATE_READY, mPlayer.getPlaybackState());
        assertEquals(95_000, mPlayer.getCurrentPosition());

        App.Clementine.setState(Clementine.State.STOP);
        mPlayer.invalidate();
        idle();
        assertEquals(Player.STATE_IDLE, mPlayer.getPlaybackState());
    }

    @Test
    public void disconnectedShowsNothing() {
        mPlayer.setConnected(false);
        idle();

        assertEquals(Player.STATE_IDLE, mPlayer.getPlaybackState());
        assertNull(mPlayer.getCurrentMediaItem());
        assertFalse(mPlayer.isCommandAvailable(Player.COMMAND_PLAY_PAUSE));
    }

    @Test
    public void sendsPlayAndPause() {
        mPlayer.pause();
        assertEquals(MsgType.PAUSE, lastSent());
        mPlayer.play();
        assertEquals(MsgType.PLAY, lastSent());
        mPlayer.stop();
        assertEquals(MsgType.STOP, lastSent());
    }

    @Test
    public void sendsSeeksInSeconds() {
        mPlayer.seekTo(61_500);

        assertEquals(MsgType.SET_TRACK_POSITION, lastSent());
        assertEquals(61, mSent.get(0).getMessage().getRequestSetTrackPosition().getPosition());
    }

    @Test
    public void skipsThroughClementine() {
        Player sessionPlayer = mPlayer.asSessionPlayer();
        assertTrue(sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT));
        assertTrue(sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS));

        sessionPlayer.seekToNext();
        assertEquals(MsgType.NEXT, lastSent());
        sessionPlayer.seekToPrevious();
        assertEquals(MsgType.PREVIOUS, lastSent());
    }

    @Test
    public void setsShuffleAndRepeat() {
        mPlayer.setShuffleModeEnabled(true);
        assertEquals(MsgType.SHUFFLE, lastSent());
        assertEquals(Clementine.ShuffleMode.ALL, App.Clementine.getShuffleMode());

        mPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
        assertEquals(MsgType.REPEAT, lastSent());
        assertEquals(Clementine.RepeatMode.TRACK, App.Clementine.getRepeatMode());
    }

    @Test
    public void ratesInClementinesRange() {
        mPlayer.rate(4);

        assertEquals(MsgType.RATE_SONG, lastSent());
        assertEquals(0.8f, mSent.get(0).getMessage().getRequestRateSong().getRating(), 0.001f);
    }

    private int lastVolume() {
        return mSent.get(mSent.size() - 1).getMessage().getRequestSetVolume().getVolume();
    }

    @Test
    public void clementinesVolumeIsARemoteDevices() {
        App.Clementine.setVolume(64);
        mPlayer.invalidate();
        idle();

        assertEquals(DeviceInfo.PLAYBACK_TYPE_REMOTE, mPlayer.getDeviceInfo().playbackType);
        assertEquals(0, mPlayer.getDeviceInfo().minVolume);
        assertEquals(100, mPlayer.getDeviceInfo().maxVolume);
        assertEquals(64, mPlayer.getDeviceVolume());
        assertTrue(mPlayer.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS));
        assertTrue(mPlayer.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS));
    }

    @Test
    public void volumeKeysStepClementinesVolume() {
        App.getPreferences().edit().putString(SharedPreferencesKeys.SP_VOLUME_INC, "5").commit();
        App.Clementine.setVolume(50);
        mPlayer.invalidate();
        idle();

        mPlayer.increaseDeviceVolume(0);
        idle();
        assertEquals(MsgType.SET_VOLUME, lastSent());
        assertEquals(55, lastVolume());
        // Shown at once, before Clementine confirms it.
        assertEquals(55, mPlayer.getDeviceVolume());

        mPlayer.decreaseDeviceVolume(0);
        mPlayer.decreaseDeviceVolume(0);
        idle();
        assertEquals(45, lastVolume());

        mPlayer.setDeviceVolume(120, 0);
        idle();
        assertEquals(100, lastVolume());
    }

    @Test
    public void withoutVolumeKeysTheVolumeIsThePhones() {
        App.getPreferences().edit().putBoolean(SharedPreferencesKeys.SP_KEY_USE_VOLUMEKEYS, false).commit();
        mPlayer.invalidate();
        idle();

        assertEquals(DeviceInfo.PLAYBACK_TYPE_LOCAL, mPlayer.getDeviceInfo().playbackType);
        assertFalse(mPlayer.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS));
        assertFalse(mPlayer.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS));
    }
}
