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
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.Clementine.ShuffleMode;
import de.qspool.clementineremote.backend.Clementine.RepeatMode;
import de.qspool.clementineremote.backend.elements.ClementineElement;
import de.qspool.clementineremote.backend.elements.Connected;
import de.qspool.clementineremote.backend.elements.Disconnected;
import de.qspool.clementineremote.backend.elements.GotPlaylistSongs;
import de.qspool.clementineremote.backend.elements.Disconnected.DisconnectReason;
import de.qspool.clementineremote.backend.elements.InvalidData;
import de.qspool.clementineremote.backend.elements.OldProtoVersion;
import de.qspool.clementineremote.backend.elements.Reload;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.EngineState;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Playlist;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Repeat;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseCurrentMetadata;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponsePlaylistSongs;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponsePlaylists;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseUpdateTrackPosition;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Shuffle;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata;
import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.backend.player.MySong;

public class ClementinePbParser {
	
	public ClementinePbParser() {
	}
	
	/**
	 * Create a protocol buffer object from the binary data
	 * @param bs The binary representation of the protocol buffer
	 * @return The parsed Element
	 */
	public ClementineElement parse(byte[] bs) {
		Message msg = null;
		ClementineElement parsedElement = new InvalidData();
		
		try {
			msg = Message.parseFrom(bs);
			
			// First check the proto version
			if (!msg.hasVersion()
			 || msg.getVersion() < Message.getDefaultInstance().getVersion()) {
				parsedElement = new OldProtoVersion();
			} else {
				parsedElement = parseMsg(msg);
			}
		} catch (InvalidProtocolBufferException e) {
			msg = null;
			parsedElement = new InvalidData();
		}
		
		return parsedElement;
	}
	
	/**
	 * Parse the message itself
	 * @param msg The created message
	 * @return The parsed data
	 */
	private ClementineElement parseMsg(Message msg) {
		ClementineElement parsedElement = null;
		
		if (msg.getType().equals(MsgType.INFO)) {
			parseInfos(msg.getResponseClementineInfo());
			parsedElement = new Connected();
		} else if (msg.getType().equals(MsgType.CURRENT_METAINFO)) {
			MySong s = parseSong(msg.getResponseCurrentMetadata());
			App.mClementine.setCurrentSong(s);
			App.mClementine.setSongPosition(0);
			parsedElement = new Reload(); 
		} else if (msg.getType().equals(MsgType.UPDATE_TRACK_POSITION)) {
			parseUpdateTrackPosition(msg.getResponseUpdateTrackPosition());
			parsedElement = new Reload(); 
		} else if (msg.getType().equals(MsgType.KEEP_ALIVE)) {
			App.mClementineConnection.setLastKeepAlive(System.currentTimeMillis());
		} else if (msg.getType().equals(MsgType.SET_VOLUME)) {
			App.mClementine.setVolume(msg.getRequestSetVolume().getVolume());
		} else if (msg.getType().equals(MsgType.PLAY)) {
			App.mClementine.setState(Clementine.State.PLAY);
			parsedElement = new Reload(); 
		} else if (msg.getType().equals(MsgType.PAUSE)) {
			App.mClementine.setState(Clementine.State.PAUSE);
			parsedElement = new Reload(); 
		} else if (msg.getType().equals(MsgType.STOP)) {
			App.mClementine.setState(Clementine.State.STOP);
			parsedElement = new Reload(); 
		} else if (msg.getType().equals(MsgType.DISCONNECT)) {
			parsedElement = parseDisconnect(msg.getResponseDisconnect());
		} else if (msg.getType().equals(MsgType.PLAYLISTS)) {
			parsedElement = parsePlaylists(msg.getResponsePlaylists());
		} else if (msg.getType().equals(MsgType.PLAYLIST_SONGS)) {
			parsedElement = parsePlaylistSongs(msg.getResponsePlaylistSongs());
		} else if (msg.getType().equals(MsgType.REPEAT)) {
			parsedElement = parseRepeat(msg.getRepeat());
		} else if (msg.getType().equals(MsgType.SHUFFLE)) {
			parsedElement = parseRandom(msg.getShuffle());
		}
		
		return parsedElement;
	}

