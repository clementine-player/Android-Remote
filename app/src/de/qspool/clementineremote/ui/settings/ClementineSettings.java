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

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ListAdapter;

import java.util.List;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.ui.adapter.PreferenceHeaderAdapter;
import de.qspool.clementineremote.utils.Utilities;

/**
 * The settings screen of Clementine Remote
 */
public class ClementineSettings extends PreferenceActivity {

    private List<Header> mHeaders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        // Keep screen on if user has requested this in preferences
        if (PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(SharedPreferencesKeys.SP_KEEP_SCREEN_ON, true)
                && Utilities.isRemoteConnected()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void setListAdapter(ListAdapter adapter) {
        if (adapter == null) {
            super.setListAdapter(adapter);
        } else {
            super.setListAdapter(new PreferenceHeaderAdapter(this, mHeaders));
        }
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
        mHeaders = target;
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (PreferencesBehaviorPlayer.class.getName().equals(fragmentName)) {
            return true;
        }
        if (PreferencesBehaviorDownloads.class.getName().equals(fragmentName)) {
            return true;
        }
        if (PreferencesConnection.class.getName().equals(fragmentName)) {
            return true;
        }
        if (PreferencesInformationAbout.class.getName().equals(fragmentName)) {
            return true;
        }
        if (PreferencesInformationLicenses.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }

    @Override
    public Header onGetInitialHeader() {
        super.onResume();
        if (mHeaders != null) {
            for (Header h : mHeaders) {
                if (!PreferenceHeaderAdapter.isHeaderCategory(h)) {
                    return h;
                }
            }
        }
        return null;
    }
}
