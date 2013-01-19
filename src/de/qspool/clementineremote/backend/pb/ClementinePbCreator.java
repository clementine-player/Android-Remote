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
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSetVolume;
import de.qspool.clementineremote.backend.requests.RequestConnect;
import de.qspool.clementineremote.backend.requests.RequestControl;
import de.qspool.clementineremote.backend.requests.RequestControl.Request;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
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
		msg.setVersion(App.PROTOCOL_BUFFER_VERSION);
		
		// Set the messagetype and the content depending
		// on the request
		if (r instanceof RequestConnect) {
			msg.setType(MsgType.CONNECT);
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
			case SHUFFLE:	msg.setType(MsgType.SHUFFLE_PLAYLIST);
							break;
			default: 		break;
			}
		} else if (r instanceof RequestVolume) {
			msg.setType(MsgType.SET_VOLUME);
			msg.setRequestSetVolume(buildVolumeMessage(msg, (RequestVolume)r));
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
}
