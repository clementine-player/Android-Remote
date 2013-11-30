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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.elements.DownloaderResult.DownloadResult;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSongFileChunk;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.MainActivity;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineSongDownloader extends
		AsyncTask<ClementineMessage, Integer, DownloaderResult> {
	
	private Context mContext;
	private SharedPreferences mSharedPref;
	private ClementineSimpleConnection mClient = new ClementineSimpleConnection();
	private NotificationManager mNotifyManager;
	private Builder mBuilder;
	private int mId;
	private MySong mCurrentSong = new MySong();
	private int mFileCount;
	private int mCurrentFile;
	
	private int mPlaylistId;
	
	private boolean mIsPlaylist = false;
	private boolean mCreatePlaylistDir = false;
	private boolean mCreatePlaylistArtistDir = false;
	private boolean mOverrideExistingFiles = false;
	
	private DownloadItem mItem;
	private int mCurrentProgress;
	private String mTitle;
	private String mSubtitle;
	private Uri mFileUri;
	
	public ClementineSongDownloader(Context context) {
		mContext = context;
		mSharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
		
		// Get preferences
		mCreatePlaylistDir = mSharedPref.getBoolean(App.SP_DOWNLOAD_SAVE_OWN_DIR, false);
		mCreatePlaylistArtistDir = mSharedPref.getBoolean(App.SP_DOWNLOAD_PLAYLIST_CRT_ARTIST_DIR, true);
		mOverrideExistingFiles = mSharedPref.getBoolean(App.SP_DOWNLOAD_OVERRIDE, false);
		
		// Get a new id
		mId = App.downloaders.size() + 1;
		
		// Show a toast that the download is starting
		Toast.makeText(mContext, R.string.player_download_started, Toast.LENGTH_SHORT).show();
		
		mNotifyManager =
		        (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		mBuilder = new NotificationCompat.Builder(mContext);
		mBuilder.setContentTitle(mContext.getString(R.string.download_noti_title))
		    .setSmallIcon(R.drawable.ic_launcher)
		    .setOngoing(true);
		
		// Set the result intent
	    mBuilder.setContentIntent(buildNotificationIntent());
	    
	    mBuilder.setPriority(Notification.PRIORITY_LOW);
	    
	    // Add this downloader to list
	    App.downloaders.add(this);
	    
	    mTitle = mContext.getString(R.string.download_noti_title);
	}
	
	@SuppressLint({ "InlinedApi", "NewApi" })
	public void startDownload(ClementineMessage message) {
		mItem = message.getMessage().getRequestDownloadSongs().getDownloadItem();
		
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
	        this.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
	    else
	        this.execute(message);
	}

	@Override
	protected DownloaderResult doInBackground(ClementineMessage... params) {
		// Check if the sd card is writeable
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
			return new DownloaderResult(DownloaderResult.DownloadResult.NOT_MOUNTED);
		
		if (mSharedPref.getBoolean(App.SP_WIFI_ONLY, false) && !Utilities.onWifi(mContext))
			return new DownloaderResult(DownloaderResult.DownloadResult.ONLY_WIFI);
		
		// First create a connection
		if (!connect())
			return new DownloaderResult(DownloaderResult.DownloadResult.CONNECTION_ERROR);
		
		// Start the download
		return startDownloading(params[0]);
	}
	
	@Override
	protected void onProgressUpdate(Integer... progress) {
		mBuilder.setProgress(100, progress[0], false);
		StringBuilder sb = new StringBuilder();
		
		switch (mItem) {
		case APlaylist:
			sb.append(mContext.getString(R.string.download_noti_title_playlist));
			sb.append(" ");
			sb.append(App.mClementine.getPlaylists().get(mPlaylistId).getName());
			break;
		case CurrentItem:
			sb.append(mContext.getString(R.string.download_noti_title_song));
			sb.append(" ");
			sb.append(mCurrentSong.getTitle());
			break;
		case ItemAlbum:
			sb.append(mContext.getString(R.string.download_noti_title_album));
			sb.append(" ");
			sb.append(mCurrentSong.getAlbum());
			break;
		}
		
		mTitle = sb.toString();
		
		sb = new StringBuilder();
		
		if (mCurrentSong.equals(new MySong())) {
			sb.append(mContext.getString(R.string.connectdialog_connecting));	
		} else {
			sb.append("(");
			sb.append(mCurrentFile);
			sb.append("/");
			sb.append(mFileCount);
			sb.append(") ");
			sb.append(mCurrentSong.getArtist());
			sb.append(" - ");
			sb.append(mCurrentSong.getTitle());
		}
		
		mSubtitle = sb.toString();
		
		mBuilder.setContentTitle(mTitle);
		mBuilder.setContentText(mSubtitle);
		
        // Displays the progress bar for the first time.
        mNotifyManager.notify(mId, mBuilder.build());
    }
	
	@Override
    protected void onCancelled() {
		// When the loop is finished, updates the notification
		mSubtitle = mContext.getString(R.string.download_noti_canceled);
        mBuilder.setContentText(mSubtitle)
        		.setOngoing(false)
        		.setAutoCancel(true)
                .setProgress(0,0,false);
        mBuilder.setPriority(Notification.PRIORITY_MIN);
        mNotifyManager.cancel(mId);
        mNotifyManager.notify(mId, mBuilder.build());
        
        mCurrentProgress = 100;
    }

	@Override
    protected void onPostExecute(DownloaderResult result) {
    	// When the loop is finished, updates the notification
		if (result == null)
			mSubtitle = mContext.getString(R.string.download_noti_canceled);
		else {
			switch (result.getResult()) {
			case CONNECTION_ERROR:
				mSubtitle = mContext.getString(R.string.download_noti_canceled);
				break;
			case FOBIDDEN:
				mSubtitle = mContext.getString(R.string.download_noti_forbidden);
				break;
			case INSUFFIANT_SPACE:
				mSubtitle = mContext.getString(R.string.download_noti_insufficient_space);
				break;
			case NOT_MOUNTED:
				mSubtitle = mContext.getString(R.string.download_noti_not_mounted);
				break;
			case ONLY_WIFI:
				mSubtitle = mContext.getString(R.string.download_noti_only_wifi);
				break;
			case SUCCESSFUL:
				mSubtitle = mContext.getString(R.string.download_noti_complete);
				break;
			}
		}
		
		mBuilder.setContentTitle(mTitle);
		mBuilder.setContentText(mSubtitle);
		
		mCurrentProgress = 100;
			
        mBuilder.setOngoing(false);
        mBuilder.setProgress(0,0,false);
        mBuilder.setAutoCancel(true);
        mBuilder.setPriority(Notification.PRIORITY_MIN);
        mNotifyManager.cancel(mId);
        mNotifyManager.notify(mId, mBuilder.build());
    }
	
	private PendingIntent buildNotificationIntent() {
	    Intent intent = new Intent(App.mApp, MainActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    intent.putExtra(App.NOTIFICATION_ID, mId);
	    intent.setData(Uri.parse("ClemetineDownload" + mId));
	    
	    // Create a TaskStack, so the app navigates correctly backwards
	    TaskStackBuilder stackBuilder = TaskStackBuilder.create(App.mApp);
	    stackBuilder.addParentStack(MainActivity.class);
	    stackBuilder.addNextIntent(intent);
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
    	
    	return mClient.createConnection(ClementineMessageFactory.buildConnectMessage(ip, port, authCode, false, true));
    }

    /**
     * Start the Downlaod
     */
    private DownloaderResult startDownloading(ClementineMessage clementineMessage) {
    	boolean downloadFinished = false;
    	DownloaderResult result = new DownloaderResult(DownloadResult.SUCCESSFUL);
    	File f = null;
    	FileOutputStream fo = null;
    	
    	// Do we have a playlist?
    	mIsPlaylist = (clementineMessage.getMessage().getRequestDownloadSongs().getDownloadItem() == DownloadItem.APlaylist);
    	if (mIsPlaylist) {
    		mPlaylistId = clementineMessage.getMessage().getRequestDownloadSongs().getPlaylistId();
    	}
    	
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
					if (f != null) f.delete();
				} catch (IOException e) {}
				
				break;
			}
			
			// Get the raw protocol buffer
			ClementineMessage message = mClient.getProtoc();
			
			// Check if an error occured
			if (message == null || message.isErrorMessage()) {
				result = new DownloaderResult(DownloadResult.CONNECTION_ERROR);
				break;
			}
			
			// Is the download forbidden?
			if (message.getMessageType() == MsgType.DISCONNECT) {
				result = new DownloaderResult(DownloadResult.FOBIDDEN);
				break;
			}
			
			// Download finished?
			if (message.getMessageType() == MsgType.DOWNLOAD_QUEUE_EMPTY) {
				break;
			}
			
			// Ignore other elements!
			if (message.getMessageType() != MsgType.SONG_FILE_CHUNK)
				continue;
			
			ResponseSongFileChunk chunk = message.getMessage().getResponseSongFileChunk();
			
			// If we received chunk no 0, then we have to decide wether to
			// accept the song offered or not
			if (chunk.getChunkNumber() == 0) {
				processSongOffer(chunk);
				
				// Update progress here to. If the first (and only) file exists and shall not be
				// overriten, the notification bar shows NULL.
				mCurrentSong = MySong.fromProtocolBuffer(chunk.getSongMetadata());
				publishProgress(mCurrentProgress);
				continue;
			}
			
			try {				
				// Check if we need to create a new file
				if (f == null) {
					// Check if we have enougth free space
					if (chunk.getSize() > Utilities.getFreeSpace()) {
						result = new DownloaderResult(DownloadResult.INSUFFIANT_SPACE);
						break;
					}
					
					File dir = new File(BuildDirPath(chunk));
					f = new File(BuildFilePath(chunk));
					
					// User wants to override files, so delete it here!
					// The check was already done in processSongOffer()
					if (f.exists()) {
						f.delete();
					}
					
					dir.mkdirs();
					f.createNewFile();
					fo = new FileOutputStream(f);
					
					// File for download fragment
					mFileUri = Uri.fromFile(f);
				}
				
				// Write chunk to sdcard
				fo.write(chunk.getData().toByteArray());
				
				// Have we downloaded all chunks?
				if (chunk.getChunkCount() == chunk.getChunkNumber()) {
					// Index file
					MediaScannerConnection.scanFile(mContext, new String[]{f.getAbsolutePath()}, null, null);
					fo.flush();
					fo.close();
					f = null;				
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
	
		return result;
    }
    
    /**
     * This method checks if the offered file exists and sends a response to Clementine.
     * If the file does not exist -> Download file
     * otherwise
     * 	 The user wants to override existing files -> Download file
     *   otherwise
     *     refuse file
     * @param chunk The chunk with the metadata
     * @return a boolean indicating if the song will be sent or not
     */
    private boolean processSongOffer(ResponseSongFileChunk chunk) {
    	File f = new File(BuildFilePath(chunk));
    	boolean accept = true;
    	
    	if (f.exists() && !mOverrideExistingFiles) 
    		accept = false;	

    	mClient.sendRequest(ClementineMessageFactory.buildSongOfferResponse(accept));
    	
    	// File for download fragment
    	if (f.exists())
    		mFileUri = Uri.fromFile(f);
    	
    	return accept;
	}

	/**
     * Updates the current notification
     * @param chunk The current downloaded chunk
     */
    private void updateProgress(ResponseSongFileChunk chunk) {
    	// Update notification
		mFileCount = chunk.getFileCount();
		mCurrentFile = chunk.getFileNumber();
		double progress = (((double) (chunk.getFileNumber()-1) /  (double) chunk.getFileCount()) 
				         + (((double) chunk.getChunkNumber() / (double) chunk.getChunkCount()) / (double) chunk.getFileCount())) 
				         * 100;
		
		publishProgress((int) progress);
		
		mCurrentProgress = (int) progress;
    }
    
    /**
     * Return the folder where the file will be placed
     * @param chunk The chunk
     */
    private String BuildDirPath(ResponseSongFileChunk chunk) {
    	String defaultPath = Environment.getExternalStorageDirectory() + "/ClementineMusic";
        String path = mSharedPref.getString(App.SP_DOWNLOAD_DIR, defaultPath);
        
    	StringBuilder sb = new StringBuilder();
    	sb.append(path);
    	sb.append(File.separator);
    	if (mIsPlaylist && mCreatePlaylistDir) {
    		sb.append(App.mClementine.getPlaylists().get(mPlaylistId).getName());
    		sb.append(File.separator);
    	}
    	
    	// Create artist/album subfolder only when we have no playlist
    	// or user set the settings
    	if (!mIsPlaylist ||
    		mIsPlaylist && mCreatePlaylistArtistDir) {
    		// Append artist name
	    	if (chunk.getSongMetadata().getAlbumartist().length() == 0)
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
    private String BuildFilePath(ResponseSongFileChunk chunk) {
    	StringBuilder sb = new StringBuilder();
    	sb.append(BuildDirPath(chunk));
    	sb.append(File.separator);
    	sb.append(chunk.getSongMetadata().getFilename());
    	
    	return sb.toString();
    }

	public DownloadItem getItem() {
		return mItem;
	}

	public int getCurrentProgress() {
		return mCurrentProgress;
	}

	public String getTitle() {
		return mTitle;
	}

	public String getSubtitle() {
		return mSubtitle;
	}
	
	/**
	 * Get the last downloaded file
	 * @return
	 */
	public Uri getLastFileUri() {
		return mFileUri;
	}
}
