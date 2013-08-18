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

import java.io.File;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;

/**
 * The settings screen of Clementine Remote
 */
public class ClementineSettings extends SherlockPreferenceActivity
								  implements OnSharedPreferenceChangeListener {
	
	private Preference mLicenseDialogPreference;
	private Preference mAboutDialogPreference;
	private Preference mVersion;
	private Dialog mCustomDialog;
	private EditTextPreference mPortPreference;
	private ListPreference mCallVolume;
	private FileDialog mFileDialog;
	private Preference mDownloadDir;
	private Preference mClementine;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        addPreferencesFromResource(R.xml.clementine_remote_settings);
        
        // Get the dialog preferences
        mLicenseDialogPreference = (Preference) getPreferenceScreen().findPreference("pref_key_license");
        mAboutDialogPreference = (Preference) getPreferenceScreen().findPreference("pref_key_about");
        mVersion = (Preference) getPreferenceScreen().findPreference("pref_version");
        mDownloadDir = (Preference) getPreferenceScreen().findPreference(App.SP_DOWNLOAD_DIR);
        mClementine = (Preference) getPreferenceScreen().findPreference("pref_clementine_website");
        
        // Get the Version
        try {
        	mVersion.setTitle(getString(R.string.pref_version_title) + 
							  " " + 
							  getPackageManager()
							  .getPackageInfo(getPackageName(), 0 ).versionName);
		} catch (NameNotFoundException e) {
			
		}
        
        mVersion.setOnPreferenceClickListener(etBrowserLinks);
        mClementine.setOnPreferenceClickListener(etBrowserLinks);
        
        // Read the port and fill in the summary
        mPortPreference = (EditTextPreference) getPreferenceScreen().findPreference(App.SP_KEY_PORT);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        String port = sharedPreferences.getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort));
		mPortPreference.setSummary(getString(R.string.pref_port_summary) + " " + port);
		
		mPortPreference.setOnPreferenceClickListener(etListener);
		
		mCallVolume = (ListPreference) getPreferenceScreen().findPreference(App.SP_CALL_VOLUME);
		String currentCallVolume = sharedPreferences.getString(App.SP_CALL_VOLUME, "20");
		mCallVolume.setSummary(getString(R.string.pref_call_volume_summary).replace("%s", currentCallVolume));
        
		// Set the onclicklistener for the dialogs
        mLicenseDialogPreference.setOnPreferenceClickListener(opclLicense);
        mAboutDialogPreference.setOnPreferenceClickListener(opclAbout);
        mDownloadDir.setOnPreferenceClickListener(opclDownloadDir );
        
        // Register Listener
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        
        // Create dialog
        String defaultPath = Environment.getExternalStorageDirectory() + "/ClementineMusic";
        String path = sharedPreferences.getString(App.SP_DOWNLOAD_DIR, defaultPath);
        File mPath = new File(path);
        mFileDialog = new FileDialog(this, mPath);
        mFileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
          public void directorySelected(File directory) {
        	  SharedPreferences.Editor editor = getPreferenceScreen().getSharedPreferences().edit();
				editor.putString(App.SP_DOWNLOAD_DIR, directory.getAbsolutePath());
				editor.commit();
				mDownloadDir.setSummary(directory.getAbsolutePath());
          }
        });
        mFileDialog.setSelectDirectoryOption(true);
        mDownloadDir.setSummary(path);
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
		} else if (key.equals(App.SP_CALL_VOLUME)) {
			String currentCallVolume = sharedPreferences.getString(App.SP_CALL_VOLUME, "20");
			mCallVolume.setSummary(getString(R.string.pref_call_volume_summary).replace("%s", currentCallVolume));
		}
	}
	
	/**
	 * Create a new Licensedialog
	 */
	private OnPreferenceClickListener opclLicense = new OnPreferenceClickListener() {
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			mCustomDialog = new Dialog(ClementineSettings.this);
			mCustomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			mCustomDialog.setContentView(R.layout.dialog_license);
			mCustomDialog.setCancelable(true);
			mCustomDialog.getWindow().getAttributes().width = LayoutParams.MATCH_PARENT;
			
			Button button = (Button) mCustomDialog.findViewById(R.id.btnCloseLicense);
			button.setOnClickListener(new OnClickListener() {
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
	private OnPreferenceClickListener opclAbout = new OnPreferenceClickListener() {
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			mCustomDialog = new Dialog(ClementineSettings.this);
			mCustomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			mCustomDialog.setContentView(R.layout.dialog_about);
			mCustomDialog.setCancelable(true);
			mCustomDialog.getWindow().getAttributes().width = LayoutParams.MATCH_PARENT;
			
			// Fill the people working on this project
			TextView tvAuthors    = (TextView) mCustomDialog.findViewById(R.id.tvAuthors);
			TextView tvSupporters = (TextView) mCustomDialog.findViewById(R.id.tvSupporters);
			TextView tvOthers     = (TextView) mCustomDialog.findViewById(R.id.tvOthers);
			
			// Authors
			tvAuthors.setText("Andreas Muttscheller\n");
			
			// Supporters
			tvSupporters.setText("David Sansome (Clementine-Dev)\n" + 
								 "John Maguire (Clementine-Dev)\n");
			
			// Others
			tvOthers.setText(Html.fromHtml("<a href=\"http://actionbarsherlock.com/\">ActionBarSherlock</a> (<a href=\"http://www.apache.org/licenses/LICENSE-2.0.html\">License</a>)<br>" +
										   "<a href=\"http://jmdns.sourceforge.net/\">JmDNS</a> (<a href=\"http://jmdns.sourceforge.net/license.html\">License</a>)<br>" +
										   "and all the <a href=\"https://www.transifex.com/projects/p/clementine-remote/\">translators</a>!"));
			tvOthers.setMovementMethod(LinkMovementMethod.getInstance());
			
			// Create the buttons and the listener
			Button button = (Button) mCustomDialog.findViewById(R.id.btnCloseAbout);
			button.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mCustomDialog.dismiss();
				}
			});
			mCustomDialog.show();
			return true;
		}
	};
	
	private OnPreferenceClickListener opclDownloadDir = new OnPreferenceClickListener() {
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			mFileDialog.showDialog();
			return true;
		}
	};
	
	private OnPreferenceClickListener etListener = new OnPreferenceClickListener() {
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			EditTextPreference editTextPref = (EditTextPreference) preference;
			editTextPref.getEditText().setSelection(editTextPref.getText().length());
			return true;
		}
	};
	
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
	
	private OnPreferenceClickListener etBrowserLinks = new OnPreferenceClickListener() {
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			if (preference.getKey().equals("pref_version")) {
				intent.setData(Uri.parse("https://code.google.com/p/clementine-remote-android"));
			} else if (preference.getKey().equals("pref_clementine_website")) {
				intent.setData(Uri.parse("http://www.clementine-player.org/"));
			}
			if (intent.resolveActivity(getPackageManager()) != null) {
			    startActivity(intent);
			} else {
			    Toast.makeText(ClementineSettings.this, R.string.app_not_available, Toast.LENGTH_LONG).show();
			}
			return true;
		}
	};
}
