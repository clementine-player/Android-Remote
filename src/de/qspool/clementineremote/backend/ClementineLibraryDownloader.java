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

package de.qspool.clementineremote.backend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.elements.DownloaderResult.DownloadResult;
import de.qspool.clementineremote.backend.event.OnLibraryDownloadListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseLibraryChunk;
import de.qspool.clementineremote.backend.player.MyLibrary;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineLibraryDownloader extends
		AsyncTask<ClementineMessage, Integer, DownloaderResult> {
	private final String TAG ="ClementineLibraryDownloader";
	
	private Context mContext;
	private SharedPreferences mSharedPref;
	private ClementineSimpleConnection mClient = new ClementineSimpleConnection();

	private MyLibrary mLibrary;

	private LinkedList<OnLibraryDownloadListener> listeners = new LinkedList<OnLibraryDownloadListener>();

	public ClementineLibraryDownloader(Context context) {
		mContext = context;
		mLibrary = new MyLibrary(context);
		mSharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
	}

	/**
	 * Add a OnLibraryDownloadFinishedListener. It is emitted when the download
	 * is finished and the library file is available
	 * 
	 * @param l
	 *            The listener object
	 */
	public void addOnLibraryDownloadListener(
			OnLibraryDownloadListener l) {
		listeners.add(l);
	}
	
	public void removeOnLibraryDownloadListener(OnLibraryDownloadListener l) {
		listeners.remove(l);
	}

	@SuppressLint({ "InlinedApi", "NewApi" })
	public void startDownload(ClementineMessage message) {

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			this.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
		else
			this.execute(message);
	}

	@Override
	protected DownloaderResult doInBackground(ClementineMessage... params) {
		// Check if the sd card is writeable
		if (!Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED))
			return new DownloaderResult(
					DownloaderResult.DownloadResult.NOT_MOUNTED);

		if (mSharedPref.getBoolean(App.SP_WIFI_ONLY, false)
				&& !Utilities.onWifi(mContext))
			return new DownloaderResult(
					DownloaderResult.DownloadResult.ONLY_WIFI);

		// First create a connection
		if (!connect())
			return new DownloaderResult(
					DownloaderResult.DownloadResult.CONNECTION_ERROR);

		// Start the download
		return startDownloading(params[0]);
	}

	@Override
	protected void onProgressUpdate(Integer... progress) {
		fireOnProgressUpdateListener(progress[0]);

		// Progress = 100, then we are optimizing the table
		if (progress[0] == 100) {
			fireOnOptimizeLibraryListener();
		}
	}

	@Override
	protected void onCancelled() {
		fireOnLibraryDownloadFinishedListener(false);
	}

	@Override
	protected void onPostExecute(DownloaderResult result) {
		// Notify the listeners
		fireOnLibraryDownloadFinishedListener(result.getResult() == DownloaderResult.DownloadResult.SUCCESSFUL);
	}

	/**
	 * Connect to Clementine
	 * 
	 * @return true if the connection was established, false if not
	 */
	private boolean connect() {
		String ip = mSharedPref.getString(App.SP_KEY_IP, "");
		int port;
		try {
			port = Integer.valueOf(mSharedPref.getString(App.SP_KEY_PORT,
					String.valueOf(Clementine.DefaultPort)));
		} catch (NumberFormatException e) {
			port = Clementine.DefaultPort;
		}
		int authCode = mSharedPref.getInt(App.SP_LAST_AUTH_CODE, 0);

		return mClient.createConnection(ClementineMessageFactory
				.buildConnectMessage(ip, port, authCode, false, true));
	}

	/**
	 * Start the Download
	 */
	private DownloaderResult startDownloading(
			ClementineMessage clementineMessage) {
		boolean downloadFinished = false;
		DownloaderResult result = new DownloaderResult(
				DownloadResult.SUCCESSFUL);
		File f = null;
		FileOutputStream fo = null;

		publishProgress(0);

		// Now request the songs
		mClient.sendRequest(clementineMessage);

		while (!downloadFinished) {
			// Check if the user canceled the process
			if (isCancelled()) {
				// Close the stream and delete the incomplete file
				try {
					if (fo != null) {
						fo.flush();
						fo.close();
					}
					if (f != null)
						f.delete();
				} catch (IOException e) {
				}
				Log.d(TAG, "isCancelled");
				break;
			}

			// Get the raw protocol buffer
			ClementineMessage message = mClient.getProtoc();

			if (message.isErrorMessage()) {
				result = new DownloaderResult(DownloadResult.CONNECTION_ERROR);
				break;
			}

			// Is the download forbidden?
			if (message.getMessageType() == MsgType.DISCONNECT) {
				result = new DownloaderResult(DownloadResult.FOBIDDEN);
				break;
			}

			// Ignore other elements!
			if (message.getMessageType() != MsgType.LIBRARY_CHUNK)
				continue;

			ResponseLibraryChunk chunk = message.getMessage()
					.getResponseLibraryChunk();

			try {
				// Check if we need to create a new file
				if (f == null) {
					// Check if we have enougth free space
					// size times 2, because we optimise the table later and
					// need space for that too!
					if ((chunk.getSize() * 2) > Utilities.getFreeSpace()) {
						result = new DownloaderResult(
								DownloadResult.INSUFFIANT_SPACE);
						break;
					}
					f = mLibrary.getLibraryDb();

					// User wants to override files, so delete it here!
					// The check was already done in processSongOffer()
					if (f.exists()) {
						f.delete();
					}

					f.createNewFile();
					fo = new FileOutputStream(f);
				}

				// Write chunk to sdcard
				fo.write(chunk.getData().toByteArray());
				
				// Have we downloaded all chunks?
				if (chunk.getChunkCount() == chunk.getChunkNumber()) {
					fo.flush();
					fo.close();
					f = null;
					downloadFinished = true;
				}

				// Update notification
				updateProgress(chunk);
			} catch (IOException e) {
				result = new DownloaderResult(DownloadResult.CONNECTION_ERROR);
				break;
			} 
		}

		// Disconnect at the end
		mClient.disconnect(ClementineMessage.getMessage(MsgType.DISCONNECT));
		
		// Optimize library table
		mLibrary.optimizeTable();

		return result;
	}

	/**
	 * Updates the current notification
	 * 
	 * @param chunk
	 *            The current downloaded chunk
	 */
	private void updateProgress(ResponseLibraryChunk chunk) {
		// Update notification
		double progress = ((double) chunk.getChunkNumber() / (double) chunk
				.getChunkCount()) * 100;

		publishProgress((int) progress);
	}

	/*
	 * Fire the listeners
	 */
	private void fireOnLibraryDownloadFinishedListener(boolean successful) {
		for (OnLibraryDownloadListener l : listeners) {
			l.OnLibraryDownloadFinished(successful);
		}
	}
	
	private void fireOnOptimizeLibraryListener() {
		for (OnLibraryDownloadListener l : listeners) {
			l.OnOptimizeLibrary();
		}
	}
	
	private void fireOnProgressUpdateListener(int progress) {
		for (OnLibraryDownloadListener l : listeners) {
			l.OnProgressUpdate(progress);
		}
	}

}
