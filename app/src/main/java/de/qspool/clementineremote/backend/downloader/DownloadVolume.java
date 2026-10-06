package de.qspool.clementineremote.backend.downloader;

/** A storage volume songs can be saved to: its MediaStore name, and its name for the user. */
public class DownloadVolume {

    /** Such as {@link android.provider.MediaStore#VOLUME_EXTERNAL_PRIMARY}. */
    public final String name;

    /** Such as "Internal shared storage" or "SanDisk SD card", from the system. */
    public final String description;

    public DownloadVolume(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
