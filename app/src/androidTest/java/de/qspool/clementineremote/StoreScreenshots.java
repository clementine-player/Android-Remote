package de.qspool.clementineremote;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

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

    /** Picks an item in the open drawer. */
    private void select(String item) {
        waitFor(By.res(mPackage, "drawer_list").hasDescendant(By.text(item)))
                .findObject(By.text(item)).click();
    }

    private void navigateTo(String item) {
        openDrawer();
        select(item);
    }

    @Test
    public void takeScreenshots() {
        mContext.startActivity(new Intent(mContext, ConnectActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor(id("btnConnect"));
        screenshot("6_connect");

        mDevice.findObject(id("btnConnect")).click();
        waitFor(id("btnPlaypause"));

        // Play a track from the playlist.
        navigateTo("Playlists");
        UiObject2 track = waitFor(By.text("Clair de lune"));
        screenshot("3_playlist");
        track.click();

        navigateTo("Player");
        waitFor(By.res(mPackage, "tvTitle").text("Clair de lune"));
        // Let playback move along the seek bar.
        SystemClock.sleep(8000);
        screenshot("1_player");

        // Back would leave the player and disconnect, so move on from the open drawer.
        openDrawer();
        screenshot("5_navigation");
        select("Library");
        waitFor(By.text("Frédéric Chopin"), LIBRARY_TIMEOUT);
        screenshot("2_library");
        mDevice.findObject(By.text("Frédéric Chopin")).click();
        waitFor(By.text("Nocturnes, Op. 9")).click();
        waitFor(By.textStartsWith("Nocturne in"));
        screenshot("2_library_album");

        navigateTo("Search");
        UiObject2 query = waitFor(id("search_src_text"));
        query.setText("Gymnop");
        mDevice.pressKeyCode(KeyEvent.KEYCODE_ENTER);
        // Submitting collapses the search field, which hides the keyboard.
        waitFor(By.textStartsWith("Gymnopédie No."));
        screenshot("4_search");
    }
}
