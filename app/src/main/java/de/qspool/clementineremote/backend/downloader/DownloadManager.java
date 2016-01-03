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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.SparseArray;
import android.widget.Toast;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.ui.MainActivity;

public class DownloadManager {

    public final static int NOTIFICATION_ID_DOWNLOADS = 129312;

    public final static int NOTIFICATION_ID_DOWNLOADS_FINISHED = 129313;

    private Context mContext;

    private NotificationManager mNotifyManager;

    private NotificationCompat.Builder mActiveNofiticationBuilder;

    private SparseArray<ClementineSongDownloader> mActiveDownloads = new SparseArray<>();

    private SparseArray<ClementineSongDownloader> mFinishedDownloads = new SparseArray<>();

    private int mIds;

    private SharedPreferences mSharedPref;

    private static DownloadManager instance;

    public static DownloadManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "No DownloadManager instance found. Use getInstance(Context) to create one");
        }
        return DownloadManager.instance;
    }

    public static DownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadManager(context);
        }
        return instance;
    }

    private DownloadManager(Context context) {
        mContext = context;
        mNotifyManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        createNewActiveNotification();
    }

    @SuppressLint("InlinedApi")
    private void createNewActiveNotification() {
        mActiveNofiticationBuilder = new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.download_noti_title))
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        // Set the result intent
        mActiveNofiticationBuilder.setContentIntent(buildNotificationIntent());

        mActiveNofiticationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private PendingIntent buildNotificationIntent() {
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(ClementineMediaSessionNotification.EXTRA_NOTIFICATION_ID,
                NOTIFICATION_ID_DOWNLOADS);

        // Create a TaskStack, so the app navigates correctly backwards
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(intent);
        return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public boolean addJob(ClementineMessage clementineMessage) {
        ClementineSongDownloader songDownloader = new ClementineSongDownloader();
        songDownloader.setId(mIds);
        songDownloader.setSongDownloaderListener(new SongDownloaderListener() {

            @Override
            public void onProgress(DownloadStatus progress) {
                DownloadManager.this.onProgress(progress);
            }

            @SuppressLint("InlinedApi")
            @Override
            public void onDownloadResult(DownloaderResult result) {
                int id = result.getId();

                // Move the download to the finished list
                mFinishedDownloads.append(id, mActiveDownloads.get(id));
                mActiveDownloads.remove(id);

                // Remove the notification if this was the last download
                if (mActiveDownloads.size() == 0) {
                    mNotifyManager.cancel(NOTIFICATION_ID_DOWNLOADS);
                } else {
                    // Build a new active notification as the old one might be
                    // expanded and looks now strange
                    createNewActiveNotification();
                }

                //download_noti_n_finished
                String title = mContext.getResources().getQuantityString(
                        R.plurals.download_noti_n_finished,
                        mFinishedDownloads.size(),
                        mFinishedDownloads.size());
                NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                        mContext)
                        .setContentTitle(title)
                        .setContentText(mContext.getString(result.getMessageStringId()))
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setPriority(NotificationCompat.PRIORITY_MIN)
                        .setAutoCancel(true)
                        .setContentIntent(buildNotificationIntent())
                        .setVisibility(Notification.VISIBILITY_PUBLIC);

                // Displays the progress bar for the first time.
                mNotifyManager
                        .notify(NOTIFICATION_ID_DOWNLOADS_FINISHED, notificationBuilder.build());
            }
        });

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);

        // Get preferences and set download settings
        String defaultPath;
        if (mContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) == null &&
                !mSharedPref.contains(SharedPreferencesKeys.SP_DOWNLOAD_DIR)) {
            Toast.makeText(mContext, R.string.download_noti_not_mounted, Toast.LENGTH_LONG).show();
            return false;
        } else {
            File defaultFile = mContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (defaultFile != null)
                defaultPath = defaultFile.getAbsolutePath();
            else
                defaultPath = "";
        }

        songDownloader.setDownloadPath(
                mSharedPref.getString(SharedPreferencesKeys.SP_DOWNLOAD_DIR, defaultPath));
        songDownloader.setDownloadOnWifiOnly(
                mSharedPref.getBoolean(SharedPreferencesKeys.SP_WIFI_ONLY, false));
        songDownloader.setCreatePlaylistDir(mSharedPref
                .getBoolean(SharedPreferencesKeys.SP_DOWNLOAD_SAVE_OWN_DIR, false));
        songDownloader.setCreateArtistDir(mSharedPref
                .getBoolean(SharedPreferencesKeys.SP_DOWNLOAD_PLAYLIST_CRT_ARTIST_DIR, true));
        songDownloader.setCreateAlbumDir(mSharedPref
                .getBoolean(SharedPreferencesKeys.SP_DOWNLOAD_PLAYLIST_CRT_ALBUM_DIR, true));
        songDownloader.setOverrideExistingFiles(mSharedPref
                .getBoolean(SharedPreferencesKeys.SP_DOWNLOAD_OVERRIDE, false));

        // Show a toast that the download is starting
        Toast.makeText(mContext, R.string.player_download_started, Toast.LENGTH_SHORT).show();

        mActiveDownloads.append(mIds, songDownloader);

        mIds++;

        songDownloader.startDownload(clementineMessage);

        return true;
    }

    public List<ClementineSongDownloader> getAllDownloaders() {
        LinkedList<ClementineSongDownloader> downloaders = new LinkedList<>();

        for (int i = 0; i < mActiveDownloads.size(); ++i) {
            downloaders.add(mActiveDownloads.valueAt(i));
        }
        for (int i = 0; i < mFinishedDownloads.size(); ++i) {
            downloaders.add(mFinishedDownloads.valueAt(i));
        }

        return downloaders;
    }

    public void removeDownloader(int id) {
        mActiveDownloads.remove(id);
        mFinishedDownloads.remove(id);
    }

    public void shutdown() {
        for (int i = 0; i < mActiveDownloads.size(); ++i) {
            mActiveDownloads.valueAt(i).cancel(true);
        }

        mNotifyManager.cancel(NOTIFICATION_ID_DOWNLOADS);
        mNotifyManager.cancel(NOTIFICATION_ID_DOWNLOADS_FINISHED);
    }

    public String getTitleForItem(ClementineSongDownloader downloader) {
        DownloadStatus status = downloader.getDownloadStatus();

        StringBuilder sb = new StringBuilder();

        switch (status.getState()) {
            case IDLE:
                sb.append(mContext.getString(R.string.download_noti_title));
                break;
            case TRANSCODING:
                sb.append(mContext.getString(R.string.download_noti_transcoding));
                break;
            case DOWNLOADING:
            case FINISHED:
                switch (downloader.getItem()) {
                    case APlaylist:
                        sb.append(mContext.getString(R.string.download_noti_title_playlist));
                        sb.append(" ");
                        sb.append(downloader.getPlaylistName());
                        break;
                    case CurrentItem:
                        sb.append(mContext.getString(R.string.download_noti_title_song));
                        sb.append(" ");
                        sb.append(status.getSong().getTitle());
                        break;
                    case Urls:
                        sb.append(mContext.getString(R.string.download_noti_title_songs));
                        sb.append(" ");
                        sb.append(status.getSong().getArtist());
                        sb.append(" - ");
                        sb.append(status.getSong().getTitle());
                        break;
                    case ItemAlbum:
                        sb.append(mContext.getString(R.string.download_noti_title_album));
                        sb.append(" ");
                        sb.append(status.getSong().getAlbum());
                        break;
                }
        }

        return sb.toString();
    }

    public String getSubtitleForItem(ClementineSongDownloader downloader) {
        DownloadStatus status = downloader.getDownloadStatus();

        StringBuilder sb = new StringBuilder();
        switch (status.getState()) {

            case IDLE:
                break;
            case TRANSCODING:
                sb.append("(");
                sb.append(status.getTranscodingFinished());
                sb.append("/");
                sb.append(status.getTranscodingTotal());
                sb.append(") ");
                sb.append(mContext.getString(R.string.download_noti_transcoding_subtitle));
                break;
            case DOWNLOADING:
                sb.append("(");
                sb.append(status.getCurrentFileIndex());
                sb.append("/");
                sb.append(status.getTotalFiles());
                sb.append(") ");
                sb.append(status.getSong().getArtist());
                sb.append(" - ");
                sb.append(status.getSong().getTitle());
                break;
            case FINISHED:
                sb.append("(");
                sb.append(status.getCurrentFileIndex());
                sb.append("/");
                sb.append(status.getTotalFiles());
                sb.append(") ");
                sb.append(
                        mContext.getString(downloader.getDownloaderResult().getMessageStringId()));
                break;
        }
        return sb.toString();
    }

    private void onProgress(DownloadStatus status) {
        switch (status.getState()) {
            case IDLE:
                mActiveNofiticationBuilder.setContentTitle(
                        mContext.getString(R.string.download_noti_title));
                mActiveNofiticationBuilder.setContentText(
                        mContext.getString(R.string.connectdialog_connecting));
                break;
            case TRANSCODING:
            case DOWNLOADING:
                if (mActiveDownloads.size() == 1) {
                    setNotificationSingleDownload(mActiveNofiticationBuilder, status);
                } else {
                    setNotificationMultipleDownloads(mActiveNofiticationBuilder);
                }
                break;
        }

        // Displays the progress bar for the first time.
        mNotifyManager.notify(NOTIFICATION_ID_DOWNLOADS, mActiveNofiticationBuilder.build());
    }

    private void setNotificationSingleDownload(NotificationCompat.Builder notificationBuilder,
            DownloadStatus status) {
        ClementineSongDownloader downloader = mActiveDownloads.get(status.getId());

        notificationBuilder.setProgress(100, (int) status.getProgress(), false);

        // Notification Title
        notificationBuilder.setContentTitle(getTitleForItem(downloader));

        // Notification subtitle
        notificationBuilder.setContentText(getSubtitleForItem(downloader));
    }

    private void setNotificationMultipleDownloads(NotificationCompat.Builder notificationBuilder) {
        NotificationCompat.InboxStyle downloadItems =
                new NotificationCompat.InboxStyle();

        // Title
        String title = mContext.getResources().getQuantityString(R.plurals.download_noti_n_downloads,
                mActiveDownloads.size(),
                mActiveDownloads.size());
        notificationBuilder.setContentTitle(title);

        // Total progress in subtitle
        int progress = 0;
        for (int i = 0; i < mActiveDownloads.size(); ++i) {
            ClementineSongDownloader downloader = mActiveDownloads.valueAt(i);
            DownloadStatus status = downloader.getDownloadStatus();
            progress += status.getProgress();

            downloadItems.addLine(getTitleForItem(downloader));
        }

        String subtitle = mContext.getString(R.string.download_noti_n_progress);
        subtitle = subtitle.replace("%d", String.valueOf(progress / mActiveDownloads.size()));
        notificationBuilder.setContentText(subtitle);
        downloadItems.setBigContentTitle(subtitle); // Show the status on expanded notification

        notificationBuilder.setStyle(downloadItems);
        notificationBuilder.setProgress(0, 0, false);
    }
}
