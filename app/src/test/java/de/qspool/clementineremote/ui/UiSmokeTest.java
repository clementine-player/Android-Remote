package de.qspool.clementineremote.ui;

import android.app.Dialog;
import android.app.Fragment;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.testing.StrictModeRule;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.ui.dialogs.DownloadChooserDialog;
import de.qspool.clementineremote.ui.dialogs.ProgressDialog;
import de.qspool.clementineremote.ui.settings.ClementineSettings;
import de.qspool.clementineremote.ui.settings.PreferencesBehaviorAdvanced;
import de.qspool.clementineremote.ui.settings.PreferencesBehaviorDownloads;
import de.qspool.clementineremote.ui.settings.PreferencesBehaviorLibrary;
import de.qspool.clementineremote.ui.settings.PreferencesBehaviorPlayer;
import de.qspool.clementineremote.ui.settings.PreferencesConnection;
import de.qspool.clementineremote.ui.settings.PreferencesInformationAbout;
import de.qspool.clementineremote.ui.settings.PreferencesInformationLicenses;
import de.qspool.clementineremote.utils.Utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Opens the app's screens and dialogs to catch inflation and theming errors, such as a
 * layout still naming a pre-AndroidX class.
 */
@RunWith(RobolectricTestRunner.class)
public class UiSmokeTest {

    @Rule
    public final StrictModeRule mStrictMode = new StrictModeRule();

    private ClementineSettings mSettings;

    @Before
    public void setUp() {
        App.Clementine = new Clementine();
        App.ClementineConnection = null;
        mSettings = Robolectric.buildActivity(ClementineSettings.class).setup().get();
    }

    private PreferenceFragment show(PreferenceFragment fragment) {
        mSettings.getFragmentManager().beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commitNow();
        assertNotNull(fragment.getView());
        return fragment;
    }

    private static Dialog latestDialog() {
        ShadowLooper.idleMainLooper();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    @Test
    public void settingsListShowsHeaders() {
        Fragment list = mSettings.getFragmentManager().findFragmentById(R.id.content_frame);
        ListView headers = list.getView().findViewById(android.R.id.list);
        assertTrue(headers.getAdapter().getCount() > 5);
    }

    @Test
    public void everyPreferenceScreenInflates() {
        PreferenceFragment[] screens = {
                new PreferencesBehaviorPlayer(),
                new PreferencesBehaviorLibrary(),
                new PreferencesBehaviorDownloads(),
                new PreferencesBehaviorAdvanced(),
                new PreferencesConnection(),
                new PreferencesInformationAbout(),
                new PreferencesInformationLicenses(),
        };
        for (PreferenceFragment screen : screens) {
            show(screen);
            assertTrue(screen.getClass().getSimpleName(),
                    screen.getPreferenceScreen().getPreferenceCount() > 0);
        }
    }

    /** Clicks a preference the way its list does, which opens its dialog. */
    private static void click(PreferenceFragment fragment, String key) {
        PreferenceScreen screen = fragment.getPreferenceScreen();
        Preference preference = fragment.findPreference(key);
        for (int i = 0; i < screen.getRootAdapter().getCount(); i++) {
            if (screen.getRootAdapter().getItem(i) == preference) {
                screen.onItemClick(null, null, i, 0);
                return;
            }
        }
        throw new AssertionError(key + " not shown");
    }

    @Test
    public void listAndEditTextPreferencesOpenTheirDialogs() {
        click(show(new PreferencesBehaviorPlayer()), "pref_volume_inc");
        latestDialog().dismiss();

        click(show(new PreferencesConnection()), "pref_port");
        latestDialog().dismiss();
    }

    @Test
    public void aboutAndLicenseDialogsOpen() {
        PreferenceFragment about = show(new PreferencesInformationAbout());
        Preference aboutPref = about.findPreference("pref_key_about");
        aboutPref.getOnPreferenceClickListener().onPreferenceClick(aboutPref);
        assertNotNull(latestDialog().findViewById(R.id.tvAuthors));

        PreferenceFragment licenses = show(new PreferencesInformationLicenses());
        for (String key : new String[]{"pref_key_license", "pref_key_opensource"}) {
            Preference pref = licenses.findPreference(key);
            pref.getOnPreferenceClickListener().onPreferenceClick(pref);
            latestDialog().dismiss();
        }
    }

    @Test
    public void progressDialogs() {
        ProgressDialog indeterminate = ProgressDialog.showIndeterminate(mSettings,
                R.string.library_please_wait, R.string.library_optimize, false, null);
        assertTrue(indeterminate.isShowing());
        indeterminate.dismiss();
        assertFalse(indeterminate.isShowing());

        ProgressDialog determinate = ProgressDialog.showDeterminate(mSettings,
                R.string.player_download_playlists, R.string.playlist_loading, 3, true);
        determinate.incrementProgress(1);
        determinate.setMaxProgress(10);
        determinate.setProgress(5);
        determinate.setContent("Playlist");
        assertTrue(determinate.isShowing());
        determinate.dismiss();
    }

    @Test
    public void messageAndChooserDialogs() {
        Dialog message = Utilities.ShowMessageDialog(mSettings, "Title", "<b>Body</b>", true);
        assertTrue(message.isShowing());
        message.dismiss();

        final DownloadChooserDialog.Type[] chosen = new DownloadChooserDialog.Type[1];
        DownloadChooserDialog chooser = new DownloadChooserDialog(mSettings);
        chooser.setCallback(new DownloadChooserDialog.Callback() {
            @Override
            public void onItemClick(DownloadChooserDialog.Type type) {
                chosen[0] = type;
            }
        });
        chooser.showDialog();
        AlertDialog dialog = (AlertDialog) latestDialog();
        dialog.getListView().performItemClick(null, 1, 1);
        assertEquals(DownloadChooserDialog.Type.ALBUM, chosen[0]);
    }

    @Test
    public void connectActivityStarts() {
        ActivityController<ConnectActivity> controller =
                Robolectric.buildActivity(ConnectActivity.class).setup();
        assertNotNull(controller.get().findViewById(R.id.connect_content));
        controller.pause().stop().destroy();
    }
}
