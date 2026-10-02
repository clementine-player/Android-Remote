package de.qspool.clementineremote;

import android.Manifest;
import android.os.Build;

import androidx.test.rule.GrantPermissionRule;

import java.util.ArrayList;
import java.util.List;

/** The runtime permissions the app asks for, granted for device tests, which Android's prompts would stop. */
final class AppPermissions {

    private AppPermissions() {
    }

    /** All of them. */
    static GrantPermissionRule granted() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        addLocalNetwork(permissions);
        return GrantPermissionRule.grant(permissions.toArray(new String[0]));
    }

    /** Only Android 17's local network permission, which the app can't reach Clementine without. */
    static GrantPermissionRule localNetwork() {
        List<String> permissions = new ArrayList<>();
        addLocalNetwork(permissions);
        return GrantPermissionRule.grant(permissions.toArray(new String[0]));
    }

    /** Before Android 17, there's no such permission to grant: the INTERNET permission covers it. */
    private static void addLocalNetwork(List<String> permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            permissions.add(Manifest.permission.ACCESS_LOCAL_NETWORK);
        }
    }
}
