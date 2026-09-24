package de.qspool.clementineremote;

import android.app.Activity;
import android.os.StrictMode;
import android.os.strictmode.Violation;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.qspool.clementineremote.ui.ConnectActivity;
import de.qspool.clementineremote.ui.TaskerSettings;
import de.qspool.clementineremote.ui.settings.ClementineSettings;
import de.qspool.clementineremote.utils.StrictModePolicies;

import static org.junit.Assert.fail;

/**
 * Opens the app's screens on a real Android runtime under StrictMode, which (unlike
 * Robolectric) detects disk and network access on the main thread.
 *
 * <p>Each violation is reduced to a signature: its type and the first app method in its stack.
 * Known violations are listed with the reason they are accepted in
 * src/androidTest/resources/strictmode-baseline.txt; anything else fails the test, which
 * prints every signature it saw so the baseline can be reviewed.
 */
@RunWith(AndroidJUnit4.class)
public class StrictModeDeviceTest {

    private static final String TAG = "StrictModeDeviceTest";

    private final List<Violation> mViolations = new ArrayList<>();

    private StrictMode.ThreadPolicy mPreviousThreadPolicy;

    private StrictMode.VmPolicy mPreviousVmPolicy;

    @Before
    public void installPolicies() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPreviousThreadPolicy = StrictMode.getThreadPolicy();
            mPreviousVmPolicy = StrictMode.getVmPolicy();
            StrictMode.setThreadPolicy(StrictModePolicies.threadPolicy()
                    .penaltyListener(Runnable::run, this::record).build());
            StrictMode.setVmPolicy(StrictModePolicies.vmPolicy()
                    .penaltyListener(Runnable::run, this::record).build());
        });
    }

    @After
    public void restorePolicies() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            StrictMode.setThreadPolicy(mPreviousThreadPolicy);
            StrictMode.setVmPolicy(mPreviousVmPolicy);
        });
    }

    private synchronized void record(Violation violation) {
        mViolations.add(violation);
    }

    private static <T extends Activity> void open(Class<T> activity) {
        try (ActivityScenario<T> scenario = ActivityScenario.launch(activity)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    @Test
    public void screensOpenWithoutNewViolations() throws Exception {
        open(ConnectActivity.class);
        open(ClementineSettings.class);
        open(TaskerSettings.class);

        // Leak checks run when objects are garbage collected.
        Runtime.getRuntime().gc();
        System.runFinalization();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Signature -> first violation with it, for its stack.
        Map<String, Violation> seen = new TreeMap<>();
        synchronized (this) {
            for (Violation violation : mViolations) {
                String signature = signature(violation);
                if (!seen.containsKey(signature)) {
                    seen.put(signature, violation);
                    Log.w(TAG, signature, violation);
                }
            }
        }

        Set<String> baseline = baseline();
        Log.i(TAG, "All signatures seen:\n" + String.join("\n", seen.keySet()));
        StringBuilder unexpected = new StringBuilder();
        for (Map.Entry<String, Violation> entry : seen.entrySet()) {
            if (!baseline.contains(entry.getKey())) {
                unexpected.append('\n').append(entry.getKey()).append(trimmedStack(entry.getValue()));
            }
        }
        if (unexpected.length() > 0) {
            fail("New StrictMode violations:" + unexpected);
        }
    }

    /** The first frames of a violation's stack, enough to see what did the I/O. */
    private static String trimmedStack(Violation violation) {
        StringBuilder out = new StringBuilder();
        StackTraceElement[] stack = violation.getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 25); i++) {
            out.append("\n    > ").append(stack[i]);
        }
        return out.toString();
    }

    /** "ViolationType at first.app.Class.method", or the first frame if none is ours. */
    static String signature(Violation violation) {
        StackTraceElement[] stack = violation.getStackTrace();
        String where = stack.length > 0 ? stack[0].getClassName() + "." + stack[0].getMethodName()
                : "?";
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().startsWith("de.qspool.clementineremote.")
                    && !frame.getClassName().contains("StrictModeDeviceTest")) {
                where = frame.getClassName() + "." + frame.getMethodName();
                break;
            }
        }
        return violation.getClass().getSimpleName() + " at " + where;
    }

    private Set<String> baseline() throws Exception {
        Set<String> entries = new TreeSet<>();
        InputStream in = getClass().getClassLoader()
                .getResourceAsStream("strictmode-baseline.txt");
        if (in == null) {
            return entries;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // "signature  # reason"
                String entry = line.replaceFirst("#.*", "").trim();
                if (!entry.isEmpty()) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }
}
