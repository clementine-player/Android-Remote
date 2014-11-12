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

import com.google.protobuf.InvalidProtocolBufferException;

import android.util.Log;

import java.util.LinkedList;
import java.util.List;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.Clementine.RepeatMode;
import de.qspool.clementineremote.backend.Clementine.ShuffleMode;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.EngineState;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Lyric;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Playlist;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Repeat;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseActiveChanged;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseCurrentMetadata;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseLyrics;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponsePlaylistSongs;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponsePlaylists;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseUpdateTrackPosition;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Shuffle;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata;
import de.qspool.clementineremote.backend.player.LyricsProvider;
import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.player.PlaylistManager;

public class ClementinePbParser {

    private PlaylistManager mPlaylistManager;

    public ClementinePbParser() {
        mPlaylistManager = App.mClementine.getPlaylistManager();
    }

    /**
     * Create a protocol buffer object from the binary data
     *
     * @param bs The binary representation of the protocol buffer
     * @return The parsed Element
     */
    public ClementineMessage parse(byte[] bs) {
        ClementineMessage parsedElement = null;

        try {
            Message msg = Message.parseFrom(bs);

            // First check the proto version
            if (!msg.hasVersion()
                    || msg.getVersion() < Message.getDefaultInstance().getVersion()) {
                parsedElement = new ClementineMessage(ErrorMessage.OLD_PROTO);
            } else {
                parsedElement = parseMsg(msg);
            }
        } catch (InvalidProtocolBufferException e) {
            Log.d("Parser", "InvalidProtocolBufferException");
            parsedElement = new ClementineMessage(ErrorMessage.INVALID_DATA);
        }

        return parsedElement;
    }

    /**
     * Parse the message itself
     *
     * @param msg The created message
     * @return The parsed data
     */
    private ClementineMessage parseMsg(Message msg) {
        ClementineMessage clementineMessage = new ClementineMessage(msg);

        switch (msg.getType()) {
            case INFO:
                parseInfos(msg.getResponseClementineInfo());
                break;
            case CURRENT_METAINFO:
                MySong s = parseSong(msg.getResponseCurrentMetadata());
                App.mClementine.setCurrentSong(s);
                App.mClementine.setSongPosition(0);
                break;
            case UPDATE_TRACK_POSITION:
                parseUpdateTrackPosition(msg.getResponseUpdateTrackPosition());
                break;
            case KEEP_ALIVE:
                App.mClementineConnection.setLastKeepAlive(System.currentTimeMillis());
                break;
            case SET_VOLUME:
                App.mClementine.setVolume(msg.getRequestSetVolume().getVolume());
                break;
            case PLAY:
                App.mClementine.setState(Clementine.State.PLAY);
                break;
            case PAUSE:
                App.mClementine.setState(Clementine.State.PAUSE);
                break;
            case STOP:
                App.mClementine.setState(Clementine.State.STOP);
                break;
            case DISCONNECT:
                break;
            case PLAYLISTS:
                parsePlaylists(msg.getResponsePlaylists());
                break;
            case PLAYLIST_SONGS:
                parsePlaylistSongs(msg.getResponsePlaylistSongs());
                break;
            case ACTIVE_PLAYLIST_CHANGED:
                parseActivePlaylistChanged(msg.getResponseActiveChanged());
                break;
            case REPEAT:
                parseRepeat(msg.getRepeat());
                break;
            case SHUFFLE:
                parseShuffle(msg.getShuffle());
                break;
            case LYRICS:
                parseLyrics(msg.getResponseLyrics());
                break;
            case SONG_FILE_CHUNK:
                break;
            case DOWNLOAD_QUEUE_EMPTY:
                break;
            default:
                break;
        }

        return clementineMessage;
    }

    /**
     * Parse the lyrics and save them into the song object
     *
     * @param responseLyrics The protocolbuffer message with the lyrics
     */
    private void parseLyrics(ResponseLyrics responseLyrics) {
        // Read all lyric providers
        for (Lyric lyric : responseLyrics.getLyricsList()) {
            // Save them into the structure
            LyricsProvider provider = new LyricsProvider();
            provider.setId(lyric.getId());
            provider.setTitle(lyric.getTitle());
            provider.setContent(lyric.getContent());

            // And save them into the song
            App.mClementine.getCurrentSong().getLyricsProvider().add(provider);
        }
    }

    /**
     * Update the currently active playlist id
     *
     * @param responseActiveChanged The response element
     * @return A new Reload element
     */
    private void parseActivePlaylistChanged(
            ResponseActiveChanged responseActiveChanged) {
        mPlaylistManager.setActivePlaylist(responseActiveChanged.getId());
    }

