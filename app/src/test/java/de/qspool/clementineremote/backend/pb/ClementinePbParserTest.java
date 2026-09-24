package de.qspool.clementineremote.backend.pb;

import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.EngineState;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Playlist;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Repeat;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RepeatMode;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSetVolume;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseCurrentMetadata;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponsePlaylists;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Shuffle;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ShuffleMode;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata;
import de.qspool.clementineremote.backend.player.MySong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ClementinePbParserTest {

    private ClementinePbParser mParser;

    @Before
    public void setUp() {
        App.Clementine = new Clementine();
        App.ClementineConnection = null;
        mParser = new ClementinePbParser();
    }

    private static Message.Builder message(MsgType type) {
        return ClementineMessage.getMessageBuilder(type);
    }

    private ClementineMessage parse(Message.Builder builder) {
        return mParser.parse(builder.build().toByteArray());
    }

    @Test
    public void rejectsGarbage() {
        ClementineMessage result = mParser.parse(new byte[]{(byte) 0xff, 0x01, 0x02});
        assertTrue(result.isErrorMessage());
        assertEquals(ErrorMessage.INVALID_DATA, result.getErrorMessage());
    }

    @Test
    public void rejectsOldProtocolVersion() {
        Message.Builder msg = message(MsgType.PLAY)
                .setVersion(Message.getDefaultInstance().getVersion() - 1);
        assertEquals(ErrorMessage.OLD_PROTO, parse(msg).getErrorMessage());
    }

    @Test
    public void info() {
        Message.Builder msg = message(MsgType.INFO).setResponseClementineInfo(
                ResponseClementineInfo.newBuilder()
                        .setVersion("Clementine 1.4.1")
                        .setState(EngineState.Playing));

        ClementineMessage result = parse(msg);

        assertFalse(result.isErrorMessage());
        assertEquals(MsgType.INFO, result.getMessageType());
        assertEquals("Clementine 1.4.1", App.Clementine.getVersion());
        assertEquals(Clementine.State.PLAY, App.Clementine.getState());
    }

    @Test
    public void currentMetainfo() {
        Message.Builder msg = message(MsgType.CURRENT_METAINFO).setResponseCurrentMetadata(
                ResponseCurrentMetadata.newBuilder().setSongMetadata(SongMetadata.newBuilder()
                        .setId(11)
                        .setTitle("Title")
                        .setArtist("Artist")
                        .setAlbum("Album")
                        .setLength(180)
                        .setArt(ByteString.EMPTY)));

        parse(msg);

        MySong song = App.Clementine.getCurrentSong();
        assertEquals(11, song.getId());
        assertEquals("Title", song.getTitle());
        assertEquals("Artist", song.getArtist());
        assertEquals("Album", song.getAlbum());
        assertEquals(180, song.getLength());
        assertEquals(0, App.Clementine.getSongPosition());
    }

    @Test
    public void playPauseStop() {
        parse(message(MsgType.PLAY));
        assertEquals(Clementine.State.PLAY, App.Clementine.getState());
        parse(message(MsgType.PAUSE));
        assertEquals(Clementine.State.PAUSE, App.Clementine.getState());
        parse(message(MsgType.STOP));
        assertEquals(Clementine.State.STOP, App.Clementine.getState());
    }

    @Test
    public void volume() {
        parse(message(MsgType.SET_VOLUME)
                .setRequestSetVolume(RequestSetVolume.newBuilder().setVolume(33)));
        assertEquals(33, App.Clementine.getVolume());
    }

    @Test
    public void repeatAndShuffle() {
        parse(message(MsgType.REPEAT)
                .setRepeat(Repeat.newBuilder().setRepeatMode(RepeatMode.Repeat_Album)));
        assertEquals(Clementine.RepeatMode.ALBUM, App.Clementine.getRepeatMode());

        parse(message(MsgType.SHUFFLE)
                .setShuffle(Shuffle.newBuilder().setShuffleMode(ShuffleMode.Shuffle_Albums)));
        assertEquals(Clementine.ShuffleMode.ALBUMS, App.Clementine.getShuffleMode());
    }

    @Test
    public void playlists() {
        parse(message(MsgType.PLAYLISTS).setResponsePlaylists(ResponsePlaylists.newBuilder()
                .addPlaylist(Playlist.newBuilder().setId(1).setName("One").setItemCount(3))
                .addPlaylist(Playlist.newBuilder().setId(2).setName("Two").setActive(true))));

        assertEquals(2, App.Clementine.getPlaylistManager().getAllPlaylists().size());
        assertEquals("One", App.Clementine.getPlaylistManager().getPlaylist(1).getName());
        assertEquals(2, App.Clementine.getPlaylistManager().getActivePlaylistId());
    }

    @Test
    public void keepAliveWithoutConnection() {
        assertEquals(MsgType.KEEP_ALIVE, parse(message(MsgType.KEEP_ALIVE)).getMessageType());
    }

    @Test
    public void factoryShuffleAndRepeatFollowPlayerState() throws Exception {
        App.Clementine.setShuffleMode(Clementine.ShuffleMode.INSIDE_ALBUM);
        App.Clementine.setRepeatMode(Clementine.RepeatMode.TRACK);

        Message shuffle = Message.parseFrom(
                ClementineMessageFactory.buildShuffle().getMessage().toByteArray());
        Message repeat = Message.parseFrom(
                ClementineMessageFactory.buildRepeat().getMessage().toByteArray());

        assertEquals(ShuffleMode.Shuffle_InsideAlbum, shuffle.getShuffle().getShuffleMode());
        assertEquals(RepeatMode.Repeat_Track, repeat.getRepeat().getRepeatMode());
    }
}
