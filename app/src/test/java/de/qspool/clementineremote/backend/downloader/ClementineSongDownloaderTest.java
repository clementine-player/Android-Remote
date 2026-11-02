package de.qspool.clementineremote.backend.downloader;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import de.qspool.clementineremote.backend.BackgroundTask;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;

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
}
