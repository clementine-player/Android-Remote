package de.qspool.clementineremote.utils;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import androidx.annotation.Nullable;
import androidx.core.app.TaskStackBuilder;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification;
import de.qspool.clementineremote.ui.MainActivity;

public class Utilities {

    @SuppressLint("DefaultLocale")
    public static String PrettyTime(int seconds) {
        // last.fm sometimes gets the track length wrong, so you end up with
        // negative times.
        seconds = Math.abs(seconds);

        int hours = seconds / (60 * 60);
        int minutes = (seconds / 60) % 60;
        seconds %= 60;

        String ret = "";
        if (hours > 0) {
            ret = String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            ret = String.format("%d:%02d", minutes, seconds);
        }

        return ret;
    }

    public static byte[] ToIPByteArray(int addr) {
        return new byte[]{(byte) addr, (byte) (addr >>> 8), (byte) (addr >>> 16),
                (byte) (addr >>> 24)};
    }

    public static InetAddress ToInetAddress(int addr) {
        try {
            return InetAddress.getByAddress(ToIPByteArray(addr));
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Get the free space on the external storage device (like sd card)
     *
     * @return The free space in byte
     */
    public static double getFreeSpaceExternal() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        return (double) stat.getAvailableBlocksLong() * (double) stat.getBlockSizeLong();
    }

    /**
     * Get the free space on the internal storage device
     *
     * @return The free space in byte
     */
    public static double getFreeSpaceInternal() {
        StatFs stat = new StatFs(App.getApp().getFilesDir().getPath());
        return (double) stat.getAvailableBlocksLong() * (double) stat.getBlockSizeLong();
    }

    /**
     * Is the device connceted to a wifi network?
     *
     * @return true if connected to a wifi network
     */
    @SuppressWarnings("deprecation")
    public static boolean onWifi() {
        ConnectivityManager connManager = (ConnectivityManager) App.getApp()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean onWifi = false;

        Network[] networks = connManager.getAllNetworks();
        NetworkInfo networkInfo;
        for (Network mNetwork : networks) {
            networkInfo = connManager.getNetworkInfo(mNetwork);
            if (networkInfo.getState().equals(NetworkInfo.State.CONNECTED) &&
                    networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                onWifi = true;
                break;
            }
        }

        return onWifi;
    }

    /**
     * Convert bytes in a human readable format.
     *
     * @param bytes The byte count
     * @param iec   false for KB, false for KiB
     * @return The human readable file size
     */
    public static String humanReadableBytes(long bytes, boolean iec) {
        // Are we using xB or xiB?
        int byteUnit = iec ? 1024 : 1000;
        float newBytes = bytes;
        int exp = 0;

        // Calculate the file size in the best readable way
        while (newBytes > byteUnit) {
            newBytes = newBytes / byteUnit;
            exp++;
        }

        // What prefix do we have to use?
        String prefix = "";
        if (exp > 0) {
            prefix = (iec ? " KMGTPE" : " kMGTPE").charAt(exp) + ((iec) ? "i" : "");
        }

        // Return a human readable String
        return String.format(Locale.US, "%.2f %sB", newBytes, prefix);
    }

    /**
     * Checks if Clementine Remote is connected to an instance
     * of Clementine.
     *
     * @return true if connected, false otherwise
     */
    public static boolean isRemoteConnected() {
        return App.ClementineConnection != null && App.ClementineConnection.isConnected();
    }

    /**
     * This method removes all illegal characters in a file name.
     *
     * @param str The string containing a file or folder name
     * @return A string that is a vaild file name
     */
    public static String removeInvalidFileCharacters(String str) {
        String illegal = "[\\\\~#%&*{}/:<>?|\\\"-]";
        return str.replaceAll(illegal, "");
    }

    public static PendingIntent getClementineRemotePendingIntent(Context context) {
        // Set the result intent
        Intent resultIntent = new Intent(context, MainActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resultIntent.putExtra(ClementineMediaSessionNotification.EXTRA_NOTIFICATION_ID, -1);

        // Create a TaskStack, so the app navigates correctly backwards
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        return stackBuilder.getPendingIntent(9912, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * The device's IPv4 address on its local network (Wi-Fi or Ethernet), where Clementine
     * announces itself. Not a VPN's or mobile data's: multicast DNS does not cross those, and
     * the first address of any interface can well be a VPN's, such as Tailscale's.
     */
    @Nullable
    public static Inet4Address getLocalNetworkIp4Address(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null) {
            return null;
        }
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            LinkProperties link = connectivity.getLinkProperties(network);
            if (capabilities == null || link == null || !isLocalNetwork(capabilities)) {
                continue;
            }
            for (LinkAddress linkAddress : link.getLinkAddresses()) {
                InetAddress address = linkAddress.getAddress();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return (Inet4Address) address;
                }
            }
        }
        return null;
    }

    /** A VPN can also report the Wi-Fi it runs over as a transport, so it is ruled out first. */
    static boolean isLocalNetwork(NetworkCapabilities capabilities) {
        return !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}
