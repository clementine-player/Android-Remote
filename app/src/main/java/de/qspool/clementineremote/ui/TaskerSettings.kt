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
package de.qspool.clementineremote.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
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
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.ui.tasker.TaskerAction
import de.qspool.clementineremote.ui.tasker.TaskerScreen
import de.qspool.clementineremote.ui.tasker.TaskerViewModel
import de.qspool.clementineremote.ui.theme.ClementineTheme
import de.qspool.clementineremote.utils.bundle.BundleScrubber
import de.qspool.clementineremote.utils.bundle.LocalePluginIntent
import de.qspool.clementineremote.utils.bundle.PluginBundleManager

/**
 * Sets up a Tasker (Locale plug-in) action: [TaskerScreen]. Done, or back with valid settings,
 * saves the action for Tasker; back with an invalid port leaves it as it was.
 */
class TaskerSettings : ComponentActivity() {

    private val form: TaskerViewModel by viewModels()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (!form.filled) {
            fill()
            form.filled = true
        }

        setContent {
            ClementineTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
                // Test tags as resource IDs, for UI Automator.
                Surface(Modifier.semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.surface) {
                    TaskerScreen(form, onDone = ::done)
                }
            }
        }
    }

    /** The action edited, if Tasker passed one; otherwise Connect to the last Clementine. */
    private fun fill() {
        val preferences = App.getPreferences()
        form.host = preferences.getString(SharedPreferencesKeys.SP_KEY_IP, "").orEmpty()
        form.port = preferences.getString(SharedPreferencesKeys.SP_KEY_PORT, null) ?: Clementine.DefaultPort.toString()
        form.authCode = preferences.getInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, 0).toString()

        BundleScrubber.scrub(intent)
        val bundle = intent.getBundleExtra(LocalePluginIntent.EXTRA_BUNDLE)
        BundleScrubber.scrub(bundle)
        if (PluginBundleManager.isBundleValid(bundle)) {
            bundle!!
            form.action = TaskerAction.of(bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_TYPE))
            form.host = bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_IP).orEmpty()
            form.port = bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_PORT).toString()
            form.authCode = bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_AUTH).toString()
        }
    }

    /** Saves the action, unless Connect's port is invalid, which is then shown. */
    private fun done() {
        if (form.action == TaskerAction.CONNECT && form.validPort == null) {
            form.portErrors++
            return
        }
        finish()
    }

    override fun finish() {
        val port = form.validPort
        if (form.action != TaskerAction.CONNECT || port != null) {
            setResult(RESULT_OK, result(port ?: Clementine.DefaultPort))
        }
        super.finish()
    }

    /** The bundle Tasker keeps for the action, and the blurb it shows for it. */
    private fun result(port: Int): Intent {
        val auth = form.authCode.toIntOrNull() ?: 0
        val bundle = PluginBundleManager.generateBundle(applicationContext, form.action.code, form.host, port, auth)
        var blurb = getString(R.string.tasker_action) + " " + getString(form.action.label)
        if (form.action == TaskerAction.CONNECT) {
            blurb += " / ${form.host}:$port"
        }
        return Intent()
            .putExtra(LocalePluginIntent.EXTRA_BUNDLE, bundle)
            .putExtra(LocalePluginIntent.EXTRA_STRING_BLURB, blurb.take(maxBlurbLength()))
    }

    /** How long a blurb Locale allows. */
    private fun maxBlurbLength() = resources.getInteger(R.integer.twofortyfouram_locale_maximum_blurb_length)

    companion object {
        const val ACTION_CONNECT = 0
        const val ACTION_DISCONNECT = 1
        const val ACTION_PLAY = 2
        const val ACTION_PAUSE = 3
        const val ACTION_PLAYPAUSE = 4
        const val ACTION_NEXT = 5
        const val ACTION_STOP = 6
    }
}
