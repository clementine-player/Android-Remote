package de.qspool.clementineremote.backend.downloader;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Saves songs as files under a directory the user picked, for Android 9 and earlier, where
 * the app holds the storage permission. Songs are shared through a FileProvider, as file://
 * URIs cannot be passed to other apps.
 */
public class FileDownloadStorage implements DownloadStorage {

    private final Context mContext;

    private final File mBaseDir;

    public FileDownloadStorage(Context context, File baseDir) {
        mContext = context.getApplicationContext();
        mBaseDir = baseDir;
    }

    public static String authority(Context context) {
        return context.getPackageName() + ".downloads";
    }

    private File file(String relativeDir, String fileName) {
        return new File(new File(mBaseDir, relativeDir), fileName);
    }

    private Uri contentUri(File file) {
        return FileProvider.getUriForFile(mContext, authority(mContext), file);
    }

    @Nullable
    @Override
    public Uri find(String relativeDir, String fileName) {
        File file = file(relativeDir, fileName);
        return file.exists() ? contentUri(file) : null;
    }

    @Override
    public PendingSong create(String relativeDir, String fileName) throws IOException {
        final File file = file(relativeDir, fileName);
        if (file.exists()) {
            file.delete();
        }
        file.getParentFile().mkdirs();
        final OutputStream out = new FileOutputStream(file);

        return new PendingSong() {
            @Override
            public OutputStream getOutputStream() {
                return out;
            }

            @Override
            public Uri commit() throws IOException {
                out.close();
                MediaScannerConnection.scanFile(mContext,
                        new String[]{file.getAbsolutePath()}, null, null);
                return contentUri(file);
            }

            @Override
            public void abort() {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
                file.delete();
            }
        };
    }
}
