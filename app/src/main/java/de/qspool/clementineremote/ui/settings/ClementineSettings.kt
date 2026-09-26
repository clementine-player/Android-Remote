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
package de.qspool.clementineremote.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.ui.dialogs.FileDialog
import de.qspool.clementineremote.ui.theme.ClementineTheme
import de.qspool.clementineremote.utils.Utilities
import java.io.File

/** The settings screen of Clementine Remote: [SettingsScreen], over the app's preferences. */
class ClementineSettings : AppCompatActivity(), SettingsActions {

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Keep the screen on if asked to in the settings.
        if (App.getPreferences().getBoolean(SharedPreferencesKeys.SP_KEEP_SCREEN_ON, true) &&
            Utilities.isRemoteConnected()
        ) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent {
            ClementineTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
                // Test tags as resource IDs, for UI Automator.
                Surface(Modifier.semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.surface) {
                    SettingsScreen(rememberPreferenceStore(App.getPreferences()), this, ::defaultDownloadDir)
                }
            }
        }
    }

    override fun onBack() = finish()

    override fun onOpenUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_not_available, Toast.LENGTH_LONG).show()
        }
    }

    private fun defaultDownloadDir(): String =
        getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath ?: ""

    private fun setDownloadDir(dir: String) {
        App.getPreferences().edit().putString(SharedPreferencesKeys.SP_DOWNLOAD_DIR, dir).apply()
    }

    /** A folder of the app's, the public Music folder, or one browsed to. */
    override fun onChooseDownloadDir() {
        val chooser = DefaultDirChooser(this)
        chooser.addDirectoryListener { dir ->
            if (dir.startsWith("/")) {
                setDownloadDir(dir)
            } else if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                val current = App.getPreferences().getString(SharedPreferencesKeys.SP_DOWNLOAD_DIR, defaultDownloadDir())
                FileDialog(this, File(current ?: defaultDownloadDir())).apply {
                    setCheckIfWritable(true)
                    setSelectDirectoryOption(true)
                    addDirectoryListener { directory -> setDownloadDir(directory.absolutePath) }
                    showDialog()
                }
            } else {
                Toast.makeText(this, R.string.download_noti_not_mounted, Toast.LENGTH_SHORT).show()
            }
        }
        chooser.showAvailableDirectories()
    }
}
