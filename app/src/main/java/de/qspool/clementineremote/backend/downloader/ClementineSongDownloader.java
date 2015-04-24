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

package de.qspool.clementineremote.backend.downloader;

import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.ClementineSimpleConnection;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.elements.DownloaderResult.DownloadResult;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSongFileChunk;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.utils.DownloadSpeedCalculator;
import de.qspool.clementineremote.utils.IDownloadCalculatorSource;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineSongDownloader extends
        AsyncTask<ClementineMessage, DownloadStatus, DownloaderResult> {

    public static class DownloadedSong {
        public MySong song;
        public Uri uri;

        public DownloadedSong(MySong song, Uri uri) {
            this.song = song;
            this.uri = uri;
        }
    }

    private int mId;

    private SongDownloaderListener mSongDownloaderListener;

    private DownloadStatus mDownloadStatus;

    private DownloaderResult mDownloaderResult;

    private ClementineSimpleConnection mClient = new ClementineSimpleConnection();

    private String mDownloadPath;

    private String mPlaylistName;

    private boolean mDownloadOnWifiOnly;

    private boolean mIsPlaylist = false;

    private boolean mCreatePlaylistDir = false;

    private boolean mCreatePlaylistArtistDir = false;

    private boolean mOverrideExistingFiles = false;

    private DownloadItem mItem;

    private LinkedList<DownloadedSong> mDownloadedSongs = new LinkedList<>();

    private int mTotalFileSize;

    private int mTotalDownloaded;

    private DownloadSpeedCalculator mDownloadSpeed;

    public ClementineSongDownloader() {
        mDownloadStatus = new DownloadStatus(mId).setState(
                DownloadStatus.DownloaderState.IDLE);
    }

    public void startDownload(ClementineMessage message) {
        if (mSongDownloaderListener == null) {
            throw new IllegalStateException("No listener defined!");
        }
        mItem = message.getMessage().getRequestDownloadSongs().getDownloadItem();

        this.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
    }

    @Override
    protected DownloaderResult doInBackground(ClementineMessage... params) {
        publishProgress(new DownloadStatus(mId).setState(
                DownloadStatus.DownloaderState.IDLE));

        if (mDownloadOnWifiOnly && !Utilities.onWifi()) {
            return new DownloaderResult(mId, DownloaderResult.DownloadResult.ONLY_WIFI);
        }

        // First create a connection
        if (!connect()) {
            return new DownloaderResult(mId, DownloaderResult.DownloadResult.CONNECTION_ERROR);
        }

        mDownloadSpeed = new DownloadSpeedCalculator(new IDownloadCalculatorSource() {
            @Override
            public int getBytesTotalDownloaded() {
                return getTotalDownloaded();
            }
        });

        // Start the download
        return startDownloading(params[0]);
    }

    @Override
    protected void onProgressUpdate(DownloadStatus... progress) {
        mDownloadStatus = progress[0];
        mSongDownloaderListener.onProgress(mDownloadStatus);
    }

    @Override
    protected void onCancelled(DownloaderResult result) {
        onPostExecute(result);
    }

    @Override
    protected void onPostExecute(DownloaderResult result) {
        mDownloaderResult = result;
        mDownloadStatus.setState(DownloadStatus.DownloaderState.FINISHED)
                       .setProgress(100);
        mSongDownloaderListener.onDownloadResult(result);
    }

    /**
     * Connect to Clementine
     *
     * @return true if the connection was established, false if not
     */
    private boolean connect() {
        ClementineMessage connectMessage = App.ClementineConnection.getRequestConnect();
        int authCode = connectMessage.getMessage().getRequestConnect().getAuthCode();

        return mClient.createConnection(
                ClementineMessageFactory.buildConnectMessage(
                        connectMessage.getIp(),
                        connectMessage.getPort(),
                        authCode,
                        false,
                        true)
        );
    }

    /**
     * Start the Downlaod
     */
    private DownloaderResult startDownloading(ClementineMessage clementineMessage) {
        DownloaderResult result = new DownloaderResult(mId, DownloadResult.SUCCESSFUL);

        File f = null;
        FileOutputStream fo = null;
        MySong currentSong = new MySong();

        // Do we have a playlist?
        checkIsPlaylist(clementineMessage);

        // Now request the songs
        mClient.sendRequest(clementineMessage);

        while (true) {
            // Check if the user canceled the process
            if (isCancelled()) {
                // Close the stream and delete the incomplete file
                try {
                    if (fo != null) {
                        fo.flush();
                        fo.close();
                    }
                    if (f != null) {
                        f.delete();
                    }
                } catch (IOException e) {
                }

                result = new DownloaderResult(mId, DownloadResult.CANCELLED);
                break;
            }

            // Get the raw protocol buffer
            ClementineMessage message = mClient.getProtoc(0);

            // Check if an error occured
            if (message.isErrorMessage()) {
                result = new DownloaderResult(mId, DownloadResult.CONNECTION_ERROR);
                break;
            }

            // Is the download forbidden?
            if (message.getMessageType() == MsgType.DISCONNECT) {
                result = new DownloaderResult(mId, DownloadResult.FOBIDDEN);
                break;
            }

            // Download finished?
            if (message.getMessageType() == MsgType.DOWNLOAD_QUEUE_EMPTY) {
                break;
            }

            // Total file size
            if (message.getMessageType() == MsgType.DOWNLOAD_TOTAL_SIZE) {
                mTotalFileSize = message.getMessage().getResponseDownloadTotalSize().getTotalSize();
                continue;
            }

            // Transcoding files?
            if (message.getMessageType() == MsgType.TRANSCODING_FILES) {
                parseTranscodingMessage(message);
                continue;
            }

            // Ignore other elements!
            if (message.getMessageType() != MsgType.SONG_FILE_CHUNK) {
                continue;
            }

            ResponseSongFileChunk chunk = message.getMessage().getResponseSongFileChunk();

            // If we received chunk no 0, then we have to decide wether to
            // accept the song offered or not
            if (chunk.getChunkNumber() == 0) {
                currentSong = MySong.fromProtocolBuffer(chunk.getSongMetadata());
                boolean accepted = processSongOffer(currentSong, chunk);

                // If we don't accept the file, add the size so the DownloadManager can show it correctly
                if (!accepted) {
                    mTotalDownloaded += chunk.getSize();
                    updateProgress(chunk, currentSong);
                }

                continue;
            }

            try {
                // Check if we need to create a new file
                if (f == null) {
                    // Check if we have enougth free space
                    if (chunk.getSize() > Utilities.getFreeSpaceExternal()) {
                        result = new DownloaderResult(mId, DownloadResult.INSUFFIANT_SPACE);
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
                }

                // Write chunk to sdcard
                fo.write(chunk.getData().toByteArray());

                mTotalDownloaded += chunk.getData().size();

                // Have we downloaded all chunks?
                if (chunk.getChunkCount() == chunk.getChunkNumber()) {
                    // Index file
                    MediaScannerConnection
                            .scanFile(App.getApp(), new String[]{f.getAbsolutePath()}, null, null);
                    fo.flush();
                    fo.close();
                    f = null;
                }

                // Update notification
                updateProgress(chunk, currentSong);
            } catch (IOException e) {
                result = new DownloaderResult(mId, DownloaderResult.DownloadResult.NOT_MOUNTED);
                break;
            }

        }

        // Disconnect at the end
        mClient.disconnect(ClementineMessage.getMessage(MsgType.DISCONNECT));

        return result;
    }

    private void parseTranscodingMessage(ClementineMessage message) {
        ClementineRemoteProtocolBuffer.ResponseTranscoderStatus status = message.getMessage()
                .getResponseTranscoderStatus();

        publishProgress(new DownloadStatus(mId)
                .setState(DownloadStatus.DownloaderState.TRANSCODING)
                .setTranscodingTotal(status.getTotal())
                .setTranscodingFinished(status.getProcessed()));
    }

    private void checkIsPlaylist(ClementineMessage clementineMessage) {
        mIsPlaylist = (clementineMessage.getMessage().getRequestDownloadSongs().getDownloadItem()
                == DownloadItem.APlaylist);
        if (mIsPlaylist) {
            int id = clementineMessage.getMessage().getRequestDownloadSongs().getPlaylistId();
            mPlaylistName = App.Clementine.getPlaylistManager().getPlaylist(id).getName();
        }
    }

    /**
     * This method checks if the offered file exists and sends a response to Clementine.
     * If the file does not exist -> Download file
     * otherwise
     * The user wants to override existing files -> Download file
     * otherwise
     * refuse file
     *
     * @param chunk The chunk with the metadata
     * @return a boolean indicating if the song will be sent or not
     */
    private boolean processSongOffer(MySong song, ResponseSongFileChunk chunk) {
        File f = new File(BuildFilePath(chunk));
        boolean accept = true;

        if (f.exists() && !mOverrideExistingFiles) {
            accept = false;
        }

        mClient.sendRequest(ClementineMessageFactory.buildSongOfferResponse(accept));

        // Save the downloaded files
        mDownloadedSongs.add(new DownloadedSong(song, Uri.fromFile(f)));

        return accept;
    }

    /**
     * Updates the current notification.
     *
     * @param chunk The current downloaded chunk
     */
    private void updateProgress(ResponseSongFileChunk chunk, MySong song) {
        double progress = 0;
        if (chunk.getChunkNumber() > 0) {
            progress = (((double) (chunk.getFileNumber() - 1) / (double) chunk.getFileCount())
                    + (((double) chunk.getChunkNumber() / (double) chunk.getChunkCount())
                    / (double) chunk.getFileCount()))
                    * 100;
        }

        publishProgress(new DownloadStatus(mId)
                .setProgress(progress)
                .setSong(song)
                .setCurrentFileIndex(chunk.getFileNumber())
                .setTotalFiles(chunk.getFileCount())
                .setState(DownloadStatus.DownloaderState.DOWNLOADING));
    }

    /**
     * Return the folder where the file will be placed
     *
     * @param chunk The chunk
     */
    private String BuildDirPath(ResponseSongFileChunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append(mDownloadPath);
        sb.append(File.separator);
        if (mIsPlaylist && mCreatePlaylistDir) {
            sb.append(Utilities.removeInvalidFileCharacters(mPlaylistName));
            sb.append(File.separator);
        }

        // Create artist/album subfolder only when we have no playlist
        // or user set the settings
        if (!mIsPlaylist ||
                mIsPlaylist && mCreatePlaylistArtistDir) {
            // Append artist name
            if (chunk.getSongMetadata().getAlbumartist().length() == 0) {
                sb.append(
                        Utilities.removeInvalidFileCharacters(chunk.getSongMetadata().getArtist()));
            } else {
                sb.append(Utilities
                        .removeInvalidFileCharacters(chunk.getSongMetadata().getAlbumartist()));
            }

            // append album
            sb.append(File.separator);
            sb.append(Utilities.removeInvalidFileCharacters(chunk.getSongMetadata().getAlbum()));
        }

        return sb.toString();
    }

    /**
     * Build the filename
     *
     * @param chunk The SongFileChunk
     * @return /sdcard/Music/Artist/Album/file.mp3
     */
    private String BuildFilePath(ResponseSongFileChunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append(BuildDirPath(chunk));
        sb.append(File.separator);
        sb.append(Utilities.removeInvalidFileCharacters(chunk.getSongMetadata().getFilename()));

        return sb.toString();
    }

    public DownloadItem getItem() {
        return mItem;
    }

    /**
     * Get the downloaded songs
     */
    public LinkedList<DownloadedSong> getDownloadedSongs() {
        return mDownloadedSongs;
    }

    public int getTotalFileSize() {
        return mTotalFileSize;
    }

    public int getTotalDownloaded() {
        return mTotalDownloaded;
    }

    public int getDownloadSpeedPerSecond() {
        return mDownloadSpeed.getDownloadSpeed();
    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        mId = id;
    }

    public void setSongDownloaderListener(SongDownloaderListener songDownloaderListener) {
        mSongDownloaderListener = songDownloaderListener;
    }

    public void setDownloadOnWifiOnly(boolean downloadOnWifiOnly) {
        mDownloadOnWifiOnly = downloadOnWifiOnly;
    }

    public void setDownloadPath(String downloadPath) {
        mDownloadPath = downloadPath;
    }

    public void setCreatePlaylistDir(boolean createPlaylistDir) {
        mCreatePlaylistDir = createPlaylistDir;
    }

    public void setCreatePlaylistArtistDir(boolean createPlaylistArtistDir) {
        mCreatePlaylistArtistDir = createPlaylistArtistDir;
    }

    public void setOverrideExistingFiles(boolean overrideExistingFiles) {
        mOverrideExistingFiles = overrideExistingFiles;
    }

    public String getPlaylistName() {
        return mPlaylistName;
    }

    public DownloadStatus getDownloadStatus() {
        return mDownloadStatus;
    }

    public DownloaderResult getDownloaderResult() {
        return mDownloaderResult;
    }
}
