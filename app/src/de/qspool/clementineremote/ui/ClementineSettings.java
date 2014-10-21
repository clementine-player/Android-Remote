/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.ui;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.utils.Utilities;

/**
 * The settings screen of Clementine Remote
 */
public class ClementineSettings extends PreferenceActivity {
    /**
     * When starting this activity, the invoking Intent can contain this extra
     * string to specify which fragment should be initially displayed.
     */
    public static final String EXTRA_SHOW_FRAGMENT = ":android:show_fragment";

    /**
     * When starting this activity, the invoking Intent can contain this extra
     * boolean that the header list should not be displayed.  This is most often
     * used in conjunction with {@link #EXTRA_SHOW_FRAGMENT} to launch
     * the activity to display a specific fragment that the user has navigated
     * to.
     */
    public static final String EXTRA_NO_HEADERS = ":android:no_headers";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if(ClementineSettingsFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    /**
     * This fragment contains a second-level set of preference that you
     * can get to by tapping an item in the first preferences fragment.
     */
    public static class ClementineSettingsFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {
        private Preference mLicenseDialogPreference;

        private Preference mOpenSourceDialogPreference;

        private Preference mAboutDialogPreference;

        private Preference mVersion;

        private Dialog mCustomDialog;

        private EditTextPreference mPortPreference;

        private ListPreference mCallVolume;

        private ListPreference mVolumeInc;

        private FileDialog mFileDialog;

        private Preference mDownloadDir;

        private Preference mClementine;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Can retrieve arguments from preference XML.
            Log.i("args", "Arguments: " + getArguments());

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.clementine_remote_settings);

            // Get the dialog preferences
            mLicenseDialogPreference = getPreferenceScreen()
                    .findPreference("pref_key_license");
            mOpenSourceDialogPreference = getPreferenceScreen()
                    .findPreference("pref_key_opensource");
            mAboutDialogPreference = getPreferenceScreen()
                    .findPreference("pref_key_about");
            mVersion = getPreferenceScreen().findPreference("pref_version");
            mDownloadDir = getPreferenceScreen().findPreference(App.SP_DOWNLOAD_DIR);
            mClementine = getPreferenceScreen().findPreference("pref_clementine_website");

            // Get the Version
            try {
                mVersion.setTitle(getString(R.string.pref_version_title) +
                        " " +
                        getActivity().getPackageManager()
                                .getPackageInfo(getActivity().getPackageName(), 0).versionName);
            } catch (PackageManager.NameNotFoundException e) {

            }

            mVersion.setOnPreferenceClickListener(etBrowserLinks);
            mClementine.setOnPreferenceClickListener(etBrowserLinks);

            // Read the port and fill in the summary
            mPortPreference = (EditTextPreference) getPreferenceScreen()
                    .findPreference(App.SP_KEY_PORT);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

            String port = sharedPreferences
                    .getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort));
            mPortPreference.setSummary(getString(R.string.pref_port_summary) + " " + port);

            mPortPreference.setOnPreferenceClickListener(etListener);

            mCallVolume = (ListPreference) getPreferenceScreen().findPreference(App.SP_CALL_VOLUME);
            String currentCallVolume = sharedPreferences.getString(App.SP_CALL_VOLUME, "20");
            mCallVolume.setSummary(
                    getString(R.string.pref_call_volume_summary).replace("%s", currentCallVolume));

            mVolumeInc = (ListPreference) getPreferenceScreen().findPreference(App.SP_VOLUME_INC);
            String currentVolumeInc = sharedPreferences
                    .getString(App.SP_VOLUME_INC, Clementine.DefaultVolumeInc);
            mVolumeInc.setSummary(
                    getString(R.string.pref_volume_inc_summary).replace("%s", currentVolumeInc));

            // Set the onclicklistener for the dialogs
            mLicenseDialogPreference.setOnPreferenceClickListener(opclLicense);
            mOpenSourceDialogPreference.setOnPreferenceClickListener(opclOpenSource);
            mAboutDialogPreference.setOnPreferenceClickListener(opclAbout);
            mDownloadDir.setOnPreferenceClickListener(opclDownloadDir);

            // Register Listener
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            // Create dialog
            String defaultPath = Environment.getExternalStorageDirectory() + "/ClementineMusic";
            String path = sharedPreferences.getString(App.SP_DOWNLOAD_DIR, defaultPath);
            File mPath = new File(path);
            mFileDialog = new FileDialog(getActivity(), mPath);
            mFileDialog.setCheckIfWritable(true);
            mFileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
                public void directorySelected(File directory) {
                    SharedPreferences.Editor editor = getPreferenceScreen().getSharedPreferences()
                            .edit();
                    editor.putString(App.SP_DOWNLOAD_DIR, directory.getAbsolutePath());
                    editor.commit();
                    mDownloadDir.setSummary(directory.getAbsolutePath());
                }
            });
            mFileDialog.setSelectDirectoryOption(true);
            mDownloadDir.setSummary(path);

            // Keep screen on if user has requested this in preferences
            if (sharedPreferences.getBoolean(App.SP_KEEP_SCREEN_ON, true)
                    && Utilities.isRemoteConnected()) {
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                String key) {
            if (key.equals(App.SP_KEY_PORT)) {
                String port = sharedPreferences
                        .getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort));
                int intPort = 0;
                try {
                    intPort = Integer.parseInt(port);
                } catch (NumberFormatException e) { }

                // Check if the port is in a valid range
                if (intPort < 1024 || intPort > 65535) {
                    port = String.valueOf(Clementine.DefaultPort);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(App.SP_KEY_PORT, port);
                    editor.commit();

                    // Tell the user that he specified an illegal port
                    Toast.makeText(getActivity(), getString(R.string.pref_port_error), Toast.LENGTH_LONG).show();
                }
                // Set the summary
                mPortPreference.setSummary(getString(R.string.pref_port_summary) + " " + port);
                mPortPreference.setText(port);
            } else if (key.equals(App.SP_CALL_VOLUME)) {
                String currentCallVolume = sharedPreferences.getString(App.SP_CALL_VOLUME, "20");
                mCallVolume.setSummary(
                        getString(R.string.pref_call_volume_summary).replace("%s", currentCallVolume));
            } else if (key.equals(App.SP_VOLUME_INC)) {
                String currentVolumeInc = sharedPreferences
                        .getString(App.SP_VOLUME_INC, Clementine.DefaultVolumeInc);
                mVolumeInc.setSummary(
                        getString(R.string.pref_volume_inc_summary).replace("%s", currentVolumeInc));
            }
        }

        /**
         * Create a new Licensedialog
         */
        private Preference.OnPreferenceClickListener
                opclLicense = new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCustomDialog = new Dialog(getActivity());
                mCustomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                mCustomDialog.setContentView(R.layout.dialog_license);
                mCustomDialog.setCancelable(true);
                mCustomDialog.getWindow().getAttributes().width = ViewGroup.LayoutParams.MATCH_PARENT;

                Button button = (Button) mCustomDialog.findViewById(R.id.btnCloseLicense);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCustomDialog.dismiss();
                    }
                });
                mCustomDialog.show();
                return true;
            }
        };

        private Preference.OnPreferenceClickListener
                opclOpenSource = new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCustomDialog = new Dialog(getActivity());
                mCustomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                mCustomDialog.setContentView(R.layout.dialog_opensource);
                mCustomDialog.setCancelable(true);
                mCustomDialog.getWindow().getAttributes().width = ViewGroup.LayoutParams.MATCH_PARENT;

                Button button = (Button) mCustomDialog.findViewById(R.id.btnCloseLicense);
                WebView text = (WebView) mCustomDialog.findViewById(R.id.opensource_licenses);

                InputStream is = getResources().openRawResource(R.raw.opensource);
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String s;

                try {
                    while ((s = br.readLine()) != null) {
                        sb.append(s);
                        sb.append("\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                text.loadDataWithBaseURL(null, sb.toString(), "text/html", "utf-8", null);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCustomDialog.dismiss();
                    }
                });
                mCustomDialog.show();
                return true;
            }
        };

        /**
         * Create a new about dialog
         */
        private Preference.OnPreferenceClickListener
                opclAbout = new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCustomDialog = new Dialog(getActivity());
                mCustomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                mCustomDialog.setContentView(R.layout.dialog_about);
                mCustomDialog.setCancelable(true);
                mCustomDialog.getWindow().getAttributes().width = ViewGroup.LayoutParams.MATCH_PARENT;

                // Fill the people working on this project
                TextView tvAuthors = (TextView) mCustomDialog.findViewById(R.id.tvAuthors);
                TextView tvSupporters = (TextView) mCustomDialog.findViewById(R.id.tvSupporters);
                TextView tvOthers = (TextView) mCustomDialog.findViewById(R.id.tvOthers);

                // Authors
                tvAuthors.setText("Andreas Muttscheller\n");

                // Supporters
                tvSupporters.setText("David Sansome (Clementine-Dev)\n" +
                        "John Maguire (Clementine-Dev)\n" +
                        "Arnaud Bienner (Clementine-Dev)");

                // Others
                tvOthers.setText(Html.fromHtml(
                        "Thanks to all the <a href=\"https://www.transifex.com/projects/p/clementine-remote/\">translators</a>!"));
                tvOthers.setMovementMethod(LinkMovementMethod.getInstance());

                // Create the buttons and the listener
                Button button = (Button) mCustomDialog.findViewById(R.id.btnCloseAbout);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCustomDialog.dismiss();
                    }
                });
                mCustomDialog.show();
                return true;
            }
        };

        private Preference.OnPreferenceClickListener
                opclDownloadDir = new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    mFileDialog.showDialog();
                } else {
                    Toast.makeText(getActivity(), R.string.download_noti_not_mounted,
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        };

        private Preference.OnPreferenceClickListener
                etListener = new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                EditTextPreference editTextPref = (EditTextPreference) preference;
                editTextPref.getEditText().setSelection(editTextPref.getText().length());
                return true;
            }
        };

        private Preference.OnPreferenceClickListener
                etBrowserLinks = new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                if (preference.getKey().equals("pref_version")) {
                    intent.setData(Uri.parse("https://github.com/clementine-player/Android-Remote"));
                } else if (preference.getKey().equals("pref_clementine_website")) {
                    intent.setData(Uri.parse("http://www.clementine-player.org/"));
                }
                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), R.string.app_not_available,
                            Toast.LENGTH_LONG).show();
                }
                return true;
            }
        };
    }

}
