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
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import de.qspool.clementineremote.ui.ConnectActivity;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

/**
 * Takes the Google Play screenshots: the app connected to a real Clementine playing the
 * showcase library (clementine-it, built with LIBRARY=showcase). Runs only when given the
 * Clementine host, as .github/workflows/store-screenshots.yml does:
 *
 *   ./gradlew connectedFdroidDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=de.qspool.clementineremote.StoreScreenshots \
 *     -Pandroid.testInstrumentationRunnerArguments.clementineHost=10.0.2.2
 *
 * The screenshots are saved on the device, in the app's external files directory under
 * screenshots/, numbered in the order the store shows them.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class StoreScreenshots {

    private static final long TIMEOUT = 30_000;

    /** Long enough for the drawer to close and a screen to fade in. */
    private static final long SETTLE_MILLIS = 1500;

    /** Downloading and indexing the library takes a while on an emulator. */
    private static final long LIBRARY_TIMEOUT = 120_000;

    @Rule
    public final GrantPermissionRule mPermissions = GrantPermissionRule.grant(
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_PHONE_STATE);

    /** On failure, keeps what the screen showed, to see why. */
    @Rule
    public final TestWatcher mOnFailure = new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
            if (mDevice == null) {
                return;
            }
            mDevice.takeScreenshot(new File(mDir, "failure.png"));
            try {
                mDevice.dumpWindowHierarchy(new File(mDir, "failure.xml"));
            } catch (IOException ignored) {
            }
        }
    };

    private Context mContext;

    private UiDevice mDevice;

    private String mPackage;

    private File mDir;

    @Before
    public void setUp() {
        String host = InstrumentationRegistry.getArguments().getString("clementineHost");
        assumeNotNull(host);

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mPackage = mContext.getPackageName();
        mDir = new File(mContext.getExternalFilesDir(null), "screenshots");
        mDir.mkdirs();
        File[] old = mDir.listFiles();
        if (old != null) {
            for (File file : old) {
                file.delete();
            }
        }

        // A clean status bar: System UI demo mode, set up here rather than in the workflow so
        // System UI is sure to be running.
        for (String command : new String[]{
                "settings put global sysui_demo_allowed 1",
                "am broadcast -a com.android.systemui.demo -e command enter",
                "am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000",
                "am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false",
                "am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true",
                "am broadcast -a com.android.systemui.demo -e command network -e mobile hide",
                "am broadcast -a com.android.systemui.demo -e command notifications -e visible false",
        }) {
            try {
                mDevice.executeShellCommand(command);
            } catch (IOException e) {
                throw new AssertionError(command, e);
            }
        }

        // Skip the first-run message and fill in Clementine's address.
        App.getPreferences().edit()
                .putBoolean(SharedPreferencesKeys.SP_FIRST_CALL, false)
                .putString(SharedPreferencesKeys.SP_KEY_IP, host)
                .putString(SharedPreferencesKeys.SP_KEY_PORT, "5500")
                .commit();
    }

    private UiObject2 waitFor(BySelector selector, long timeout) {
        UiObject2 object = mDevice.wait(Until.findObject(selector), timeout);
        assertNotNull("Not shown: " + selector, object);
        return object;
    }

    private UiObject2 waitFor(BySelector selector) {
        return waitFor(selector, TIMEOUT);
    }

    private BySelector id(String name) {
        return By.res(mPackage, name);
    }

    private void screenshot(String name) {
        // Let animations and images settle.
        mDevice.waitForIdle();
        mDevice.waitForWindowUpdate(mPackage, 1000);
        assertTrue(name, mDevice.takeScreenshot(new File(mDir, name + ".png")));
    }

    private void openDrawer() {
        // The drawer toggle is described by the "Connect" string (see MainActivity).
        waitFor(By.clazz("android.widget.ImageButton")
                .desc(mContext.getString(R.string.connectdialog_connect))).click();
        waitFor(id("drawer_list"));
        mDevice.waitForIdle();
    }

    /**
     * Picks an item in the open drawer, and waits for the drawer to close and the new screen to
     * fade in: until then, the old screen is still there to be found and tapped.
     */
    private void select(String item) {
        waitFor(By.res(mPackage, "drawer_list").hasDescendant(By.text(item)))
                .findObject(By.text(item)).click();
        SystemClock.sleep(SETTLE_MILLIS);
        mDevice.waitForIdle();
    }

    private void navigateTo(String item) {
        openDrawer();
        select(item);
    }

    /**
     * Plays a track by tapping it in the playlist, and shows the player. Clementine sometimes
     * starts another track instead, so this checks and tries again.
     */
    private void play(String title) {
        for (int attempt = 1; ; attempt++) {
            if (attempt > 1) {
                navigateTo("Playlists");
            }
            waitFor(By.text(title));
            mDevice.waitForIdle();
            mDevice.findObject(By.text(title)).click();
            navigateTo("Player");
            if (mDevice.wait(Until.hasObject(By.res(mPackage, "tvTitle").text(title)), 10_000)) {
                return;
            }
            assertTrue("Could not play " + title, attempt < 3);
        }
    }

    @Test
    public void takeScreenshots() {
        mContext.startActivity(new Intent(mContext, ConnectActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor(id("btnConnect"));
        screenshot("6_connect");

        mDevice.findObject(id("btnConnect")).click();
        waitFor(id("btnPlaypause"));

        navigateTo("Playlists");
        waitFor(By.text("Clair de lune"));
        screenshot("3_playlist");
        play("Clair de lune");
        // Let playback move along the seek bar.
        SystemClock.sleep(8000);
        screenshot("1_player");

        // Back would leave the player and disconnect, so move on from the open drawer.
        openDrawer();
        screenshot("5_navigation");
        select("Library");
        // The library is downloaded from Clementine by pulling down on the empty list.
        waitFor(id("library_refresh_empty_layout")).swipe(Direction.DOWN, 0.8f);
        waitFor(By.text("Frédéric Chopin"), LIBRARY_TIMEOUT);
        screenshot("2_library");
        mDevice.findObject(By.text("Frédéric Chopin")).click();
        waitFor(By.text("Nocturnes, Op. 9")).click();
        waitFor(By.textStartsWith("Nocturne in"));
        screenshot("2_library_album");

        navigateTo("Search");
        // The field needs focus for Enter to submit the search.
        UiObject2 query = waitFor(id("search_src_text"));
        query.click();
        query.setText("Gymnopédie");
        mDevice.waitForIdle();
        mDevice.pressEnter();
        // Results are grouped by source, then artist and album: open the first entry at each
        // level down to the tracks.
        BySelector tracks = By.textStartsWith("Gymnopédie No.");
        for (int level = 0; level < 4 && !mDevice.wait(Until.hasObject(tracks), 5000); level++) {
            waitFor(id("global_search")).getChildren().get(0).click();
            SystemClock.sleep(SETTLE_MILLIS);
        }
        waitFor(tracks);
        screenshot("4_search");
    }
}
