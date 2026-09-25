package de.qspool.clementineremote.backend.downloader;

import android.content.ContentResolver;
import android.net.Uri;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSongFileChunk;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata;
import de.qspool.clementineremote.testing.StrictModeRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Saving downloaded songs as files, as on Android 9 and earlier, and the folders they go in.
 * Saving to MediaStore needs a real device: see MediaStoreDownloadStorageTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DownloadStorageTest {

    @Rule
    public final StrictModeRule mStrictMode = new StrictModeRule();

    @Rule
    public final TemporaryFolder mFolder = new TemporaryFolder();

    private FileDownloadStorage mStorage;

    @Before
    public void setUp() {
        mStorage = new FileDownloadStorage(RuntimeEnvironment.getApplication(),
                mFolder.getRoot());
    }

    private Uri save(String dir, String name, String content) throws Exception {
        DownloadStorage.PendingSong song = mStorage.create(dir, name);
        song.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
        return song.commit();
    }

    private static String read(Uri uri) throws Exception {
        ContentResolver resolver = RuntimeEnvironment.getApplication().getContentResolver();
        try (InputStream in = resolver.openInputStream(uri)) {
            byte[] buffer = new byte[64];
            int length = in.read(buffer);
            return new String(buffer, 0, length, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void savedSongIsSharedAsContentUri() throws Exception {
        assertNull(mStorage.find("Artist/Album/", "song.ogg"));

        Uri uri = save("Artist/Album/", "song.ogg", "first");

        assertEquals(ContentResolver.SCHEME_CONTENT, uri.getScheme());
        assertEquals(FileDownloadStorage.authority(RuntimeEnvironment.getApplication()),
                uri.getAuthority());
        assertEquals(uri, mStorage.find("Artist/Album/", "song.ogg"));
        assertTrue(new File(mFolder.getRoot(), "Artist/Album/song.ogg").isFile());
        assertEquals("first", read(uri));
    }

    @Test
    public void savingAgainReplacesTheSong() throws Exception {
        save("", "song.ogg", "first");
        Uri uri = save("", "song.ogg", "second");
        assertEquals("second", read(uri));
    }

    @Test
    public void abortedSongIsDeleted() throws Exception {
        DownloadStorage.PendingSong song = mStorage.create("Artist/", "song.ogg");
        song.getOutputStream().write(new byte[]{1, 2, 3});
        song.abort();

        assertNull(mStorage.find("Artist/", "song.ogg"));
        assertFalse(new File(mFolder.getRoot(), "Artist/song.ogg").exists());
    }

    private static ResponseSongFileChunk chunk(String artist, String albumArtist, String album) {
        return ResponseSongFileChunk.newBuilder()
                .setSongMetadata(SongMetadata.newBuilder()
                        .setArtist(artist)
                        .setAlbumartist(albumArtist)
                        .setAlbum(album)
                        .setFilename("01 - Song: Name?.mp3"))
                .build();
    }

    @Test
    public void foldersFollowTheSettings() {
        ClementineSongDownloader downloader = new ClementineSongDownloader();
        ResponseSongFileChunk chunk = chunk("Artist", "", "Album/Live");

        assertEquals("", downloader.buildRelativeDir(chunk));

        downloader.setCreateArtistDir(true);
        assertEquals("Artist/", downloader.buildRelativeDir(chunk));

        downloader.setCreateAlbumDir(true);
        assertEquals("Artist/AlbumLive/", downloader.buildRelativeDir(chunk));

        assertEquals("Various/AlbumLive/",
                downloader.buildRelativeDir(chunk("Artist", "Various", "Album/Live")));

        downloader.setIsPlaylist(true, "My: Playlist");
        assertEquals("Artist/AlbumLive/", downloader.buildRelativeDir(chunk));
        downloader.setCreatePlaylistDir(true);
        assertEquals("My Playlist/Artist/AlbumLive/", downloader.buildRelativeDir(chunk));

        assertEquals("01  Song Name.mp3", ClementineSongDownloader.buildFileName(chunk));
    }

    @Test
    public void emptyTagsDoNotMakeEmptyFolders() {
        ClementineSongDownloader downloader = new ClementineSongDownloader();
        downloader.setCreateArtistDir(true);
        downloader.setCreateAlbumDir(true);
        assertEquals("Album/", downloader.buildRelativeDir(chunk("", "", "Album")));
        assertEquals("", downloader.buildRelativeDir(chunk("", "", "")));
    }

    @Test
    public void unknownFileTypesAreSavedAsAudio() {
        assertEquals("audio/mpeg", MediaStoreDownloadStorage.mimeType("song"));
        assertEquals("audio/mpeg", MediaStoreDownloadStorage.mimeType("song.txt"));
    }
}
