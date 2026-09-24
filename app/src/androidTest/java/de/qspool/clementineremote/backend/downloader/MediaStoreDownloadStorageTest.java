package de.qspool.clementineremote.backend.downloader;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Saves songs to the shared Music collection, as on Android 10 and later. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class MediaStoreDownloadStorageTest {

    private final String mDir = "test-" + System.nanoTime() + "/Album/";

    private Context mContext;

    private MediaStoreDownloadStorage mStorage;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mStorage = new MediaStoreDownloadStorage(mContext);
    }

    @After
    public void deleteSongs() {
        mContext.getContentResolver().delete(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Audio.Media.RELATIVE_PATH + "=?",
                new String[]{MediaStoreDownloadStorage.BASE_DIR + mDir});
    }

    private Uri save(String name, String content) throws Exception {
        DownloadStorage.PendingSong song = mStorage.create(mDir, name);
        song.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
        return song.commit();
    }

    private String read(Uri uri) throws Exception {
        try (InputStream in = mContext.getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            return out.toString("UTF-8");
        }
    }

    private String column(Uri uri, String column) {
        try (Cursor cursor = mContext.getContentResolver().query(uri, new String[]{column},
                null, null, null)) {
            assertNotNull(cursor);
            cursor.moveToFirst();
            return cursor.getString(0);
        }
    }

    @Test
    public void savedSongIsInTheMusicCollection() throws Exception {
        assertNull(mStorage.find(mDir, "01 Song.ogg"));

        Uri uri = save("01 Song.ogg", "first");

        assertEquals(ContentResolver.SCHEME_CONTENT, uri.getScheme());
        assertEquals(uri, mStorage.find(mDir, "01 Song.ogg"));
        assertEquals("first", read(uri));
        assertEquals("Music/Clementine/" + mDir,
                column(uri, MediaStore.Audio.Media.RELATIVE_PATH));
        assertEquals("01 Song.ogg", column(uri, MediaStore.Audio.Media.DISPLAY_NAME));
        assertEquals("0", column(uri, MediaStore.Audio.Media.IS_PENDING));
    }

    @Test
    public void savingAgainReplacesTheSong() throws Exception {
        Uri first = save("song.mp3", "first");
        Uri second = save("song.mp3", "second");

        assertNotEquals(first, second);
        assertEquals(second, mStorage.find(mDir, "song.mp3"));
        assertEquals("second", read(second));
        assertEquals("song.mp3", column(second, MediaStore.Audio.Media.DISPLAY_NAME));
    }

    @Test
    public void abortedSongIsDeleted() throws Exception {
        DownloadStorage.PendingSong song = mStorage.create(mDir, "song.flac");
        song.getOutputStream().write(new byte[]{1, 2, 3});
        song.abort();

        assertNull(mStorage.find(mDir, "song.flac"));
    }

    @Test
    public void mimeTypesComeFromTheExtension() {
        assertEquals("audio/mpeg", MediaStoreDownloadStorage.mimeType("song.MP3"));
        assertEquals("audio/flac", MediaStoreDownloadStorage.mimeType("song.flac"));
        assertEquals("audio/mpeg", MediaStoreDownloadStorage.mimeType("song.txt"));
    }
}
