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

import de.qspool.clementineremote.backend.exceptions.ErrorMessageException;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;

public class ClementineMessage {
	/**
	 * Various message types can be assigned to a group.
	 * E.g. state changes (play, pause, ...) are assigned to RELOAD,
	 * so other classes know the shall reload. This reduces the complexity of
	 * the if / switch statements.
	 */
	public enum MessageGroup {NONE, GUI_RELOAD};
	public enum ErrorMessage {NO, INVALID_DATA, OLD_PROTO, KEEP_ALIVE_TIMEOUT, NO_CONNECTION};
	
	private Message mMessage;
	private MessageGroup mTypeGroup;
	private ErrorMessage mErrorMessage;
	
	// Additional data for the connect message
	private String mIp;
	private int mPort;
	
	/**
	 * Create a ClementineMessage from a giver protocol buffer
	 * @param msg The created message
	 */
	public ClementineMessage(Message msg) {
		mMessage = msg;
		mTypeGroup = MessageGroup.NONE;
		mErrorMessage = ErrorMessage.NO;
	}
	
	/**
	 * Create a ClementineMessage from a Builder. It will build the 
	 * message when this constructor is called. Futher addition to the 
	 * message is not possible
	 * @param builder The builder Object
	 */
	public ClementineMessage(Message.Builder builder) {
		mMessage = builder.build();
		mTypeGroup = MessageGroup.NONE;
		mErrorMessage = ErrorMessage.NO;
	}
	
	/**
	 * Call this constructor if the message contains an error. We don't 
	 * have a message and this object will throw a ErrorMessageException
	 * @param error
	 */
	public ClementineMessage(ErrorMessage error) {
		mErrorMessage = error;
	}
	
	/**
	 * Get a ClementineMessage from a specific message type
	 * @param msgType the type
	 * @return The ClementineMessage ready to send
	 */
	public static ClementineMessage getMessage(MsgType msgType) {
		return new ClementineMessage(ClementineMessage.getMessageBuilder(msgType));
	}
	
	/**
	 * Static function to get a Message builder. 
	 * It will set the default version and the message type for you.
	 * @param msgType The message type this message shall have
	 * @return The Message.Builder object to work with
	 */
	public static Message.Builder getMessageBuilder(MsgType msgType) {
		Message.Builder builder = Message.newBuilder();
		builder.setVersion(builder.getDefaultInstanceForType().getVersion());
		builder.setType(msgType);
		
		return builder;
	}
	
	/**
	 * Set a group of types
	 * @param typeGroup A type of group
	 * @throws ErrorMessageException when this is a error message
	 */
	public void setTypeGroup(MessageGroup typeGroup) {
		if (mErrorMessage != ErrorMessage.NO) throw new Error("This is an error message. This should be handled! Error: " + mErrorMessage);
		mTypeGroup = typeGroup;
	}
	
	public MessageGroup getTypeGroup() {
		if (mErrorMessage != ErrorMessage.NO) throw new Error("This is an error message. This should be handled! Error: " + mErrorMessage);
		return mTypeGroup;
	}
	
	public Message getMessage() {
		if (mErrorMessage != ErrorMessage.NO) throw new Error("This is an error message. This should be handled! Error: " + mErrorMessage);
		return mMessage;
	}
	
	public MsgType getMessageType() {
		if (mErrorMessage != ErrorMessage.NO) throw new Error("This is an error message. This should be handled! Error: " + mErrorMessage);
		return mMessage.getType();
	}
	
	public ErrorMessage getErrorMessage() {
		return mErrorMessage;
	}
	
	public boolean isErrorMessage() {
		return (mErrorMessage != ErrorMessage.NO);
	}

	public String getIp() {
		return mIp;
	}

	public void setIp(String mIp) {
		this.mIp = mIp;
	}

	public int getPort() {
		return mPort;
	}

	public void setPort(int mPort) {
		this.mPort = mPort;
	}
}
