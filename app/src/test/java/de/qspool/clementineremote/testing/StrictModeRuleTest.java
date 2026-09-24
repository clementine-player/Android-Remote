package de.qspool.clementineremote.testing;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The rule has to fail tests that break the VM policy, or it guards nothing. */
@RunWith(RobolectricTestRunner.class)
public class StrictModeRuleTest {

    private static void runUnderRule(Statement body) throws Throwable {
        new StrictModeRule().apply(body, Description.EMPTY).evaluate();
    }

    @Test
    public void failsWhenAFileUriIsHandedToAnotherApp() throws Throwable {
        try {
            runUnderRule(new Statement() {
                @Override
                public void evaluate() {
                    Intent play = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(Uri.fromFile(new File("/sdcard/a.ogg")), "audio/*")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        RuntimeEnvironment.getApplication().startActivity(play);
                    } catch (RuntimeException ignored) {
                        // No activity to handle it; the violation is raised before that.
                    }
                }
            });
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FileUriExposed"));
            return;
        }
        fail("StrictModeRule did not report the exposed file:// URI");
    }

    @Test
    public void passesCleanCode() throws Throwable {
        runUnderRule(new Statement() {
            @Override
            public void evaluate() {
            }
        });
    }
}
