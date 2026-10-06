package de.qspool.clementineremote.backend.downloader;

import android.net.Uri;

/** A song saved where downloads go: its content URI, and its size in bytes. */
public class SavedSong {

    public final Uri uri;

    public final long size;

    public SavedSong(Uri uri, long size) {
        this.uri = uri;
        this.size = size;
    }
}
