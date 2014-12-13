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

package de.qspool.clementineremote.ui.settings;

import android.app.Dialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.qspool.clementineremote.R;

public class PreferencesInformationLicenses extends PreferenceFragment {

    private Preference mLicenseDialogPreference;

    private Preference mOpenSourceDialogPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_license);

        // Get the dialog preferences
        mLicenseDialogPreference = getPreferenceScreen()
                .findPreference("pref_key_license");
        mOpenSourceDialogPreference = getPreferenceScreen()
                .findPreference("pref_key_opensource");

        // Set the onclicklistener for the dialogs
        mLicenseDialogPreference.setOnPreferenceClickListener(opclLicense);
        mOpenSourceDialogPreference.setOnPreferenceClickListener(opclOpenSource);
    }

    /**
     * Create a new Licensedialog
     */
    private Preference.OnPreferenceClickListener
            opclLicense = new Preference.OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            final Dialog licenseDialog = new Dialog(getActivity());
            licenseDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            licenseDialog.setContentView(R.layout.dialog_license);
            licenseDialog.setCancelable(true);
            licenseDialog.getWindow().getAttributes().width = ViewGroup.LayoutParams.MATCH_PARENT;

            Button button = (Button) licenseDialog.findViewById(R.id.btnCloseLicense);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    licenseDialog.dismiss();
                }
            });
            licenseDialog.show();
            return true;
        }
    };

    private Preference.OnPreferenceClickListener
            opclOpenSource = new Preference.OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            final Dialog openSourceDialog = new Dialog(getActivity());
            openSourceDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            openSourceDialog.setContentView(R.layout.dialog_opensource);
            openSourceDialog.setCancelable(true);
            openSourceDialog.getWindow().getAttributes().width
                    = ViewGroup.LayoutParams.MATCH_PARENT;

            Button button = (Button) openSourceDialog.findViewById(R.id.btnCloseLicense);
            WebView text = (WebView) openSourceDialog.findViewById(R.id.opensource_licenses);

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
                    openSourceDialog.dismiss();
                }
            });
            openSourceDialog.show();
            return true;
        }
    };
}
