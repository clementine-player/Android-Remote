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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Saves songs as files under a directory the user picked, for Android 9 and earlier, where
 * the app holds the storage permission. Songs are shared through a FileProvider, as file://
 * URIs cannot be passed to other apps.
 */
public class FileDownloadStorage implements DownloadStorage {

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "aac", "aif", "aiff", "ape", "flac", "m4a", "mp3", "mp4", "mpc", "oga", "ogg",
            "opus", "spc", "spx", "tta", "vgm", "wav", "wma", "wv"));

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
    public SavedSong find(String relativeDir, String fileName) {
        File file = file(relativeDir, fileName);
        return file.exists() ? new SavedSong(contentUri(file), file.length()) : null;
    }

    /**
     * The audio files in the folder and the folders in it. Their titles aren't read, as that
     * means opening every file; the folder may be one the user picked with other music in it.
     */
    @Override
    public List<StoredSong> list() {
        List<StoredSong> songs = new ArrayList<>();
        addSongs(mBaseDir, "", songs);
        return songs;
    }

    private void addSongs(File dir, String relativeDir, List<StoredSong> songs) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().startsWith(".")) {
                continue;
            }
            if (file.isDirectory()) {
                addSongs(file, relativeDir + file.getName() + "/", songs);
            } else if (isAudio(file.getName())) {
                songs.add(new StoredSong(contentUri(file), relativeDir, file.getName(), null, null));
            }
        }
    }

    /** Whether the file is of a type Clementine plays, and so can send, by its extension. */
    static boolean isAudio(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && AUDIO_EXTENSIONS.contains(
                fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    @Override
    public long freeSpace() {
        // The folder is only made with the first song saved.
        File dir = mBaseDir;
        while (dir != null && !dir.exists()) {
            dir = dir.getParentFile();
        }
        return dir == null ? -1 : dir.getUsableSpace();
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