	/**
	 * Parse a song message
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

		return copySongMetadata(songMetadata);
	}
	
	private MySong copySongMetadata(SongMetadata songMetadata) {
		MySong song = new MySong();
		
		// Apply the metadata
		song.setId	  (songMetadata.getId());
		song.setIndex (songMetadata.getIndex());
		song.setArtist(songMetadata.getArtist());
		song.setTitle (songMetadata.getTitle());
		song.setAlbum (songMetadata.getAlbum());
		song.setAlbumartist(songMetadata.getAlbumartist());
		song.setPrettyLength(songMetadata.getPrettyLength());
		song.setLength(songMetadata.getLength());
		song.setGenre (songMetadata.getGenre());
		song.setYear  (songMetadata.getPrettyYear());
		song.setTrack (songMetadata.getTrack());
		song.setDisc  (songMetadata.getDisc());
		song.setPlaycount(songMetadata.getPlaycount());
		if (songMetadata.hasArt()) {
			song.setArt   (songMetadata.getArt());
		}

		return song;
	}

	/**
	 * Parse the info message
	 * @param responseClementineInfo The info message
	 */
	private void parseInfos(ResponseClementineInfo responseClementineInfo) {
		// Get the version number of clementine
		App.mClementine.setVersion(responseClementineInfo.getVersion());
		
		// Get the current state of the player
		EngineState state = responseClementineInfo.getState();
		switch (state.getNumber()) {
		case EngineState.Playing_VALUE: App.mClementine.setState(Clementine.State.PLAY);
										break;
		case EngineState.Paused_VALUE:  App.mClementine.setState(Clementine.State.PAUSE);
										break;
		default: 						App.mClementine.setState(Clementine.State.STOP);
		}
	}
	
	/**
	 * Sets the current position of the track
	 * @param responseUpdateTrackPosition The message
	 */
	private void parseUpdateTrackPosition(ResponseUpdateTrackPosition responseUpdateTrackPosition) {
		App.mClementine.setSongPosition(responseUpdateTrackPosition.getPosition());
	}
	
	/**
	 * Parse the Disconnect message
	 * @param responseDisconnect The response from Clementine
	 * @return The parsed Element
	 */
	private ClementineElement parseDisconnect(
			ResponseDisconnect responseDisconnect) {
		Disconnected disconnected = null;
		
		switch (responseDisconnect.getReasonDisconnect().getNumber()) {
		case ReasonDisconnect.Server_Shutdown_VALUE:
				 disconnected = new Disconnected(DisconnectReason.SERVER_CLOSE);
			     break;
		case ReasonDisconnect.Wrong_Auth_Code_VALUE:
			 disconnected = new Disconnected(DisconnectReason.WRONG_AUTH_CODE);
		     break;
		default: disconnected = new Disconnected(DisconnectReason.SERVER_CLOSE);
				 break;
		}
		return disconnected;
	}
	
	/**
	 * Parse the playlists
	 * @param responsePlaylists The Playlist Elements
	 * @return Reload Element
	 */
	private ClementineElement parsePlaylists(ResponsePlaylists responsePlaylists) {
		// First clear the current playlists
		App.mClementine.getPlaylists().clear();
		
		List<Playlist> playlists = responsePlaylists.getPlaylistList();
		
		for (Playlist playlist : playlists) {
			// Create the playlist and add the information
			MyPlaylist myPlaylist = new MyPlaylist();
			myPlaylist.setId(playlist.getId());
			myPlaylist.setName(playlist.getName());
			myPlaylist.setActive(playlist.getActive());
			myPlaylist.setItemCount(playlist.getItemCount());
			
			// Add the playlist to the playlist list
			App.mClementine.addPlaylist(myPlaylist);
		}
		
		return new Reload();
	}
	
	private ClementineElement parsePlaylistSongs(ResponsePlaylistSongs response) {
		Playlist playlist = response.getRequestedPlaylist();
		LinkedList<MySong> playlistSongs = App.mClementine.getPlaylists().get(playlist.getId()).getPlaylistSongs();
		playlistSongs.clear();
		
		List<SongMetadata> songs = response.getSongsList();
		
		for (SongMetadata s : songs) {
			playlistSongs.add(copySongMetadata(s));
		}
		
		return new GotPlaylistSongs();
	}
	
	/**
	 * Get the Repeat Mode
	 * @param repeat The Element
	 * @return Reload
	 */
	private ClementineElement parseRepeat(Repeat repeat) {
		switch (repeat.getRepeatMode()) {
		case Repeat_Off:		App.mClementine.setRepeatMode(RepeatMode.OFF);
								break;
		case Repeat_Track:		App.mClementine.setRepeatMode(RepeatMode.TRACK);
								break;
		case Repeat_Album:		App.mClementine.setRepeatMode(RepeatMode.ALBUM);
								break;
		case Repeat_Playlist:	App.mClementine.setRepeatMode(RepeatMode.PLAYLIST);
								break;
		default: break;
		}
		return new Reload();
	}
	
	/**
	 * Get the Random Mode
	 * @param random The Element
	 * @return Reload
	 */
	private ClementineElement parseRandom(Shuffle random) {
		switch (random.getShuffleMode()) {
		case Shuffle_Off:		App.mClementine.setShuffleMode(ShuffleMode.OFF);
								break;
		case Shuffle_All:		App.mClementine.setShuffleMode(ShuffleMode.ALL);
								break;
		case Shuffle_InsideAlbum:	App.mClementine.setShuffleMode(ShuffleMode.INSIDE_ALBUM);
									break;
		case Shuffle_Albums:	App.mClementine.setShuffleMode(ShuffleMode.ALBUMS);
								break;
		default: break;
		}
		return new Reload();
	}
}
