package de.qspool.clementineremote.backend.downloader;

import static de.qspool.clementineremote.testing.LockAssertions.assertWaitsForLock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import de.qspool.clementineremote.backend.BackgroundTask;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.player.MySong;

/** A song download, as the Downloads tab reads it while it runs. */
@RunWith(RobolectricTestRunner.class)
public class ClementineSongDownloaderTest {

    /**
     * The Downloads tab shows the speed of each running download, and a download runs from when
     * it starts, before it has connected to Clementine, or when it never does.
     */
    @Test
    public void hasNoSpeedBeforeItConnects() {
        Context context = RuntimeEnvironment.getApplication();
        // Without a network Wi-Fi only allows, the download ends before it connects.
        shadowOf(context.getSystemService(ConnectivityManager.class)).clearAllNetworks();

        ClementineSongDownloader downloader = new ClementineSongDownloader();
        downloader.setNetwork(DownloadNetwork.WIFI_ONLY);
        downloader.setSongDownloaderListener(new SongDownloaderListener() {
            @Override
            public void onProgress(DownloadStatus progress) {
            }

            @Override
            public void onDownloadResult(DownloaderResult result) {
            }
        });
        downloader.startDownload(
                ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.CurrentItem));

        // It's still running until the main thread hears it ended, which here it doesn't.
        assertEquals(BackgroundTask.Status.RUNNING, downloader.getStatus());
        assertEquals(0, downloader.getDownloadSpeedPerSecond());
    }

    /**
     * The download's thread adds songs while the Downloads tab lists them, so the tab gets a copy
     * of them, which it can't change.
     */
    @Test
    public void downloadedSongsAreAnUnmodifiableCopy() {
        ClementineSongDownloader downloader = new ClementineSongDownloader();
        downloader.addDownloadedSong(
                new ClementineSongDownloader.DownloadedSong(new MySong(), null));
        List<ClementineSongDownloader.DownloadedSong> read = downloader.getDownloadedSongs();

        downloader.addDownloadedSong(
                new ClementineSongDownloader.DownloadedSong(new MySong(), null));
        assertEquals(1, read.size());
        assertEquals(2, downloader.getDownloadedSongs().size());
        assertThrows(UnsupportedOperationException.class, read::clear);
    }

    @Test
    public void addingADownloadedSongWaitsForTheLock() throws InterruptedException {
        ClementineSongDownloader downloader = new ClementineSongDownloader();
        assertWaitsForLock(downloader, () -> downloader.addDownloadedSong(
                new ClementineSongDownloader.DownloadedSong(new MySong(), null)));
    }

    @Test
    public void readingTheDownloadedSongsWaitsForTheLock() throws InterruptedException {
        ClementineSongDownloader downloader = new ClementineSongDownloader();
        assertWaitsForLock(downloader, downloader::getDownloadedSongs);
    }
}
