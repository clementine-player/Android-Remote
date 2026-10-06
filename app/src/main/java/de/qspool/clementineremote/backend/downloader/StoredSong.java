package de.qspool.clementineremote.backend.downloader;

import android.net.Uri;

import androidx.annotation.Nullable;

/**
 * A song found where downloads are saved, whenever it was downloaded: its content URI, the
 * folder it's in relative to the download location (such as "Artist/Album/"), its file name,
 * and its title and artist when they are known.
 */
public class StoredSong {

    public final Uri uri;

    public final String relativeDir;

    public final String fileName;

    @Nullable
    public final String title;

    @Nullable
    public final String artist;

    public StoredSong(Uri uri, String relativeDir, String fileName, @Nullable String title,
            @Nullable String artist) {
        this.uri = uri;
        this.relativeDir = relativeDir;
        this.fileName = fileName;
        this.title = title;
        this.artist = artist;
    }
}
