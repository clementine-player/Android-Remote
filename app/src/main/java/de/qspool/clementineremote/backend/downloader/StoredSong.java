package de.qspool.clementineremote.backend.downloader;

import android.net.Uri;

import androidx.annotation.Nullable;

/**
 * A song found where downloads are saved, whenever it was downloaded: its content URI, the
 * folder it's in relative to the download location (such as "Artist/Album/"), its file name,
 * and its title, artist, album and track when they are known.
 */
public class StoredSong {

    public final Uri uri;

    public final String relativeDir;

    public final String fileName;

    @Nullable
    public final String title;

    @Nullable
    public final String artist;

    @Nullable
    public final String album;

    /** Its number on its album, with the disc's in the thousands; 0 when unknown. */
    public final int track;

    public StoredSong(Uri uri, String relativeDir, String fileName, @Nullable String title,
            @Nullable String artist) {
        this(uri, relativeDir, fileName, title, artist, null, 0);
    }

    public StoredSong(Uri uri, String relativeDir, String fileName, @Nullable String title,
            @Nullable String artist, @Nullable String album, int track) {
        this.uri = uri;
        this.relativeDir = relativeDir;
        this.fileName = fileName;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.track = track;
    }
}
