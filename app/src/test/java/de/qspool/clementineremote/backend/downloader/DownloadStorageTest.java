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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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
        SavedSong saved = mStorage.find("Artist/Album/", "song.ogg");
        assertEquals(uri, saved.uri);
        assertEquals("first".length(), saved.size);
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

    @Test
    public void songsAlreadySavedAreOnlyDownloadedAgainWhenOverriddenAndDifferent() {
        SavedSong saved = new SavedSong(Uri.parse("content://downloads/song.ogg"), 1000);

        // Not saved yet.
        assertTrue(ClementineSongDownloader.shouldDownload(null, 1000, false));
        assertTrue(ClementineSongDownloader.shouldDownload(null, 1000, true));
        // Saved, and existing files are kept.
        assertFalse(ClementineSongDownloader.shouldDownload(saved, 1000, false));
        assertFalse(ClementineSongDownloader.shouldDownload(saved, 2000, false));
        // Overridden only when the file offered differs.
        assertFalse(ClementineSongDownloader.shouldDownload(saved, 1000, true));
        assertTrue(ClementineSongDownloader.shouldDownload(saved, 2000, true));
    }

    @Test
    public void freeSpaceIsReadWhereSongsGoEvenBeforeTheFolderIsMade() {
        assertTrue(mStorage.freeSpace() > 0);
        File notMadeYet = new File(mFolder.getRoot(), "Music/Clementine");
        FileDownloadStorage storage = new FileDownloadStorage(
                RuntimeEnvironment.getApplication(), notMadeYet);
        assertEquals(mFolder.getRoot().getUsableSpace(), storage.freeSpace(), 64L << 20);
        assertFalse(notMadeYet.exists());
    }

    @Test
    public void listsTheSongsSavedInEveryFolder() throws Exception {
        Uri top = save("", "song.mp3", "top");
        Uri inAlbum = save("Artist/Album/", "01 Song.FLAC", "album");
        // Not songs: other files, hidden ones, and those whose download was cut short.
        save("Artist/", "cover.jpg", "image");
        save("Artist/", ".hidden.mp3", "hidden");
        mStorage.create("Artist/", "partial.ogg").abort();

        Map<String, StoredSong> songs = new HashMap<>();
        for (StoredSong song : mStorage.list()) {
            songs.put(song.relativeDir + song.fileName, song);
        }

        assertEquals(new HashSet<>(Arrays.asList("song.mp3", "Artist/Album/01 Song.FLAC")),
                songs.keySet());
        assertEquals(top, songs.get("song.mp3").uri);
        StoredSong song = songs.get("Artist/Album/01 Song.FLAC");
        assertEquals(inAlbum, song.uri);
        assertEquals("Artist/Album/", song.relativeDir);
        assertEquals("01 Song.FLAC", song.fileName);
        assertNull(song.title);
        assertEquals("album", read(song.uri));
    }

    @Test
    public void nothingIsListedBeforeTheFolderIsMade() {
        FileDownloadStorage storage = new FileDownloadStorage(RuntimeEnvironment.getApplication(),
                new File(mFolder.getRoot(), "not made yet"));
        assertTrue(storage.list().isEmpty());
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
