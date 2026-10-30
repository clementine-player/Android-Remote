package de.qspool.clementineremote.backend.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;

/** The downloads, running and finished. */
@RunWith(RobolectricTestRunner.class)
public class DownloadManagerTest {

    /**
     * The downloads screen lists the downloads from another thread while they finish. Each is
     * listed once, or the list it shows them in crashes on two rows with the same key.
     */
    @Test
    public void listsEachDownloadOnceWhileTheyFinish() throws Exception {
        DownloadManager manager = DownloadManager.getInstance(RuntimeEnvironment.getApplication());
        int count = 2000;
        for (int id = 0; id < count; ++id) {
            ClementineSongDownloader downloader = new ClementineSongDownloader();
            downloader.setId(id);
            manager.track(downloader);
        }

        String[] problem = {null};
        Thread finisher = new Thread(() -> {
            for (int id = 0; id < count; ++id) {
                manager.finish(id);
            }
        });
        finisher.start();
        try {
            while (finisher.isAlive() && problem[0] == null) {
                Set<Integer> ids = new HashSet<>();
                for (ClementineSongDownloader downloader : manager.getAllDownloaders()) {
                    if (downloader == null || !ids.add(downloader.getId())) {
                        problem[0] = "Listed twice or as null: " + (downloader == null ? null : downloader.getId());
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            problem[0] = e.toString();
        }
        finisher.join();

        assertNull(problem[0]);
        assertEquals(count, manager.getAllDownloaders().size());
        for (int id = 0; id < count; ++id) {
            manager.removeDownloader(id);
        }
    }
}
