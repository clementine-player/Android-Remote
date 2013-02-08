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

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message.Builder;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Repeat;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestPlaylistSongs;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSetVolume;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Shuffle;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ShuffleMode;
import de.qspool.clementineremote.backend.requests.RequestChangeCurrentSong;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestChangeSong;
import de.qspool.clementineremote.backend.requests.RequestConnect;
import de.qspool.clementineremote.backend.requests.RequestControl;
import de.qspool.clementineremote.backend.requests.RequestControl.Request;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.backend.requests.RequestPlaylistSong;
import de.qspool.clementineremote.backend.requests.RequestVolume;
import de.qspool.clementineremote.backend.requests.RequestToThread;

/**
 * Creates the protocol buffer messages
 */
public class ClementinePbCreator {
	
	public ClementinePbCreator() {
	}

	/**
	 * Create a prtocolbuffer messsage
	 * @param r What kind of message is created depends on r
	 * @return The binary representation of the message.
	 */
	public byte[] createRequest(RequestToThread r) {
		// Get a new builder and set the version
		Message.Builder msg = Message.newBuilder();
		
		// Set the messagetype and the content depending
		// on the request
		if (r instanceof RequestConnect) {
			msg.setType(MsgType.CONNECT);
			msg.setRequestConnect(buildConnectMessage(msg, (RequestConnect)r));
		} else if (r instanceof RequestDisconnect) {
			msg.setType(MsgType.DISCONNECT);
		} else if (r instanceof RequestControl) {
			RequestControl rc = (RequestControl) r;
			Request action = rc.getRequest();
			switch (action) {
			case PLAY: 		msg.setType(MsgType.PLAY);
							break;
			case PLAYPAUSE: msg.setType(MsgType.PLAYPAUSE);
							break;
			case PAUSE:		msg.setType(MsgType.PAUSE);
							break;
			case STOP: 		msg.setType(MsgType.STOP);
							break;
			case NEXT: 		msg.setType(MsgType.NEXT);
							break;
			case PREV: 		msg.setType(MsgType.PREVIOUS);
							break;
			case REPEAT:	msg.setType(MsgType.REPEAT);
							msg.setRepeat(buildRepeat(msg));
							break;
			case SHUFFLE:	msg.setType(MsgType.SHUFFLE);
							msg.setShuffle(buildRandom(msg));
							break;
			default: 		break;
			}
		} else if (r instanceof RequestVolume) {
			msg.setType(MsgType.SET_VOLUME);
			msg.setRequestSetVolume(buildVolumeMessage(msg, (RequestVolume)r));
		} else if (r instanceof RequestPlaylistSong) {
			msg.setType(MsgType.REQUEST_PLAYLIST_SONGS);
			msg.setRequestPlaylistSongs(buildRequestPlaylistSongs(msg, (RequestPlaylistSong)r));
		} else if (r instanceof RequestChangeCurrentSong) {
			msg.setType(MsgType.CHANGE_SONG);
			msg.setRequestChangeSong(buildRequestChangeSong(msg, (RequestChangeCurrentSong)r));
		}
		Message m = msg.build();
		
		return m.toByteArray();
	}

	/**
	 * Create the volume specific message
	 * @param msg The Message itself
	 * @param r The Request
	 * @return the Volume message part
	 */
	private RequestSetVolume.Builder buildVolumeMessage(Message.Builder msg, RequestVolume r) {
		RequestSetVolume.Builder requestSetVolume = msg.getRequestSetVolumeBuilder();
		requestSetVolume.setVolume(r.getVolume());
		return requestSetVolume;
	}
	
	/**
	 * Create the connect specific message
	 * @param msg The Message itself
	 * @param r The Request
	 * @return the connect message part
	 */
	private ClementineRemoteProtocolBuffer.RequestConnect.Builder
			buildConnectMessage(Message.Builder msg, RequestConnect r) {
		ClementineRemoteProtocolBuffer.RequestConnect.Builder 
			requestConnect = msg.getRequestConnectBuilder();
		requestConnect.setAuthCode(r.getAuthCode());
		return requestConnect;
	}
	
	/**
	 * Build Random Message
	 * @param msg The root message
	 * @return The created element
	 */
	private Shuffle.Builder buildRandom(Builder msg) {
		Shuffle.Builder shuffle = msg.getShuffleBuilder();
		
		switch (App.mClementine.getShuffleMode()) {
		case OFF: 		shuffle.setShuffleMode(ShuffleMode.Shuffle_Off);
						break;
		case ALL:		shuffle.setShuffleMode(ShuffleMode.Shuffle_All);
						break;
		case INSIDE_ALBUM:	shuffle.setShuffleMode(ShuffleMode.Shuffle_InsideAlbum);
							break;
		case ALBUMS:	shuffle.setShuffleMode(ShuffleMode.Shuffle_Albums);
						break;
		}
		return shuffle;
	}

	/**
	 * Build Repeat Message
	 * @param msg The root message
	 * @return The created element
	 */
	private Repeat.Builder buildRepeat(Builder msg) {
		Repeat.Builder repeat = msg.getRepeatBuilder();
		
		switch (App.mClementine.getRepeatMode()) {
		case OFF: 		repeat.setRepeatMode(ClementineRemoteProtocolBuffer.RepeatMode.Repeat_Off);
						break;
		case TRACK:		repeat.setRepeatMode(ClementineRemoteProtocolBuffer.RepeatMode.Repeat_Track);
						break;
		case ALBUM:		repeat.setRepeatMode(ClementineRemoteProtocolBuffer.RepeatMode.Repeat_Album);
						break;
		case PLAYLIST:	repeat.setRepeatMode(ClementineRemoteProtocolBuffer.RepeatMode.Repeat_Playlist);
						break;
		}
		return repeat;
	}
	
	/**
	 * Request all Songs in current playlist
	 * @param msg The root message
	 * @param r The Request Object
	 * @return The Builder for the Message
	 */
	private RequestPlaylistSongs.Builder buildRequestPlaylistSongs(Builder msg,
			RequestPlaylistSong r) {
		RequestPlaylistSongs.Builder requestPlaylistSongs = msg.getRequestPlaylistSongsBuilder();
		
		requestPlaylistSongs.setId(r.getPlaylistId());
		
		return requestPlaylistSongs;
	}
	
	/**
	 * Request all Songs in current playlist
	 * @param msg The root message
	 * @param r The Request Object
	 * @return The Builder for the Message
	 */
	private RequestChangeSong.Builder buildRequestChangeSong(
			Builder msg, RequestChangeCurrentSong r) {
		RequestChangeSong.Builder request = msg.getRequestChangeSongBuilder();
		
		request.setSongIndex(r.getSong().getIndex());
		request.setPlaylistId(r.getPlaylistId());
		
		return request;
	}
}
