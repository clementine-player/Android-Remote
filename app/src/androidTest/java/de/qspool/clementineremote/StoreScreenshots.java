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

    /** Long enough for a screen or sheet to animate in. */
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

    /** A Compose element, by its test tag: its resource name, without the package. */
    private static BySelector tag(String name) {
        return By.res(name);
    }

    private void screenshot(String name) {
        // Let animations and images settle.
        mDevice.waitForIdle();
        mDevice.waitForWindowUpdate(mPackage, 1000);
        assertTrue(name, mDevice.takeScreenshot(new File(mDir, name + ".png")));
    }

    /** Shows a screen with the navigation bar, and waits for it to settle. */
    private void navigateTo(String destination) {
        waitFor(tag(destination)).click();
        SystemClock.sleep(SETTLE_MILLIS);
        mDevice.waitForIdle();
    }

    /** Opens the player full screen from the mini player. */
    private void openPlayer() {
        waitFor(tag("miniPlayer")).click();
        waitFor(tag("btnCollapse"));
        SystemClock.sleep(SETTLE_MILLIS);
    }

    /** Closes the player, back to the screen below. */
    private void closePlayer() {
        waitFor(tag("btnCollapse")).click();
        SystemClock.sleep(SETTLE_MILLIS);
        mDevice.waitForIdle();
    }

    /**
     * Plays a track by tapping it in the queue, and opens the player. Clementine sometimes
     * starts another track instead, so this checks and tries again.
     */
    private void play(String title) {
        for (int attempt = 1; ; attempt++) {
            if (attempt > 1) {
                closePlayer();
            }
            // In the queue, not the mini player, which may show the same title.
            BySelector song = By.text(title).hasAncestor(tag("queueSongs"));
            waitFor(song);
            mDevice.waitForIdle();
            mDevice.findObject(song).click();
            openPlayer();
            if (mDevice.wait(Until.hasObject(tag("tvTitle").text(title)), 10_000)) {
                return;
            }
            assertTrue("Could not play " + title, attempt < 3);
        }
    }

    /**
     * Searches Clementine. Enter only submits once the keyboard is attached to the field, which
     * can take a moment, so press it until the search starts.
     */
    private void search(String text) {
        UiObject2 query = waitFor(tag("searchField"));
        query.click();
        query.setText(text);
        for (int attempt = 1; ; attempt++) {
            mDevice.waitForIdle();
            mDevice.pressEnter();
            if (mDevice.wait(Until.hasObject(tag("searchProgress")), 5000)
                    || mDevice.hasObject(tag("global_search"))) {
                return;
            }
            assertTrue("Could not submit the search", attempt < 3);
            waitFor(tag("searchField")).click();
        }
    }

    @Test
    public void takeScreenshots() {
        mContext.startActivity(new Intent(mContext, ConnectActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor(tag("btnConnect"));
        screenshot("6_connect");

        mDevice.findObject(tag("btnConnect")).click();
        // Connected: the queue shows.
        waitFor(tag("navQueue"));

        waitFor(By.text("Clair de lune").hasAncestor(tag("queueSongs")));
        screenshot("3_playlist");
        play("Clair de lune");
        // Let playback move along the seek bar.
        SystemClock.sleep(8000);
        screenshot("1_player");
        closePlayer();

        // The connection sheet, from the chip at the top.
        waitFor(tag("connectionChip")).click();
        waitFor(tag("btnSwitch"));
        SystemClock.sleep(SETTLE_MILLIS);
        screenshot("5_connection");
        mDevice.pressBack();
        SystemClock.sleep(SETTLE_MILLIS);

        navigateTo("navLibrary");
        // The library is downloaded from Clementine on request.
        waitFor(tag("btnDownloadLibrary")).click();
        waitFor(By.text("Frédéric Chopin"), LIBRARY_TIMEOUT);
        screenshot("2_library");
        mDevice.findObject(By.text("Frédéric Chopin")).click();
        waitFor(By.text("Nocturnes, Op. 9")).click();
        waitFor(By.textStartsWith("Nocturne in"));
        screenshot("2_library_album");

        navigateTo("navSearch");
        search("Gymnopédie");
        // Results are grouped by source, then artist and album: open the first entry at each
        // level down to the tracks.
        BySelector tracks = By.textStartsWith("Gymnopédie No.");
        for (int level = 0; level < 4 && !mDevice.wait(Until.hasObject(tracks), 5000); level++) {
            waitFor(tag("global_search")).getChildren().get(0).click();
            SystemClock.sleep(SETTLE_MILLIS);
        }
        waitFor(tracks);
        screenshot("4_search");
    }
}
