package de.qspool.clementineremote.utils;

import android.os.Build;
import android.os.StrictMode;

/**
 * The StrictMode checks the app runs under in debug builds and tests.
 *
 * <p>The thread policy flags disk and network access on the main thread. The VM policy flags
 * leaked resources and unsafe hand-offs to other apps. It deliberately leaves out checks
 * that would flag every connection this app makes: untagged sockets and cleartext traffic
 * (the Clementine remote protocol is plain TCP on the local network).
 */
public final class StrictModePolicies {

    private StrictModePolicies() {
    }

    public static StrictMode.ThreadPolicy.Builder threadPolicy() {
        return new StrictMode.ThreadPolicy.Builder().detectAll();
    }

    public static StrictMode.VmPolicy.Builder vmPolicy() {
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .detectFileUriExposure();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.detectContentUriWithoutPermission();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.detectUnsafeIntentLaunch().detectIncorrectContextUse();
        }
        return builder;
    }

    /** Debug builds: log every violation to logcat under the StrictMode tag. */
    public static void enableLogging() {
        StrictMode.setThreadPolicy(threadPolicy().penaltyLog().build());
        StrictMode.setVmPolicy(vmPolicy().penaltyLog().build());
    }
}
