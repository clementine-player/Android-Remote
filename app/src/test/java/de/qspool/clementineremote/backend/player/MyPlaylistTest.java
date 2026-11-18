package de.qspool.clementineremote.backend.player;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static de.qspool.clementineremote.testing.LockAssertions.assertWaitsForLock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * A playlist's songs are replaced whole, and readers get an unmodifiable copy. Clementine's
 * messages replace them on the connection's thread while the screens read them on the main
 * thread, so both hold the playlist's lock.
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
    public void theSongsReadCantBeChanged() {
        MyPlaylist playlist = new MyPlaylist();
        playlist.setSongs(songs(2));
        List<MySong> read = playlist.getPlaylistSongs();
        assertThrows(UnsupportedOperationException.class, read::clear);
    }

    @Test
    public void theSongsReadDontChangeWithThePlaylist() {
        MyPlaylist playlist = new MyPlaylist();
        playlist.setSongs(songs(2));
        List<MySong> read = playlist.getPlaylistSongs();
        playlist.setSongs(songs(3));
        assertEquals(2, read.size());
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
}
