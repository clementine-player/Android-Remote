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
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.io.File;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.ui.dialogs.FileDialog;

public class PreferencesBehaviorDownloads extends PreferenceFragment {

    private DefaultDirChooser mDefaultDirChooser;

    private FileDialog mFileDialog;

    private Preference mDownloadDir;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_downloads);

        mDownloadDir = getPreferenceScreen()
                .findPreference(SharedPreferencesKeys.SP_DOWNLOAD_DIR);

        mDownloadDir.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mDefaultDirChooser.showAvailableDirectories();
                return true;
            }
        });

        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());

        // Create dialog
        String defaultPath = getActivity().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                .getAbsolutePath();
        String path = sharedPreferences
                .getString(SharedPreferencesKeys.SP_DOWNLOAD_DIR, defaultPath);
        File mPath = new File(path);
        mFileDialog = new FileDialog(getActivity(), mPath);
        mFileDialog.setCheckIfWritable(true);
        mFileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
            public void directorySelected(File directory) {
                SharedPreferences.Editor editor = getPreferenceScreen().getSharedPreferences()
                        .edit();
                editor.putString(SharedPreferencesKeys.SP_DOWNLOAD_DIR,
                        directory.getAbsolutePath());
                editor.commit();
                mDownloadDir.setSummary(directory.getAbsolutePath());
            }
        });
        mFileDialog.setSelectDirectoryOption(true);
        mDownloadDir.setSummary(path);

        mDefaultDirChooser = new DefaultDirChooser(getActivity());
        mDefaultDirChooser.addDirectoryListener(new DefaultDirChooser.DirectorySelectedListener() {
            @Override
            public void directorySelected(String dir) {
                if (dir.startsWith("/")) {
                    SharedPreferences.Editor editor = getPreferenceScreen().getSharedPreferences()
                            .edit();
                    editor.putString(SharedPreferencesKeys.SP_DOWNLOAD_DIR, dir);
                    editor.commit();
                    mDownloadDir.setSummary(dir);
                } else {
                    if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                        mFileDialog.showDialog();
                    } else {
                        Toast.makeText(getActivity(), R.string.download_noti_not_mounted,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }
}
