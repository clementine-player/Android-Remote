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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.app.NotificationCompat.Builder;
import android.widget.Toast;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.elements.ClementineElement;
import de.qspool.clementineremote.backend.elements.Disconnected;
import de.qspool.clementineremote.backend.elements.InvalidData;
import de.qspool.clementineremote.backend.elements.SongDownloadResult;
import de.qspool.clementineremote.backend.elements.SongFileChunk;
import de.qspool.clementineremote.backend.elements.SongDownloadResult.DownloadResult;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.requests.RequestConnect;
import de.qspool.clementineremote.backend.requests.RequestDisconnect;
import de.qspool.clementineremote.backend.requests.RequestDownload;
import de.qspool.clementineremote.backend.requests.RequestDownload.DownloadType;
import de.qspool.clementineremote.backend.requests.RequestNextSong;
import de.qspool.clementineremote.ui.ConnectDialog;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineSongDownloader extends
		AsyncTask<RequestDownload, Integer, SongDownloadResult> {
	
	private Context mContext;
	private SharedPreferences mSharedPref;
	private ClementineSimpleConnection mClient = new ClementineSimpleConnection();
	private NotificationManager mNotifyManager;
	private Builder mBuilder;
	private int mId;
	private MySong mCurrentSong;
	private int mFileCount;
	private int mCurrentFile;
	
	private int mPlaylistId;
	
	private boolean isPlaylist = false;
	private boolean createPlaylistDir = false;
	private boolean createPlaylistArtistDir = false;
	
	public ClementineSongDownloader(Context context, int id) {
		mContext = context;
		mSharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
		
		// Get preferences
		createPlaylistDir = mSharedPref.getBoolean(App.SP_DOWNLOAD_SAVE_OWN_DIR, false);
		createPlaylistArtistDir = mSharedPref.getBoolean(App.SP_DOWNLOAD_PLAYLIST_CRT_ARTIST_DIR, false);
		
		mId = id;
		
		// Show a toast that the download is starting
		Toast.makeText(mContext, R.string.player_howto_cancel, Toast.LENGTH_SHORT).show();
		
		mNotifyManager =
		        (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		mBuilder = new NotificationCompat.Builder(mContext);
		mBuilder.setContentTitle(mContext.getString(R.string.download_noti_title))
		    .setSmallIcon(R.drawable.ic_launcher)
		    .setOngoing(true);
		
		// Set the result intent
	    mBuilder.setContentIntent(buildNotificationIntent());
	    
	    mBuilder.setPriority(Notification.PRIORITY_LOW);
	}

	@Override
	protected SongDownloadResult doInBackground(RequestDownload... params) {
		// Check if the sd card is writeable
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
			return new SongDownloadResult(SongDownloadResult.DownloadResult.NOT_MOUNTED);
		
		if (mSharedPref.getBoolean(App.SP_WIFI_ONLY, false) && !Utilities.onWifi(mContext))
			return new SongDownloadResult(SongDownloadResult.DownloadResult.ONLY_WIFI);
		
		// First create a connection
		if (!connect())
			return new SongDownloadResult(SongDownloadResult.DownloadResult.CONNECTION_ERROR);
		
		// Start the download
		return startDownloading(params[0]);
	}
	
	@Override
	protected void onProgressUpdate(Integer... progress) {
		mBuilder.setProgress(100, progress[0], false);
		if (mCurrentSong == null) {
			mBuilder.setContentText(mContext.getString(R.string.connectdialog_connecting));	
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append("(");
			sb.append(mCurrentFile);
			sb.append("/");
			sb.append(mFileCount);
			sb.append(") ");
			sb.append(mCurrentSong.getArtist());
			sb.append(" - ");
			sb.append(mCurrentSong.getTitle());
			mBuilder.setContentText(sb.toString());
		}
        // Displays the progress bar for the first time.
        mNotifyManager.notify(mId, mBuilder.build());
    }
	
	@Override
    protected void onCancelled() {
		// When the loop is finished, updates the notification
        mBuilder.setContentText(mContext.getText(R.string.download_noti_canceled))
        		.setOngoing(false)
        		.setAutoCancel(true)
                .setProgress(0,0,false);
        mBuilder.setPriority(Notification.PRIORITY_MIN);
        mNotifyManager.cancel(mId);
        mNotifyManager.notify(mId, mBuilder.build());
    }

	@Override
    protected void onPostExecute(SongDownloadResult result) {
    	// When the loop is finished, updates the notification
		if (result == null)
			mBuilder.setContentText(mContext.getText(R.string.download_noti_canceled));
		else {
			switch (result.getResult()) {
			case CONNECTION_ERROR:
				mBuilder.setContentText(mContext.getText(R.string.download_noti_canceled));
				break;
			case FOBIDDEN:
				mBuilder.setContentText(mContext.getText(R.string.download_noti_forbidden));
				break;
			case INSUFFIANT_SPACE:
				mBuilder.setContentText(mContext.getText(R.string.download_noti_insufficient_space));
				break;
			case NOT_MOUNTED:
				mBuilder.setContentText(mContext.getText(R.string.download_noti_not_mounted));
				break;
			case ONLY_WIFI:
				mBuilder.setContentText(mContext.getText(R.string.download_noti_only_wifi));
				break;
			case SUCCESSFUL:
				mBuilder.setContentTitle(mContext.getText(R.string.download_noti_complete));
				break;
			}
		}
			
        mBuilder.setOngoing(false);
        mBuilder.setProgress(0,0,false);
        mBuilder.setAutoCancel(true);
        mBuilder.setPriority(Notification.PRIORITY_MIN);
        mNotifyManager.cancel(mId);
        mNotifyManager.notify(mId, mBuilder.build());
    }
	
	private PendingIntent buildNotificationIntent() {
		Intent resultIntent = new Intent(App.mApp, ConnectDialog.class);
	    resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    resultIntent.putExtra(App.NOTIFICATION_ID, mId);
	    
	    // Create a TaskStack, so the app navigates correctly backwards
	    TaskStackBuilder stackBuilder = TaskStackBuilder.create(App.mApp);
	    stackBuilder.addParentStack(ConnectDialog.class);
	    stackBuilder.addNextIntent(resultIntent);
	    return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
	}

    /**
     * Connect to Clementine
     * @return true if the connection was established, false if not
     */
    private boolean connect() {
    	String ip = mSharedPref.getString(App.SP_KEY_IP, "");
    	int port;
		try {
			port = Integer.valueOf(mSharedPref.getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort)));			
		} catch (NumberFormatException e) {
			port = Clementine.DefaultPort;
		}
    	int authCode = mSharedPref.getInt(App.SP_LAST_AUTH_CODE, 0);
    	
    	RequestConnect r = new RequestConnect(ip, port, authCode, false, true);
    	
    	return mClient.createConnection(r);
    }

    /**
     * Start the Downlaod
     */
    private SongDownloadResult startDownloading(RequestDownload r) {
    	boolean downloadFinished = false;
    	SongDownloadResult result = new SongDownloadResult(DownloadResult.SUCCESSFUL);
    	boolean ignoreSong = false;
    	File f = null;
    	FileOutputStream fo = null;
    	
    	// Do we have a playlist?
    	isPlaylist = (r.getType() == DownloadType.PLAYLIST);
    	if (isPlaylist) {
    		mPlaylistId = r.getPlaylistId();
    	}
    	
    	publishProgress(0);
    	
		// Now request the songs
		mClient.sendRequest(r);
		
		while (!downloadFinished) {
			// Get the raw protocol buffer
			ClementineElement element = mClient.getProtoc();
			
			if (element instanceof InvalidData) {
				result = new SongDownloadResult(DownloadResult.CONNECTION_ERROR);
				break;
			}
			
			// Is the download forbidden?
			if (element instanceof Disconnected) {
				result = new SongDownloadResult(DownloadResult.FOBIDDEN);
				break;
			}
			
			// Ignore other elements!
			if (!(element instanceof SongFileChunk))
				continue;
			
			SongFileChunk chunk = (SongFileChunk) element;
			
			// Shall the current song be ignored? (It is already on the device)
			if (ignoreSong && chunk.getSongMetadata() == null) {
				// Update the chunk, nevertheless it's ignored, so the user
				// doesn't think its hanging
				updateProgress(chunk);
				continue;
			}
			
			ignoreSong = false;
			
			try {				
				// Check if we need to create a new file
				if (f == null) {
					// Check if we have enougth free space
					if (chunk.getSize() > Utilities.getFreeSpace()) {
						result = new SongDownloadResult(DownloadResult.INSUFFIANT_SPACE);
						break;
					}
					
					File dir = new File(BuildDirPath(chunk));
					f = new File(BuildFilePath(chunk));
					
					// Save the songs Metadata on first chunk
					mCurrentSong = chunk.getSongMetadata();
					updateProgress(chunk);
					
					// do not override existing files!
					if (f.exists()) {
						// Check if we have downloaded all files
						if (chunk.getFileCount() == chunk.getFileNumber())
							downloadFinished = true;
						
						f = null;
						ignoreSong = true;
						
						// Request new track
						mClient.sendRequest(new RequestNextSong());
						
						continue;
					}
					
					dir.mkdirs();
					f.createNewFile();
					fo = new FileOutputStream(f);
				}
				
				// Write chunk to sdcard
				fo.write(chunk.getData());
				
				// Check if the user canceled the process
				if (isCancelled()) {
					// Close the stream and delete the incomplete file
					fo.flush();
					fo.close();
					f.delete();
					break;
				}
				
				// Have we downloaded all chunks?
				if (chunk.getChunkCount() == chunk.getChunkNumber()) {
					fo.flush();
					fo.close();
					f = null;
					
					// Request new track
					mClient.sendRequest(new RequestNextSong());
					
					// Check if we have downloaded all files
					if (chunk.getFileCount() == chunk.getFileNumber())
						downloadFinished = true;
					
				}
				
				// Update notification
				updateProgress(chunk);
			} catch (IOException e) {
				result = new SongDownloadResult(DownloadResult.CONNECTION_ERROR);
				break;
			}
			
		}
		
		// Disconnect at the end
		mClient.disconnect(new RequestDisconnect());
		
		return result;
    }
    
    /**
     * Updates the current notification
     * @param chunk The current downloaded chunk
     */
    private void updateProgress(SongFileChunk chunk) {
    	// Update notification
		mFileCount = chunk.getFileCount();
		mCurrentFile = chunk.getFileNumber();
		double progress = (((double) (chunk.getFileNumber()-1) /  (double) chunk.getFileCount()) 
				         + (((double) chunk.getChunkNumber() / (double) chunk.getChunkCount()) / (double) chunk.getFileCount())) 
				         * 100;
		
		publishProgress((int) progress);
    }
    
    /**
     * Return the folder where the file will be placed
     * @param chunk The chunk
     */
    private String BuildDirPath(SongFileChunk chunk) {
    	String defaultPath = Environment.getExternalStorageDirectory() + "//ClementineMusic";
        String path = mSharedPref.getString(App.SP_DOWNLOAD_DIR, defaultPath);
        
    	StringBuilder sb = new StringBuilder();
    	sb.append(path);
    	sb.append(File.separator);
    	if (isPlaylist && createPlaylistDir) {
    		sb.append(App.mClementine.getPlaylists().get(mPlaylistId).getName());
    		sb.append(File.separator);
    	}
    	
    	// Create artist/album subfolder only when we have no playlist
    	// or user set the settings
    	if (!isPlaylist ||
    		isPlaylist && createPlaylistDir && createPlaylistArtistDir) {
    		// Append artist name
	    	if (chunk.getSongMetadata().getAlbumartist().isEmpty())
	    		sb.append(chunk.getSongMetadata().getArtist());
	    	else
	    		sb.append(chunk.getSongMetadata().getAlbumartist());
	    	
	    	// append album
	    	sb.append(File.separator);
    		sb.append(chunk.getSongMetadata().getAlbum());
    	}
    	
    	return sb.toString();
    }
    
    /**
     * Build the filename
     * @param e The SongFileChunk
     * @return /sdcard/Music/Artist/Album/file.mp3
     */
    private String BuildFilePath(SongFileChunk chunk) {
    	StringBuilder sb = new StringBuilder();
    	sb.append(BuildDirPath(chunk));
    	sb.append(File.separator);
    	sb.append(chunk.getSongMetadata().getFilename());
    	
    	return sb.toString();
    }
}
