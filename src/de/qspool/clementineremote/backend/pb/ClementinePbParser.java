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
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.elements.ClementineElement;
import de.qspool.clementineremote.backend.elements.Connected;
import de.qspool.clementineremote.backend.elements.InvalidData;
import de.qspool.clementineremote.backend.elements.Reload;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.EngineState;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseClementineInfo;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseCurrentMetadata;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata;
import de.qspool.clementineremote.backend.player.Song;

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
			parsedElement = parseMsg(msg);
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
			Song s = parseSong(msg.getResponseCurrentMetadata());
			App.mClementine.setCurrentSong(s);
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
		}
		
		return parsedElement;
	}
	
	/**
	 * Parse a song message
	 * @param responseCurrentMetadata The song message
	 * @return The Song in the representation of this app
	 */
	private Song parseSong(ResponseCurrentMetadata responseCurrentMetadata) {
		if (!responseCurrentMetadata.hasSongMetadata()) {
			return null;
		}
		
		// Get the metadata from protocolbuffer and set the song
		SongMetadata songMetadata = responseCurrentMetadata.getSongMetadata();
		if (!songMetadata.hasId()) {
			return null;
		}
		Song song = new Song();
		
		// Apply the metadata
		song.setId	  (songMetadata.getId());
		song.setIndex (songMetadata.getIndex());
		song.setArtist(songMetadata.getArtist());
		song.setTitle (songMetadata.getTitle());
		song.setAlbum (songMetadata.getAlbum());
		song.setAlbumartist(songMetadata.getAlbumartist());
		song.setLength(songMetadata.getPrettyLength());
		song.setGenre (songMetadata.getGenre());
		song.setYear  (songMetadata.getPrettyYear());
		song.setTrack (songMetadata.getTrack());
		song.setDisc  (songMetadata.getDisc());
		song.setPlaycount(songMetadata.getPlaycount());
		song.setArt   (songMetadata.getArt());

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
}