    /**
     * Parse a song message
     *
     * @param responseCurrentMetadata The song message
     * @return The Song in the representation of this app
     */
    private MySong parseSong(ResponseCurrentMetadata responseCurrentMetadata) {
        if (!responseCurrentMetadata.hasSongMetadata()) {
            return null;
        }

        // Get the metadata from protocolbuffer and set the song
        SongMetadata songMetadata = responseCurrentMetadata.getSongMetadata();
        if (!songMetadata.hasId()) {
            return null;
        }

        return MySong.fromProtocolBuffer(songMetadata);
    }

    /**
     * Parse the info message
     *
     * @param responseClementineInfo The info message
     */
    private void parseInfos(ResponseClementineInfo responseClementineInfo) {
        // Get the version number of clementine
        App.mClementine.setVersion(responseClementineInfo.getVersion());

        // Get the current state of the player
        EngineState state = responseClementineInfo.getState();
        switch (state.getNumber()) {
            case EngineState.Playing_VALUE:
                App.mClementine.setState(Clementine.State.PLAY);
                break;
            case EngineState.Paused_VALUE:
                App.mClementine.setState(Clementine.State.PAUSE);
                break;
            default:
                App.mClementine.setState(Clementine.State.STOP);
        }
    }

    /**
     * Sets the current position of the track
     *
     * @param responseUpdateTrackPosition The message
     */
    private void parseUpdateTrackPosition(ResponseUpdateTrackPosition responseUpdateTrackPosition) {
        App.mClementine.setSongPosition(responseUpdateTrackPosition.getPosition());
    }

    /**
     * Parse the playlists
     *
     * @param responsePlaylists The Playlist Elements
     */
    private void parsePlaylists(ResponsePlaylists responsePlaylists) {
        mPlaylistManager.removeAll();

        List<Playlist> playlists = responsePlaylists.getPlaylistList();

        for (Playlist playlist : playlists) {
            // Create the playlist and add the information
            MyPlaylist myPlaylist = new MyPlaylist();
            myPlaylist.setId(playlist.getId());
            myPlaylist.setName(playlist.getName());
            myPlaylist.setActive(playlist.getActive());
            myPlaylist.setItemCount(playlist.getItemCount());
            myPlaylist.setClosed(playlist.getClosed());

            mPlaylistManager.addPlaylist(myPlaylist);
        }

        mPlaylistManager.allPlaylistsReceived();
    }

    /**
     * Parse the songs in a playlist and add them to our structure
     *
     * @param response The message with the songs
     */
    private void parsePlaylistSongs(ResponsePlaylistSongs response) {
        Playlist playlist = response.getRequestedPlaylist();

        List<SongMetadata> songs = response.getSongsList();
        List<MySong> mySongs = new LinkedList<MySong>();

        for (SongMetadata s : songs) {
            mySongs.add(MySong.fromProtocolBuffer(s));
        }

        mPlaylistManager.playlistSongsDownloaded(playlist.getId(), mySongs);
    }

    /**
     * Get the Repeat Mode
     *
     * @param repeat The Element
     */
    private void parseRepeat(Repeat repeat) {
        switch (repeat.getRepeatMode()) {
            case Repeat_Off:
                App.mClementine.setRepeatMode(RepeatMode.OFF);
                break;
            case Repeat_Track:
                App.mClementine.setRepeatMode(RepeatMode.TRACK);
                break;
            case Repeat_Album:
                App.mClementine.setRepeatMode(RepeatMode.ALBUM);
                break;
            case Repeat_Playlist:
                App.mClementine.setRepeatMode(RepeatMode.PLAYLIST);
                break;
            default:
                break;
        }
    }

    /**
     * Get the shuffle Mode
     *
     * @param shuffle The Element
     */
    private void parseShuffle(Shuffle shuffle) {
        switch (shuffle.getShuffleMode()) {
            case Shuffle_Off:
                App.mClementine.setShuffleMode(ShuffleMode.OFF);
                break;
            case Shuffle_All:
                App.mClementine.setShuffleMode(ShuffleMode.ALL);
                break;
            case Shuffle_InsideAlbum:
                App.mClementine.setShuffleMode(ShuffleMode.INSIDE_ALBUM);
                break;
            case Shuffle_Albums:
                App.mClementine.setShuffleMode(ShuffleMode.ALBUMS);
                break;
            default:
                break;
        }
    }
}
