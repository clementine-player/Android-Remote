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

package de.qspool.clementineremote.backend.elements;

import de.qspool.clementineremote.backend.player.MySong;

public class SongFileChunk extends ClementineElement {
	private int mChunkNumber;
	private int mChunkCount;
	private int mFileNumber;
	private int mFileCount;
	private MySong mSongMetadata;
	private byte[] mData;
	private int mSize;
	
	public SongFileChunk(int chunkNumber,
						  int chunkCount,
						  int fileNumber,
						  int fileCount,
						  MySong songMetadata,
						  byte[] data,
						  int size) {
		mChunkNumber = chunkNumber;
		mChunkCount = chunkCount;
		mFileNumber = fileNumber;
		mFileCount = fileCount;
		mSongMetadata = songMetadata;
		mData = data;
		mSize = size;
	}
	  
	public int getChunkNumber() {
		return mChunkNumber;
	}
	public int getChunkCount() {
		return mChunkCount;
	}
	public int getFileNumber() {
		return mFileNumber;
	}
	public int getFileCount() {
		return mFileCount;
	}
	public MySong getSongMetadata() {
		return mSongMetadata;
	}
	public byte[] getData() {
		return mData;
	}
	public int getSize() {
		return mSize;
	}
	
}
