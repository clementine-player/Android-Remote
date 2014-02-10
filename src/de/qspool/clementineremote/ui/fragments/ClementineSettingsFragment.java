package de.qspool.clementineremote.ui.fragments;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ClementineSettingsFragment extends PreferenceFragment 
										 implements OnSharedPreferenceChangeListener {

	private Preference mLicenseDialogPreference;
	private Preference mAboutDialogPreference;
	private Preference mVersion;
	private Dialog mCustomDialog;
	private EditTextPreference mPortPreference;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.clementine_remote_settings);
        
        // Get the dialog preferences
        mLicenseDialogPreference = (Preference) getPreferenceScreen().findPreference("pref_key_license");
        mAboutDialogPreference = (Preference) getPreferenceScreen().findPreference("pref_key_about");
        mVersion = (Preference) getPreferenceScreen().findPreference("pref_version");
        
        // Get the Version
        try {
        	mVersion.setTitle(getString(R.string.pref_version_title) + 
							  " " + 
							  getActivity().getPackageManager()
							  			   .getPackageInfo(getActivity().getPackageName(), 0 ).versionName);
		} catch (NameNotFoundException e) {
			
		}
        
        // Read the port and fill in the summary
        mPortPreference = (EditTextPreference) getPreferenceScreen().findPreference(App.SP_KEY_PORT);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        
        String port = sharedPreferences.getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort));
		mPortPreference.setSummary(getString(R.string.pref_port_summary) + " " + port);
		
		mPortPreference.setOnPreferenceClickListener(etListener);
        
		// Set the onclicklistener for the dialogs
        mLicenseDialogPreference.setOnPreferenceClickListener(opclLicense);
        mAboutDialogPreference.setOnPreferenceClickListener(opclAbout);
        
        // Register Listener
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
				Toast.makeText(getActivity(), getString(R.string.pref_port_error), Toast.LENGTH_LONG).show();
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
			mCustomDialog = new Dialog(getActivity());
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
			mCustomDialog = new Dialog(getActivity());
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
	
	private OnPreferenceClickListener etListener = new OnPreferenceClickListener() {
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			EditTextPreference editTextPref = (EditTextPreference) preference;
			editTextPref.getEditText().setSelection(editTextPref.getText().length());
			return true;
		}
	};
}
