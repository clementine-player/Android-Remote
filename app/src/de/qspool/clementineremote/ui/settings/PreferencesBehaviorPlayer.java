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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;

public class PreferencesBehaviorPlayer extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private ListPreference mCallVolume;

    private ListPreference mVolumeInc;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_player);

        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());

        mCallVolume = (ListPreference) getPreferenceScreen()
                .findPreference(SharedPreferencesKeys.SP_CALL_VOLUME);
        String currentCallVolume = sharedPreferences
                .getString(SharedPreferencesKeys.SP_CALL_VOLUME, "20");
        mCallVolume.setSummary(
                getString(R.string.pref_call_volume_summary).replace("%s", currentCallVolume));

        mVolumeInc = (ListPreference) getPreferenceScreen()
                .findPreference(SharedPreferencesKeys.SP_VOLUME_INC);
        String currentVolumeInc = sharedPreferences
                .getString(SharedPreferencesKeys.SP_VOLUME_INC, Clementine.DefaultVolumeInc);
        mVolumeInc.setSummary(
                getString(R.string.pref_volume_inc_summary).replace("%s", currentVolumeInc));

        // Register Listener
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SharedPreferencesKeys.SP_CALL_VOLUME)) {
            String currentCallVolume = sharedPreferences
                    .getString(SharedPreferencesKeys.SP_CALL_VOLUME, "20");
            mCallVolume.setSummary(
                    getString(R.string.pref_call_volume_summary).replace("%s", currentCallVolume));
        } else if (key.equals(SharedPreferencesKeys.SP_VOLUME_INC)) {
            String currentVolumeInc = sharedPreferences
                    .getString(SharedPreferencesKeys.SP_VOLUME_INC,
                            Clementine.DefaultVolumeInc);
            mVolumeInc.setSummary(
                    getString(R.string.pref_volume_inc_summary).replace("%s", currentVolumeInc));
        }
    }
}