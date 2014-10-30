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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.utils.bundle.BundleScrubber;
import de.qspool.clementineremote.utils.bundle.PluginBundleManager;

/**
 * Tasker settings page
 */
public class TaskerSettings extends Activity {

    public final static int ACTION_CONNECT = 0;

    public final static int ACTION_DISCONNECT = 1;

    public final static int ACTION_PLAY = 2;

    public final static int ACTION_PAUSE = 3;

    public final static int ACTION_PLAYPAUSE = 4;

    public final static int ACTION_NEXT = 5;

    private final static String TAG = "TaskerSettings";

    private SharedPreferences mSharedPref;

    private int mSelectedAction = ACTION_CONNECT;

    private EditText mIp;

    private EditText mPort;

    private EditText mAuth;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.tasker_settings);

        getActionBar().setDisplayShowHomeEnabled(true);

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        mIp = (EditText) findViewById(R.id.tasker_ip);
        mPort = (EditText) findViewById(R.id.tasker_port);
        mAuth = (EditText) findViewById(R.id.tasker_auth);
        mIp.setText(mSharedPref.getString(App.SP_KEY_IP, ""));
        mPort.setText(mSharedPref.getString(App.SP_KEY_PORT, String.valueOf(Clementine.DefaultPort)));
        mAuth.setText(String.valueOf(mSharedPref.getInt(App.SP_LAST_AUTH_CODE, 0)));

        BundleScrubber.scrub(getIntent());
        final Bundle localeBundle = getIntent()
                .getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
        BundleScrubber.scrub(localeBundle);

        if (null == savedInstanceState) {
            if (PluginBundleManager.isBundleValid(localeBundle)) {
                mIp.setText(localeBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_IP));
                mPort.setText(String.valueOf(
                        localeBundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_PORT)));
                mAuth.setText(String.valueOf(
                        localeBundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_AUTH)));

                mSelectedAction = localeBundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_TYPE);
                switch (mSelectedAction) {
                    case ACTION_CONNECT:
                        ((RadioButton) findViewById(R.id.radio_connect)).setChecked(true);
                        break;
                    case ACTION_DISCONNECT:
                        ((RadioButton) findViewById(R.id.radio_disconnect)).setChecked(true);
                        break;
                    case ACTION_PLAY:
                        ((RadioButton) findViewById(R.id.radio_play)).setChecked(true);
                        break;
                    case ACTION_PAUSE:
                        ((RadioButton) findViewById(R.id.radio_pause)).setChecked(true);
                        break;
                    case ACTION_PLAYPAUSE:
                        ((RadioButton) findViewById(R.id.radio_playpause)).setChecked(true);
                        break;
                    case ACTION_NEXT:
                        ((RadioButton) findViewById(R.id.radio_next)).setChecked(true);
                        break;
                }
            }
        }
    }

    @Override
    public void finish() {
        final Intent resultIntent = new Intent();

        String ip = mIp.getText().toString();
        int port = Integer.valueOf(mPort.getText().toString());
        int auth = Integer.valueOf(mAuth.getText().toString());

        final Bundle resultBundle =
                PluginBundleManager.generateBundle(getApplicationContext(), mSelectedAction,
                        ip, port, auth);

        resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, resultBundle);

        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.tasker_action));
        sb.append(" ");

        switch (mSelectedAction) {
            case ACTION_CONNECT:
                sb.append(getString(R.string.tasker_connect));
                sb.append(" / ");
                sb.append(ip);
                sb.append(":");
                sb.append(port);
                break;
            case ACTION_DISCONNECT:
                sb.append(getString(R.string.tasker_disconnect));
                break;
            case ACTION_PLAY:
                sb.append(getString(R.string.tasker_play));
                break;
            case ACTION_PAUSE:
                sb.append(getString(R.string.tasker_pause));
                break;
            case ACTION_PLAYPAUSE:
                sb.append(getString(R.string.tasker_playpause));
                break;
            case ACTION_NEXT:
                sb.append(getString(R.string.tasker_next));
                break;
        }

        resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB,
                generateBlurb(getApplicationContext(), sb.toString()));

        setResult(RESULT_OK, resultIntent);

        super.finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.done:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inf = getMenuInflater();
        inf.inflate(R.menu.tasker_menu, menu);

        return true;
    }

    /**
     * @param context Application context.
     * @param message The toast message to be displayed by the plug-in. Cannot be null.
     * @return A blurb for the plug-in.
     */
    static String generateBlurb(final Context context, final String message) {
        final int maxBlurbLength =
                context.getResources()
                        .getInteger(R.integer.twofortyfouram_locale_maximum_blurb_length);

        if (message.length() > maxBlurbLength) {
            return message.substring(0, maxBlurbLength);
        }

        return message;
    }

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();
        if (!checked) {
          return;
        }

        // Check which radio button was clicked
        switch (view.getId()) {
            case R.id.radio_connect:
                mSelectedAction = ACTION_CONNECT;
                break;
            case R.id.radio_disconnect:
                mSelectedAction = ACTION_DISCONNECT;
                break;
            case R.id.radio_play:
                mSelectedAction = ACTION_PLAY;
                break;
            case R.id.radio_pause:
                mSelectedAction = ACTION_PAUSE;
                break;
            case R.id.radio_playpause:
                mSelectedAction = ACTION_PLAYPAUSE;
                break;
            case R.id.radio_next:
                mSelectedAction = ACTION_NEXT;
                break;
            default:
                break;
        }
    }
}
