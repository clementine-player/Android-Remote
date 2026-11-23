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

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.downloader.DownloadNetwork;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.elements.DownloaderResult.DownloadResult;
import de.qspool.clementineremote.backend.library.LibraryDatabaseHelper;
import de.qspool.clementineremote.backend.listener.OnLibraryDownloadListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseLibraryChunk;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineLibraryDownloader extends
        BackgroundTask<ClementineMessage, Long, DownloaderResult> {

    private static final int READ_TIMEOUT_MS = 3000;

    private static final long IDLE_TIMEOUT_MS = 60_000L;

    private final String TAG = "ClementineLibraryDownloader";

    private Context mContext;

    private SharedPreferences mSharedPref;

    private final ClementineSimpleConnection mClient = new ClementineSimpleConnection();

    private LibraryDatabaseHelper mLibrary;

    // Iterated on main-thread callbacks while UI listeners register/unregister concurrently.
    private final List<OnLibraryDownloadListener> listeners
            = new CopyOnWriteArrayList<>();

    private int mTotalSize;

    public ClementineLibraryDownloader(Context context) {
        mContext = context;
        mLibrary = new LibraryDatabaseHelper();
        mSharedPref = App.getPreferences();
    }

    /**
     * Add a OnLibraryDownloadFinishedListener. It is emitted when the download
     * is finished and the library file is available
     *
     * @param l The listener object
     */
    public void addOnLibraryDownloadListener(
            OnLibraryDownloadListener l) {
        listeners.add(l);
    }

    public void removeOnLibraryDownloadListener(OnLibraryDownloadListener l) {
        listeners.remove(l);
    }

    public void startDownload(ClementineMessage message) {
        execute(message);
    }

    @Override
    protected DownloaderResult doInBackground(ClementineMessage message) {
        if (!DownloadNetwork.chosen(mSharedPref).allowsCurrentNetwork(mContext)) {
            return new DownloaderResult(0, DownloaderResult.DownloadResult.NETWORK_NOT_ALLOWED);
        }

        // First create a connection
        if (!connect()) {
            return new DownloaderResult(0, DownloaderResult.DownloadResult.CONNECTION_ERROR);
        }

        // Start the download
        return startDownloading(message);
    }

    @Override
    protected void onProgressUpdate(Long progress) {
        fireOnProgressUpdateListener(progress);

        // Guard mTotalSize > 0 so a 0-byte/uninitialized state cannot trigger optimize UI.
        if (mTotalSize > 0 && progress == mTotalSize) {
            fireOnOptimizeLibraryListener();
        }
    }

    @Override
    protected void onCancelRequested() {
        // Socket.connect() ignores Thread.interrupt(); close an in-flight handshake socket immediately.
        mClient.abortConnecting();
    }

    @Override
    protected void onCancelled(DownloaderResult result) {
        fireOnLibraryDownloadFinishedListener(new DownloaderResult(0,
                DownloaderResult.DownloadResult.CANCELLED));
    }

    @Override
    protected void onPostExecute(DownloaderResult result) {
        // Notify the listeners
        fireOnLibraryDownloadFinishedListener(result);
    }

    /**
     * Connect to Clementine
     *
     * @return true if the connection was established, false if not
     */
    private boolean connect() {
        // Reject blank hosts and fall back to DefaultPort for unparsable or out-of-range ports before InetSocketAddress throws.
        String ip = mSharedPref.getString(SharedPreferencesKeys.SP_KEY_IP, "");
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(mSharedPref.getString(SharedPreferencesKeys.SP_KEY_PORT,
                    String.valueOf(Clementine.DefaultPort)));
            if (port < 1 || port > 65535) {
                port = Clementine.DefaultPort;
            }
        } catch (NumberFormatException e) {
            port = Clementine.DefaultPort;
        }
        int authCode = mSharedPref.getInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, 0);

        return mClient.createConnection(ClementineMessageFactory
                .buildConnectMessage(ip.trim(), port, authCode, false, true));
    }

    /**
     * Start the Download
     */
    private DownloaderResult startDownloading(
            ClementineMessage clementineMessage) {
        boolean downloadFinished = false;
        DownloaderResult result = new DownloaderResult(0,
                DownloadResult.SUCCESSFUL);
        File f = null;
        FileOutputStream fo = null;
        long lastActivityMs = System.currentTimeMillis();

        try {
            // Now request the songs
            if (!mClient.sendRequest(clementineMessage)) {
                return new DownloaderResult(0, DownloadResult.CONNECTION_ERROR);
            }

            while (!downloadFinished) {
                // Check if the user canceled the process
                if (isCancelled()) {
                    result = new DownloaderResult(0, DownloadResult.CANCELLED);
                    break;
                }

                // Read raw frames (without mutating global player state) using a finite timeout so
                // cancellation unblocks promptly while still allowing IDLE_TIMEOUT_MS for DB export.
                ClementineMessage message = mClient.readRawProtoc(READ_TIMEOUT_MS);

                if (message.isErrorMessage()) {
                    if (message.getErrorMessage() == ErrorMessage.TIMEOUT) {
                        if ((System.currentTimeMillis() - lastActivityMs) >= IDLE_TIMEOUT_MS) {
                            result = new DownloaderResult(0, DownloadResult.CONNECTION_ERROR);
                            break;
                        }
                        continue;
                    }
                    result = new DownloaderResult(0, DownloadResult.CONNECTION_ERROR);
                    break;
                }
                lastActivityMs = System.currentTimeMillis();

                // Is the download forbidden?
                if (message.getMessageType() == MsgType.DISCONNECT) {
                    result = new DownloaderResult(0, message);
                    break;
                }

                // Ignore other elements!
                if (message.getMessageType() != MsgType.LIBRARY_CHUNK) {
                    continue;
                }

                ResponseLibraryChunk chunk = message.getMessage()
                        .getResponseLibraryChunk();

                try {
                    // Check if we need to create a new file
                    if (f == null) {
                        long chunkSize = chunk.getSize();
                        // Widen to long before doubling so >1 GiB libraries don't overflow 32-bit int;
                        // size times 2 reserves room for optimizeTable() to build FTS tables alongside songs.
                        if (chunkSize < 0 || (chunkSize * 2L) > Utilities.getFreeSpaceExternal()) {
                            result = new DownloaderResult(0,
                                    DownloadResult.INSUFFIANT_SPACE);
                            break;
                        }
                        // Written beside the library on the phone, which stays
                        // usable until this one is complete.
                        f = mLibrary.getPartialLibraryDb();
                        if (f.getParentFile() != null) {
                            f.getParentFile().mkdirs();
                        }
                        if (f.exists()) {
                            f.delete();
                        }

                        f.createNewFile();
                        fo = new FileOutputStream(f);

                        mTotalSize = chunk.getSize();
                    }

                    // Write chunk to sdcard
                    fo.write(chunk.getData().toByteArray());

                    publishProgress(f.length());

                    // Have we downloaded all chunks?
                    if (chunk.getChunkCount() == chunk.getChunkNumber()) {
                        fo.flush();
                        fo.close();
                        fo = null;
                        f = null;
                        downloadFinished = true;
                    }

                } catch (IOException e) {
                    result = new DownloaderResult(0,
                            DownloaderResult.DownloadResult.NOT_MOUNTED);
                    break;
                }
            }
        } finally {
            // Always close streams and delete unfinished partials so an early exit/cancel cannot
            // leak open FDs or leave a half-written partial for the post-loop optimization check.
            if (fo != null) {
                try {
                    fo.close();
                } catch (IOException ignored) {
                }
            }
            if (!downloadFinished && f != null) {
                f.delete();
            }
            // Disconnect at the end
            mClient.disconnect(ClementineMessage.getMessage(MsgType.DISCONNECT));
        }

        // Optimize the library received, then put it in place of the old one
        File partial = mLibrary.getPartialLibraryDb();
        // Require downloadFinished as well as SUCCESSFUL so truncated exports are never optimized.
        if (result.getResult() == DownloadResult.SUCCESSFUL && downloadFinished && partial.exists()) {
            try {
                mLibrary.optimizeTable(partial);
                File library = mLibrary.getLibraryDb();
                if (!partial.renameTo(library)) {
                    // File.renameTo may refuse overwriting an existing file on some filesystems.
                    library.delete();
                    if (!partial.renameTo(library)) {
                        result = new DownloaderResult(0, DownloadResult.ERROR);
                    }
                }
            } catch (SQLiteException e) {
                // Database is damaged
                result = new DownloaderResult(0, DownloadResult.ERROR);
            }
        }
        partial.delete();

        return result;
    }

    /*
     * Fire the listeners
     */
    private void fireOnLibraryDownloadFinishedListener(DownloaderResult result) {
        for (OnLibraryDownloadListener l : listeners) {
            l.OnLibraryDownloadFinished(result);
        }
    }

    private void fireOnOptimizeLibraryListener() {
        for (OnLibraryDownloadListener l : listeners) {
            l.OnOptimizeLibrary();
        }
    }

    private void fireOnProgressUpdateListener(long progress) {
        for (OnLibraryDownloadListener l : listeners) {
            l.OnProgressUpdate(progress, mTotalSize);
        }
    }

}
