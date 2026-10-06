package de.qspool.clementineremote.testing;

import android.os.StrictMode;
import android.os.strictmode.IncorrectContextUseViolation;
import android.os.strictmode.Violation;
import android.util.Log;

import org.junit.rules.ExternalResource;

import java.util.ArrayList;
import java.util.List;

import de.qspool.clementineremote.utils.StrictModePolicies;

import static org.junit.Assert.fail;

/**
 * Fails the test if it triggers a StrictMode VM policy violation (leaked cursors or streams,
 * file:// URIs handed to other apps, unsafe intents).
 *
 * <p>Robolectric does not detect thread policy violations (disk or network on the main
 * thread): those need a real Android runtime and are checked by the instrumented
 * StrictModeDeviceTest on an emulator.
 */
public class StrictModeRule extends ExternalResource {

    private final List<Violation> mViolations = new ArrayList<>();

    private StrictMode.VmPolicy mPrevious;

    @Override
    protected void before() {
        mPrevious = StrictMode.getVmPolicy();
        StrictMode.setVmPolicy(StrictModePolicies.vmPolicy()
                .penaltyListener(Runnable::run, this::record)
                .build());
    }

    private synchronized void record(Violation violation) {
        // Robolectric reports even activities as non-UI contexts, so this check only means
        // something on a real runtime (debug builds, StrictModeDeviceTest).
        if (violation instanceof IncorrectContextUseViolation) {
            return;
        }
        // Robolectric's stand-in for MediaStore never closes the database it makes to keep the
        // songs in. That leak is found whenever the garbage collector next runs, often in a later
        // test. Cursors the app leaks from its queries are opened elsewhere, and still fail.
        if (openedIn(violation, "org.robolectric.fakes.FakeMediaProvider", "onCreate")) {
            return;
        }
        mViolations.add(violation);
    }

    /**
     * Whether the resource {@code violation} reports was opened in {@code className}'s method
     * {@code method}, or in a lambda inside it.
     */
    private static boolean openedIn(Violation violation, String className, String method) {
        for (Throwable t = violation; t != null; t = t.getCause()) {
            for (StackTraceElement frame : t.getStackTrace()) {
                if (frame.getClassName().equals(className)
                        && (frame.getMethodName().equals(method)
                                || frame.getMethodName().startsWith("lambda$" + method + "$"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void after() {
        // Leak checks run when objects are garbage collected.
        System.gc();
        System.runFinalization();
        StrictMode.setVmPolicy(mPrevious);
        synchronized (this) {
            if (!mViolations.isEmpty()) {
                StringBuilder message = new StringBuilder("StrictMode violations:");
                for (Violation violation : mViolations) {
                    message.append("\n  ").append(Log.getStackTraceString(violation));
                }
                fail(message.toString());
            }
        }
    }
}
