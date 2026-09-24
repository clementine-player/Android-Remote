package de.qspool.clementineremote.backend.downloader;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Saves songs to the shared Music collection through MediaStore, under Music/Clementine/.
 * This needs no storage permission on Android 10 and later, and the songs show up in music
 * players straight away.
 *
 * <p>Only the songs this app saved are visible to it, so a song saved before the app was
 * reinstalled is not found, and saving it again stores a second copy that MediaStore renames.
 */
@RequiresApi(Build.VERSION_CODES.Q)
public class MediaStoreDownloadStorage implements DownloadStorage {

    public static final String BASE_DIR = Environment.DIRECTORY_MUSIC + "/Clementine/";

    private static final String DEFAULT_MIME_TYPE = "audio/mpeg";

    private final ContentResolver mResolver;

    private final Uri mCollection;

    public MediaStoreDownloadStorage(Context context) {
        mResolver = context.getContentResolver();
        mCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    }

    @Nullable
    @Override
    public Uri find(String relativeDir, String fileName) {
        String[] projection = {MediaStore.Audio.Media._ID};
        String selection = MediaStore.Audio.Media.RELATIVE_PATH + "=? AND "
                + MediaStore.Audio.Media.DISPLAY_NAME + "=?";
        String[] args = {BASE_DIR + relativeDir, fileName};
        try (Cursor cursor = mResolver.query(mCollection, projection, selection, args, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            return ContentUris.withAppendedId(mCollection, cursor.getLong(0));
        }
    }

    @Override
    public PendingSong create(String relativeDir, String fileName) throws IOException {
        Uri existing = find(relativeDir, fileName);
        if (existing != null) {
            mResolver.delete(existing, null, null);
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, BASE_DIR + relativeDir);
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType(fileName));
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        final Uri uri = mResolver.insert(mCollection, values);
        if (uri == null) {
            throw new IOException("MediaStore refused " + BASE_DIR + relativeDir + fileName);
        }

        final OutputStream out;
        try {
            out = mResolver.openOutputStream(uri, "w");
            if (out == null) {
                throw new IOException("Cannot open " + uri);
            }
        } catch (IOException | RuntimeException e) {
            mResolver.delete(uri, null, null);
            throw e;
        }

        return new PendingSong() {
            @Override
            public OutputStream getOutputStream() {
                return out;
            }

            @Override
            public Uri commit() throws IOException {
                out.close();
                ContentValues published = new ContentValues();
                published.put(MediaStore.Audio.Media.IS_PENDING, 0);
                mResolver.update(uri, published, null, null);
                return uri;
            }

            @Override
            public void abort() {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
                try {
                    mResolver.delete(uri, null, null);
                } catch (RuntimeException ignored) {
                    // MediaStore removes pending songs it was left with after about a week.
                }
            }
        };
    }

    /** MediaStore only takes audio types into the audio collection. */
    static String mimeType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT_MIME_TYPE;
        }
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
        return type != null && type.startsWith("audio/") ? type : DEFAULT_MIME_TYPE;
    }
}
