package de.qspool.clementineremote.backend.downloader;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Where downloaded songs are saved. Songs are addressed by a directory relative to the
 * download location (such as "Artist/Album/", or "" for the location itself) and a file name.
 * The URIs returned are content URIs that can be handed to a music player.
 */
@WorkerThread
public interface DownloadStorage {

    /** Returns the song saved under this name, or null if there is none. */
    @Nullable
    Uri find(String relativeDir, String fileName) throws IOException;

    /** Starts saving a song, replacing any saved under the same name. */
    PendingSong create(String relativeDir, String fileName) throws IOException;

    /** A song being written. It is visible to other apps only once committed. */
    interface PendingSong {

        OutputStream getOutputStream();

        /** Finishes the song and returns its URI. */
        Uri commit() throws IOException;

        /** Discards the partly written song. */
        void abort();
    }
}
