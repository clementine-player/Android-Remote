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
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;
import android.view.View.OnClickListener;

/**
 * The settings screen of Clementine Remote
 */
public class ClementineRemoteSettings extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private Preference mLicenseDialogPreference;
	private Preference mAboutDialogPreference;
	private Dialog mCustomDialog;
	private EditTextPreference mPortPreference;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.clementine_remote_settings);
        
        // Get the dialog preferences
        mLicenseDialogPreference = (Preference) getPreferenceScreen().findPreference("pref_key_license");
        mAboutDialogPreference = (Preference) getPreferenceScreen().findPreference("pref_key_about");
        
        // Read the port and fill in the summary
        mPortPreference = (EditTextPreference) getPreferenceScreen().findPreference("pref_port");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        String port = sharedPreferences.getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort));
		mPortPreference.setSummary(getString(R.string.pref_port_summary) + " " + port);
        
		// Set the onclicklistener for the dialogs
        mLicenseDialogPreference.setOnPreferenceClickListener(opclLicense);
        mAboutDialogPreference.setOnPreferenceClickListener(opclAbout);
        
        /// Register Listener
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(App.SP_KEY_PORT)) {
			String port = sharedPreferences.getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort));
			// Check if the port is in a valid range
			if (Integer.parseInt(port) < 1024 || Integer.parseInt(port) > 65535) {
				port = String.valueOf(Clementine.DefaultPort);
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(App.SP_KEY_PORT, port);
				editor.commit();
				
				// Tell the user that he specified an illegal port
				Toast.makeText(this, getString(R.string.pref_port_error), Toast.LENGTH_LONG).show();
			}
			// Set the summary
			mPortPreference.setSummary(getString(R.string.pref_port_summary) + " " + port);
			mPortPreference.setText(port);
		}
	}
	
	/**
	 * Create a new Licensedialog
	 */
	private OnPreferenceClickListener opclLicense = new OnPreferenceClickListener() {
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			mCustomDialog = new Dialog(ClementineRemoteSettings.this);
			mCustomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			mCustomDialog.setContentView(R.layout.dialog_license);
			mCustomDialog.setCancelable(true);
			mCustomDialog.getWindow().getAttributes().width = LayoutParams.MATCH_PARENT;
			
			Button button = (Button) mCustomDialog.findViewById(R.id.btnCloseLicense);
			button.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					ClementineRemoteSettings.this.mCustomDialog.dismiss();
				}
			});
			mCustomDialog.show();
			return true;
		}
	};
	
	/**
	 * Create a new about dialog
	 */
	private OnPreferenceClickListener opclAbout = new OnPreferenceClickListener() {
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			mCustomDialog = new Dialog(ClementineRemoteSettings.this);
			mCustomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			mCustomDialog.setContentView(R.layout.dialog_about);
			mCustomDialog.setCancelable(true);
			mCustomDialog.getWindow().getAttributes().width = LayoutParams.MATCH_PARENT;
			
			Button button = (Button) mCustomDialog.findViewById(R.id.btnCloseAbout);
			button.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					ClementineRemoteSettings.this.mCustomDialog.dismiss();
				}
			});
			mCustomDialog.show();
			return true;
		}
	};
}
