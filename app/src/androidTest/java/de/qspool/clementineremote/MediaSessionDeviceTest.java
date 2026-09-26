package de.qspool.clementineremote;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.ConnectActivity;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

/**
 * The media session against a real Clementine: once connected, Android knows the session and
 * its song, and the system's media controls (in the notification shade) drive Clementine.
 * Runs only when given the Clementine host, as .github/workflows/store-screenshots.yml does.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class MediaSessionDeviceTest {

    private static final long TIMEOUT = 30_000;

    private static final String SYSTEM_UI = "com.android.systemui";

    @Rule
    public final GrantPermissionRule mPermissions = GrantPermissionRule.grant(
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_PHONE_STATE);

    /** On failure, keeps what the screen showed and the media sessions Android knew. */
    @Rule
    public final TestWatcher mOnFailure = new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
            if (mDevice == null) {
                return;
            }
            String name = "media-" + description.getMethodName();
            mDevice.takeScreenshot(new File(mDir, name + ".png"));
            try {
                mDevice.dumpWindowHierarchy(new File(mDir, name + ".xml"));
                Files.write(new File(mDir, name + "-sessions.txt").toPath(),
                        mediaSessions().getBytes());
            } catch (IOException ignored) {
            }
        }
    };

    private Context mContext;

    private UiDevice mDevice;

    private File mDir;

    @Before
    public void setUp() {
        String host = InstrumentationRegistry.getArguments().getString("clementineHost");
        assumeNotNull(host);

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDir = new File(mContext.getExternalFilesDir(null), "screenshots");
        mDir.mkdirs();

        App.getPreferences().edit()
                .putBoolean(SharedPreferencesKeys.SP_FIRST_CALL, false)
                .putString(SharedPreferencesKeys.SP_KEY_IP, host)
                .putString(SharedPreferencesKeys.SP_KEY_PORT, "5500")
                .commit();

        mContext.startActivity(new Intent(mContext, ConnectActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        // Already connected when another test connected first: the player shows straight away.
        UiObject2 connect = mDevice.wait(
                Until.findObject(By.res("btnConnect")), 10_000);
        if (connect != null) {
            connect.click();
        }
        assertNotNull("Not connected", mDevice.wait(
                Until.findObject(By.res("btnPlaypause")), TIMEOUT));
    }

    @After
    public void tearDown() {
        if (mDevice != null) {
            mDevice.pressHome();
        }
    }

    private String mediaSessions() {
        try {
            return mDevice.executeShellCommand("dumpsys media_session");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private MySong waitForSong() {
        long end = SystemClock.uptimeMillis() + TIMEOUT;
        while (App.Clementine.getCurrentSong() == null && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(200);
        }
        MySong song = App.Clementine.getCurrentSong();
        assertNotNull("Clementine sent no song", song);
        return song;
    }

    private boolean waitForState(Clementine.State state) {
        long end = SystemClock.uptimeMillis() + TIMEOUT;
        while (App.Clementine.getState() != state && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(200);
        }
        return App.Clementine.getState() == state;
    }

    @Test
    public void androidKnowsTheSessionAndSong() {
        MySong song = waitForSong();

        long end = SystemClock.uptimeMillis() + TIMEOUT;
        String sessions = mediaSessions();
        while (!sessions.contains(song.getTitle()) && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(500);
            sessions = mediaSessions();
        }
        assertTrue("No session for " + mContext.getPackageName(),
                sessions.contains("package=" + mContext.getPackageName()));
        assertTrue("The session doesn't show " + song.getTitle(),
                sessions.contains(song.getTitle()));
    }

    @Test
    public void systemMediaControlsDriveClementine() {
        MySong song = waitForSong();
        // Start from playing, so the controls offer Pause.
        if (App.Clementine.getState() != Clementine.State.PLAY) {
            mDevice.findObject(By.res("btnPlaypause")).click();
            assertTrue("Clementine didn't start playing", waitForState(Clementine.State.PLAY));
        }

        mDevice.openNotification();
        assertNotNull("No media controls for " + song.getTitle(),
                mDevice.wait(Until.findObject(By.text(song.getTitle())), TIMEOUT));

        // System UI's buttons: the app's own player has buttons with the same descriptions.
        mDevice.wait(Until.findObject(By.pkg(SYSTEM_UI).desc("Pause")), TIMEOUT).click();
        assertTrue("Clementine didn't pause", waitForState(Clementine.State.PAUSE));

        mDevice.wait(Until.findObject(By.pkg(SYSTEM_UI).desc("Play")), TIMEOUT).click();
        assertTrue("Clementine didn't resume", waitForState(Clementine.State.PLAY));
    }
}
