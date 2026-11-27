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
import java.util.UUID;

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

    public File partialDir() {
        // Keep staging partials on mBaseDir's volume so commitPartial() renames with zero copy.
        return new File(mBaseDir, ".partial");
    }

    /** Moves an already-written staging file on the same storage volume directly into place. */
    public Uri commitPartial(String relativeDir, String fileName, File partialFile)
            throws IOException {
        final File target = file(relativeDir, fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create download directory " + parent);
        }
        if (!renameReplacingTarget(parent, partialFile, target)) {
            partialFile.delete();
            throw new IOException("Cannot finalize download " + target);
        }
        MediaScannerConnection.scanFile(mContext,
                new String[]{target.getAbsolutePath()}, null, null);
        return contentUri(target);
    }

    private static boolean renameReplacingTarget(File parent, File part, File target) {
        if (part.renameTo(target)) {
            return true;
        }
        if (!target.exists()) {
            return false;
        }
        // File.renameTo() does not reliably overwrite an existing target on all filesystems;
        // back up target first so a failed swap can restore the original song.
        File backup = new File(parent, ".bak-" + UUID.randomUUID() + ".tmp");
        boolean backedUp = target.renameTo(backup);
        if (!backedUp) {
            return false;
        }
        if (part.renameTo(target)) {
            backup.delete();
            return true;
        }
        backup.renameTo(target);
        return false;
    }

    @Override
    public PendingSong create(String relativeDir, String fileName) throws IOException {
        final File target = file(relativeDir, fileName);
        final File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create download directory " + parent);
        }
        // Stage to a fixed-length temp file and swap on commit() so failed downloads keep existing
        // songs intact and long target filenames never exceed filesystem name limits with a prefix.
        final File part = new File(parent, ".dl-" + UUID.randomUUID() + ".tmp");
        final OutputStream out = new FileOutputStream(part);

        return new PendingSong() {
            @Override
            public OutputStream getOutputStream() {
                return out;
            }

            @Override
            public Uri commit() throws IOException {
                out.close();
                if (!renameReplacingTarget(parent, part, target)) {
                    part.delete();
                    throw new IOException("Cannot finalize download " + target);
                }
                MediaScannerConnection.scanFile(mContext,
                        new String[]{target.getAbsolutePath()}, null, null);
                return contentUri(target);
            }

            @Override
            public void abort() {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
                part.delete();
            }
        };
    }
}
