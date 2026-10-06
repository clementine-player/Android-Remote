package de.qspool.clementineremote.backend.downloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.qspool.clementineremote.SharedPreferencesKeys;

/**
 * The storage volumes songs can be saved to on Android 10 and later: the phone's own shared
 * storage, and SD cards and USB drives that MediaStore indexes. The primary volume is the
 * default; the user can pick another in the settings.
 */
@RequiresApi(Build.VERSION_CODES.Q)
public final class DownloadVolumes {

    /** Returned by {@link #freeSpace} when the volume isn't found. */
    public static final long UNKNOWN_SPACE = -1;

    private DownloadVolumes() {
    }

    /** The volume picked in the settings, whether or not it is there now. */
    public static String chosen(SharedPreferences preferences) {
        return preferences.getString(SharedPreferencesKeys.SP_DOWNLOAD_VOLUME,
                MediaStore.VOLUME_EXTERNAL_PRIMARY);
    }

    /** Whether MediaStore can save songs to the volume now: an SD card may have been taken out. */
    public static boolean isAvailable(Context context, String name) {
        return MediaStore.getExternalVolumeNames(context).contains(name);
    }

    /** The volumes songs can be saved to now, the primary one first. */
    @WorkerThread
    public static List<DownloadVolume> available(Context context) {
        Set<String> names = MediaStore.getExternalVolumeNames(context);
        StorageManager storage = context.getSystemService(StorageManager.class);
        List<DownloadVolume> volumes = new ArrayList<>();
        for (StorageVolume volume : storage.getStorageVolumes()) {
            String name = mediaStoreName(volume);
            if (name != null && names.contains(name)) {
                DownloadVolume found = new DownloadVolume(name, volume.getDescription(context));
                if (volume.isPrimary()) {
                    volumes.add(0, found);
                } else {
                    volumes.add(found);
                }
            }
        }
        return volumes;
    }

    /**
     * The free space on the volume, in bytes, or {@link #UNKNOWN_SPACE}. It is read from the
     * app's own folder there, which needs no permission and is on the same file system.
     */
    @WorkerThread
    public static long freeSpace(Context context, String name) {
        StorageManager storage = context.getSystemService(StorageManager.class);
        for (File dir : context.getExternalFilesDirs(null)) {
            if (dir == null) {
                continue;
            }
            StorageVolume volume = storage.getStorageVolume(dir);
            if (volume != null && name.equals(mediaStoreName(volume))) {
                return dir.getUsableSpace();
            }
        }
        return UNKNOWN_SPACE;
    }

    /**
     * The name MediaStore knows the volume by. Android 10 has no method for it, but names the
     * primary volume "external_primary" and the others by their file system's UUID, in lower
     * case.
     */
    @Nullable
    static String mediaStoreName(StorageVolume volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return volume.getMediaStoreVolumeName();
        }
        if (volume.isPrimary()) {
            return MediaStore.VOLUME_EXTERNAL_PRIMARY;
        }
        String uuid = volume.getUuid();
        return uuid == null ? null : uuid.toLowerCase(Locale.ROOT);
    }
}
