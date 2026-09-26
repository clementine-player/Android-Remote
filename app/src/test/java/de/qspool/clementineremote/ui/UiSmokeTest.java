package de.qspool.clementineremote.ui;

import androidx.lifecycle.ViewModelProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.testing.StrictModeRule;
import de.qspool.clementineremote.ui.connect.ConnectDialog;
import de.qspool.clementineremote.ui.connect.ConnectViewModel;
import de.qspool.clementineremote.ui.settings.ClementineSettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Starts the app's screens, to catch errors in setting them up, such as a theme a screen needs
 * missing, or main-thread disk access.
 */
@RunWith(RobolectricTestRunner.class)
public class UiSmokeTest {

    @Rule
    public final StrictModeRule mStrictMode = new StrictModeRule();

    @Before
    public void setUp() {
        App.Clementine = new Clementine();
        App.ClementineConnection = null;
    }

    @Test
    public void settingsStart() {
        ActivityController<ClementineSettings> controller =
                Robolectric.buildActivity(ClementineSettings.class).setup();
        assertFalse(controller.get().isFinishing());
        controller.pause().stop().destroy();
    }

    @Test
    public void connectStartsWithTheFirstRunMessageThenThePermissions() {
        ActivityController<ConnectActivity> controller =
                Robolectric.buildActivity(ConnectActivity.class).setup();
        ConnectViewModel state = new ViewModelProvider(controller.get()).get(ConnectViewModel.class);

        assertTrue(state.getDialog().getValue() instanceof ConnectDialog.Message);
        state.dismissDialog();
        assertEquals(new ConnectDialog.Permissions(java.util.Arrays.asList(
                controller.get().missingPermissions())), state.getDialog().getValue());

        controller.pause().stop().destroy();
    }
}
