package de.qspool.clementineremote.backend.pb;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;

import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks that every request carries its type, the protocol version and its payload
 * after a serialisation round trip.
 */
public class ClementineMessageFactoryTest {

    private static Message roundTrip(ClementineMessage message) throws Exception {
        Message parsed = Message.parseFrom(message.getMessage().toByteArray());
        assertTrue(parsed.hasVersion());
        assertEquals(Message.getDefaultInstance().getVersion(), parsed.getVersion());
        return parsed;
    }

    @Test
    public void connect() throws Exception {
        ClementineMessage message = ClementineMessageFactory
                .buildConnectMessage("192.0.2.1", 5500, 12345, true, false);

        assertEquals("192.0.2.1", message.getIp());
        assertEquals(5500, message.getPort());

        Message parsed = roundTrip(message);
        assertEquals(MsgType.CONNECT, parsed.getType());
        assertEquals(12345, parsed.getRequestConnect().getAuthCode());
        assertTrue(parsed.getRequestConnect().getSendPlaylistSongs());
        assertEquals(false, parsed.getRequestConnect().getDownloader());
    }

    @Test
    public void volume() throws Exception {
        Message parsed = roundTrip(ClementineMessageFactory.buildVolumeMessage(42));
        assertEquals(MsgType.SET_VOLUME, parsed.getType());
        assertEquals(42, parsed.getRequestSetVolume().getVolume());
    }

    @Test
    public void trackPosition() throws Exception {
        Message parsed = roundTrip(ClementineMessageFactory.buildTrackPosition(97));
        assertEquals(MsgType.SET_TRACK_POSITION, parsed.getType());
        assertEquals(97, parsed.getRequestSetTrackPosition().getPosition());
    }

    @Test
    public void rateTrack() throws Exception {
        Message parsed = roundTrip(ClementineMessageFactory.buildRateTrack(0.6f));
        assertEquals(MsgType.RATE_SONG, parsed.getType());
        assertEquals(0.6f, parsed.getRequestRateSong().getRating(), 0.0001f);
    }

    @Test
    public void changeSong() throws Exception {
        Message parsed = roundTrip(ClementineMessageFactory.buildRequestChangeSong(3, 7));
        assertEquals(MsgType.CHANGE_SONG, parsed.getType());
        assertEquals(3, parsed.getRequestChangeSong().getSongIndex());
        assertEquals(7, parsed.getRequestChangeSong().getPlaylistId());
    }

    @Test
    public void requestPlaylistSongs() throws Exception {
        Message parsed = roundTrip(ClementineMessageFactory.buildRequestPlaylistSongs(5));
        assertEquals(MsgType.REQUEST_PLAYLIST_SONGS, parsed.getType());
        assertEquals(5, parsed.getRequestPlaylistSongs().getId());
    }

    @Test
    public void insertUrls() throws Exception {
        LinkedList<String> urls = new LinkedList<>(Arrays.asList("file:///a.mp3", "file:///b.mp3"));
        Message parsed = roundTrip(ClementineMessageFactory.buildInsertUrl(2, urls));
        assertEquals(MsgType.INSERT_URLS, parsed.getType());
        assertEquals(2, parsed.getRequestInsertUrls().getPlaylistId());
        assertEquals(urls, parsed.getRequestInsertUrls().getUrlsList());
    }

    @Test
    public void downloadSongs() throws Exception {
        LinkedList<String> urls = new LinkedList<>(Arrays.asList("file:///a.mp3"));
        Message parsed = roundTrip(ClementineMessageFactory
                .buildDownloadSongsMessage(DownloadItem.Urls, 4, urls));
        assertEquals(MsgType.DOWNLOAD_SONGS, parsed.getType());
        assertEquals(DownloadItem.Urls, parsed.getRequestDownloadSongs().getDownloadItem());
        assertEquals(4, parsed.getRequestDownloadSongs().getPlaylistId());
        assertEquals(urls, parsed.getRequestDownloadSongs().getUrlsList());
    }

    @Test
    public void songOfferResponse() throws Exception {
        Message parsed = roundTrip(ClementineMessageFactory.buildSongOfferResponse(true));
        assertEquals(MsgType.SONG_OFFER_RESPONSE, parsed.getType());
        assertTrue(parsed.getResponseSongOffer().getAccepted());
    }

    @Test
    public void closePlaylist() throws Exception {
        Message parsed = roundTrip(ClementineMessageFactory.buildClosePlaylist(9));
        assertEquals(MsgType.CLOSE_PLAYLIST, parsed.getType());
        assertEquals(9, parsed.getRequestClosePlaylist().getPlaylistId());
    }

    @Test
    public void globalSearch() throws Exception {
        Message parsed = roundTrip(ClementineMessageFactory.buildGlobalSearch("beatles"));
        assertEquals(MsgType.GLOBAL_SEARCH, parsed.getType());
        assertEquals("beatles", parsed.getRequestGlobalSearch().getQuery());
    }
}
