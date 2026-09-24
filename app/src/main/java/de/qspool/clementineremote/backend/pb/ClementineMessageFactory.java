/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
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

package de.qspool.clementineremote.backend.pb;

import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Repeat;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestChangeSong;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestConnect;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestDownloadSongs;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestInsertUrls;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestPlaylistSongs;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestRateSong;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestRemoveSongs;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSetTrackPosition;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSetVolume;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSongOffer;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Shuffle;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ShuffleMode;
import de.qspool.clementineremote.backend.player.MySong;

/**
 * Creates the protocol buffer messages
 */
public class ClementineMessageFactory {

    private ClementineMessageFactory() {
    }

    /**
     * Create a song offer response
     *
     * @return ResponseSongOffer Builder for protocol buffer message
     */
    public static ClementineMessage buildSongOfferResponse(boolean accepted) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.SONG_OFFER_RESPONSE);
        ResponseSongOffer.Builder offer = ResponseSongOffer.newBuilder();
        offer.setAccepted(accepted);
        msg.setResponseSongOffer(offer);

        return new ClementineMessage(msg);
    }

    public static ClementineMessage buildDownloadSongsMessage(DownloadItem downloadItem) {
        return buildDownloadSongsMessage(downloadItem, -1, null);
    }

    public static ClementineMessage buildDownloadSongsMessage(DownloadItem downloadItem, int playlistId) {
        return buildDownloadSongsMessage(downloadItem, playlistId, null);
    }

    public static ClementineMessage buildDownloadSongsMessage(DownloadItem downloadItem, LinkedList<String> urls) {
        return buildDownloadSongsMessage(downloadItem, -1, urls);
    }

    /**
     * Create a download song message
     *
     * @return The built request
     */
    public static ClementineMessage buildDownloadSongsMessage(DownloadItem downloadItem, int playlistId, LinkedList<String> urls) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.DOWNLOAD_SONGS);
        RequestDownloadSongs.Builder request = RequestDownloadSongs.newBuilder();

        request.setPlaylistId(playlistId);
        request.setDownloadItem(downloadItem);
        if (urls != null && !urls.isEmpty())
            request.addAllUrls(urls);

        msg.setRequestDownloadSongs(request);

        return new ClementineMessage(msg);
    }

    /**
     * Create the volume specific message
     *
     * @return the Volume message part
     */
    public static ClementineMessage buildVolumeMessage(int volume) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.SET_VOLUME);

        RequestSetVolume.Builder requestSetVolume = RequestSetVolume.newBuilder();
        requestSetVolume.setVolume(volume);

        msg.setRequestSetVolume(requestSetVolume);

        return new ClementineMessage(msg);
    }

    /**
     * Create the connect specific message
     *
     * @return the connect message part
     */
    public static ClementineMessage buildConnectMessage(String ip, int port, int authCode,
            boolean getPlaylistSongs, boolean isDownloader) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.CONNECT);

        RequestConnect.Builder requestConnect = RequestConnect.newBuilder();

        requestConnect.setAuthCode(authCode);
        requestConnect.setSendPlaylistSongs(getPlaylistSongs);
        requestConnect.setDownloader(isDownloader);

        msg.setRequestConnect(requestConnect);

        ClementineMessage clementineMessage = new ClementineMessage(msg);
        clementineMessage.setIp(ip);
        clementineMessage.setPort(port);

        return clementineMessage;
    }

    /**
     * Build shuffle Message
     *
     * @return The created element
     */
    public static ClementineMessage buildShuffle() {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.SHUFFLE);

        Shuffle.Builder shuffle = Shuffle.newBuilder();

        switch (App.Clementine.getShuffleMode()) {
            case OFF:
                shuffle.setShuffleMode(ShuffleMode.Shuffle_Off);
                break;
            case ALL:
                shuffle.setShuffleMode(ShuffleMode.Shuffle_All);
                break;
            case INSIDE_ALBUM:
                shuffle.setShuffleMode(ShuffleMode.Shuffle_InsideAlbum);
                break;
            case ALBUMS:
                shuffle.setShuffleMode(ShuffleMode.Shuffle_Albums);
                break;
        }
        msg.setShuffle(shuffle);

        return new ClementineMessage(msg);
    }

    /**
     * Build Repeat Message
     *
     * @return The created element
     */
    public static ClementineMessage buildRepeat() {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.REPEAT);

        Repeat.Builder repeat = Repeat.newBuilder();

        switch (App.Clementine.getRepeatMode()) {
            case OFF:
                repeat.setRepeatMode(ClementineRemoteProtocolBuffer.RepeatMode.Repeat_Off);
                break;
            case TRACK:
                repeat.setRepeatMode(ClementineRemoteProtocolBuffer.RepeatMode.Repeat_Track);
                break;
            case ALBUM:
                repeat.setRepeatMode(ClementineRemoteProtocolBuffer.RepeatMode.Repeat_Album);
                break;
            case PLAYLIST:
                repeat.setRepeatMode(ClementineRemoteProtocolBuffer.RepeatMode.Repeat_Playlist);
                break;
        }
        msg.setRepeat(repeat);

        return new ClementineMessage(msg);
    }

    /**
     * Request all Songs in current playlist
     *
     * @return The Builder for the Message
     */
    public static ClementineMessage buildRequestPlaylistSongs(int playlistId) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.REQUEST_PLAYLIST_SONGS);

        RequestPlaylistSongs.Builder requestPlaylistSongs = RequestPlaylistSongs.newBuilder();

        requestPlaylistSongs.setId(playlistId);

        msg.setRequestPlaylistSongs(requestPlaylistSongs);

        return new ClementineMessage(msg);
    }

    /**
     * Request all Songs in current playlist
     *
     * @return The Builder for the Message
     */
    public static ClementineMessage buildRequestChangeSong(int songIndex, int playlistId) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.CHANGE_SONG);

        RequestChangeSong.Builder request = RequestChangeSong.newBuilder();

        request.setSongIndex(songIndex);
        request.setPlaylistId(playlistId);

        msg.setRequestChangeSong(request);

        return new ClementineMessage(msg);
    }

    /**
     * Request to set the track position
     *
     * @return The Clementine message
     */
    public static ClementineMessage buildTrackPosition(int position) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.SET_TRACK_POSITION);

        RequestSetTrackPosition.Builder request = RequestSetTrackPosition.newBuilder();
        request.setPosition(position);

        msg.setRequestSetTrackPosition(request);

        return new ClementineMessage(msg);
    }

    /**
     * Rate the current track
     *
     * @param rating the rating from 0 to 1. Multiply five times for star count
     * @return the Clementine Message
     */
    public static ClementineMessage buildRateTrack(float rating) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.RATE_SONG);

        RequestRateSong.Builder request = RequestRateSong.newBuilder();
        request.setRating(rating);

        msg.setRequestRateSong(request);

        return new ClementineMessage(msg);
    }

    /**
     * Inserts a song into given playlist
     *
     * @param playistId The id of the playlist
     * @param urls      The urls to the items
     * @return the Clementine Message
     */
    public static ClementineMessage buildInsertUrl(int playistId, LinkedList<String> urls) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.INSERT_URLS);

        RequestInsertUrls.Builder insertUrls = RequestInsertUrls.newBuilder();
        insertUrls.setPlaylistId(playistId);
        for (String url : urls) {
            insertUrls.addUrls(url);
        }

        msg.setRequestInsertUrls(insertUrls);

        return new ClementineMessage(msg);
    }

    public static ClementineMessage buildInsertSongs(int playistId,
            LinkedList<ClementineRemoteProtocolBuffer.SongMetadata> songs) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.INSERT_URLS);

        RequestInsertUrls.Builder insertSongs = RequestInsertUrls.newBuilder();
        insertSongs.setPlaylistId(playistId);
        insertSongs.addAllSongs(songs);

        msg.setRequestInsertUrls(insertSongs);

        return new ClementineMessage(msg);
    }

    public static ClementineMessage buildRemoveSongFromPlaylist(int playlistId, MySong song) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.REMOVE_SONGS);

        RequestRemoveSongs.Builder removeItems = RequestRemoveSongs.newBuilder();
        removeItems.setPlaylistId(playlistId);
        removeItems.addSongs(song.getIndex());

        msg.setRequestRemoveSongs(removeItems);

        return new ClementineMessage(msg);
    }

    public static ClementineMessage buildRemoveMultipleSongsFromPlaylist(int playlistId,
            LinkedList<MySong> songs) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.REMOVE_SONGS);

        RequestRemoveSongs.Builder removeItems = RequestRemoveSongs.newBuilder();
        removeItems.setPlaylistId(playlistId);

        for (MySong s : songs) {
            removeItems.addSongs(s.getIndex());
        }

        msg.setRequestRemoveSongs(removeItems);

        return new ClementineMessage(msg);
    }

    public static ClementineMessage buildClosePlaylist(int playlistId) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.CLOSE_PLAYLIST);

        ClementineRemoteProtocolBuffer.RequestClosePlaylist.Builder requestClosePlaylist = ClementineRemoteProtocolBuffer.RequestClosePlaylist.newBuilder();
        requestClosePlaylist.setPlaylistId(playlistId);

        msg.setRequestClosePlaylist(requestClosePlaylist);

        return new ClementineMessage(msg);
    }

    public static ClementineMessage buildGlobalSearch(String query) {
        Message.Builder msg = ClementineMessage.getMessageBuilder(MsgType.GLOBAL_SEARCH);
        ClementineRemoteProtocolBuffer.RequestGlobalSearch.Builder requestGlobalSearch = ClementineRemoteProtocolBuffer.RequestGlobalSearch.newBuilder();

        requestGlobalSearch.setQuery(query);

        msg.setRequestGlobalSearch(requestGlobalSearch);

        return new ClementineMessage(msg);
    }
}
