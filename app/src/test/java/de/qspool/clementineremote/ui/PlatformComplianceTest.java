package de.qspool.clementineremote.ui;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.view.View;
import android.view.WindowInsets;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementineService;
import de.qspool.clementineremote.ui.settings.ClementineSettings;
import de.qspool.clementineremote.utils.Utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Behaviour required by the Android versions the app targets: edge-to-edge layout, runtime
 * permissions, the foreground service and immutable PendingIntents.
 */
@RunWith(RobolectricTestRunner.class)
public class PlatformComplianceTest {

    @Before
    public void setUp() {
        App.Clementine = new Clementine();
        App.ClementineConnection = null;
    }

    @Test
    public void toolbarGrowsUnderTheStatusBarAndContentClearsTheNavigationBar() {
        ClementineSettings activity = Robolectric.buildActivity(ClementineSettings.class)
                .setup().get();
        View toolbar = activity.findViewById(R.id.toolbar);
        View content = activity.findViewById(android.R.id.content);
        int toolbarPadding = toolbar.getPaddingTop();
        int toolbarMinHeight = toolbar.getMinimumHeight();

        WindowInsets insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 60, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 40))
                .build()
                .toWindowInsets();
        content.dispatchApplyWindowInsets(insets);

        assertEquals(toolbarPadding + 60, toolbar.getPaddingTop());
        assertEquals(toolbarMinHeight + 60, toolbar.getMinimumHeight());
        assertEquals(0, content.getPaddingTop());
        assertEquals(40, content.getPaddingBottom());
    }

    private static List<String> missingPermissions() {
        ConnectActivity activity = Robolectric.buildActivity(ConnectActivity.class).create().get();
        return Arrays.asList(activity.missingPermissions());
    }

    @Test
    public void asksForNotificationsButNotStorageOnCurrentAndroid() {
        List<String> missing = missingPermissions();
        assertTrue(missing.contains(Manifest.permission.POST_NOTIFICATIONS));
        assertTrue(missing.contains(Manifest.permission.READ_PHONE_STATE));
        assertFalse(missing.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE));
    }

    @Test
    @Config(sdk = 28)
    public void asksForStorageButNotNotificationsOnAndroid9() {
        List<String> missing = missingPermissions();
        assertTrue(missing.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        assertFalse(missing.contains(Manifest.permission.POST_NOTIFICATIONS));
    }

    @Test
    public void connectRequestPutsTheServiceInTheForeground() {
        Intent connect = new Intent().putExtra(ClementineService.EXTRA_STRING_IP, "192.0.2.1");
        ServiceController<ClementineService> controller =
                Robolectric.buildService(ClementineService.class, connect).create()
                        .startCommand(0, 1);

        assertNotNull(shadowOf(controller.get()).getLastForegroundNotification());
        assertFalse(shadowOf(controller.get()).isForegroundStopped());

        controller.destroy();
        assertTrue(shadowOf(controller.get()).isForegroundStopped());
    }

    @Test
    public void pendingIntentsAreImmutable() {
        PendingIntent intent = Utilities.getClementineRemotePendingIntent(
                org.robolectric.RuntimeEnvironment.getApplication());
        assertTrue(shadowOf(intent).isImmutable());
    }
}
