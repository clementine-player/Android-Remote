package de.qspool.clementineremote.backend.player;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A playlist's songs are replaced whole, and readers get a copy. Clementine's messages replace them
 * on the connection's thread while the screens read them on the main thread, so both hold the
 * playlist's lock.
 */
public class MyPlaylistTest {

    private static List<MySong> songs(int count) {
        List<MySong> songs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MySong song = new MySong();
            song.setIndex(i);
            songs.add(song);
        }
        return songs;
    }

    @Test
    public void setSongsReplacesThem() {
        MyPlaylist playlist = new MyPlaylist();
        assertFalse(playlist.hasSongs());

        List<MySong> first = songs(3);
        playlist.setSongs(first);
        assertEquals(first, playlist.getPlaylistSongs());

        List<MySong> second = songs(2);
        playlist.setSongs(second);
        assertEquals(second, playlist.getPlaylistSongs());
        assertTrue(playlist.hasSongs());
    }

    @Test
    public void changingTheCopyLeavesThePlaylist() {
        MyPlaylist playlist = new MyPlaylist();
        playlist.setSongs(songs(2));
        playlist.getPlaylistSongs().clear();
        assertEquals(2, playlist.getPlaylistSongs().size());
    }

    @Test
    public void setSongsWaitsForTheLock() throws InterruptedException {
        MyPlaylist playlist = new MyPlaylist();
        assertWaitsForLock(playlist, () -> playlist.setSongs(songs(2)));
    }

    @Test
    public void getPlaylistSongsWaitsForTheLock() throws InterruptedException {
        MyPlaylist playlist = new MyPlaylist();
        assertWaitsForLock(playlist, playlist::getPlaylistSongs);
    }

    @Test
    public void hasSongsWaitsForTheLock() throws InterruptedException {
        MyPlaylist playlist = new MyPlaylist();
        assertWaitsForLock(playlist, playlist::hasSongs);
    }

    /** Runs call on another thread while holding playlist's lock, and checks it waits for it. */
    private static void assertWaitsForLock(MyPlaylist playlist, Runnable call)
            throws InterruptedException {
        Thread thread = new Thread(call);
        synchronized (playlist) {
            thread.start();
            // The thread either blocks on the lock or, if the call doesn't take it, finishes.
            Thread.State state;
            while ((state = thread.getState()) != Thread.State.BLOCKED
                    && state != Thread.State.TERMINATED) {
                Thread.sleep(1);
            }
            if (state == Thread.State.TERMINATED) {
                fail("The call didn't wait for the playlist's lock");
            }
        }
        thread.join();
    }
}
