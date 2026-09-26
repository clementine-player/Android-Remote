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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.ui.dialogs.DownloadChooserDialog
import de.qspool.clementineremote.ui.settings.ClementineSettings
import de.qspool.clementineremote.ui.shell.AppShell
import de.qspool.clementineremote.ui.shell.Destination
import de.qspool.clementineremote.ui.shell.ShellActions
import de.qspool.clementineremote.ui.shell.ShellViewModel
import de.qspool.clementineremote.ui.theme.ClementineTheme
import de.qspool.clementineremote.utils.Utilities

/**
 * The app while connected to Clementine: the Compose shell ([AppShell]). It follows the
 * connection, and finishes when it ends, back to [ConnectActivity]; the volume keys set
 * Clementine's volume.
 */
class MainActivity : AppCompatActivity(), ShellActions {

    private val shell: ShellViewModel by viewModels()

    /** Hears the connection end while shown. */
    private val handler = Handler(Looper.getMainLooper()) { message ->
        val clementineMessage = message.obj as? ClementineMessage
        if (clementineMessage != null &&
            (clementineMessage.isErrorMessage || clementineMessage.messageType == MsgType.DISCONNECT)
        ) {
            disconnected()
        }
        true
    }

    private var toast: Toast? = null

    /** Whether to show the connect screen once disconnected, rather than leave the app. */
    private var openConnectScreen = true

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            show(intent)
        }
        setContent {
            ClementineTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
                // Test tags as resource IDs, for UI Automator.
                Surface(Modifier.semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.surface) {
                    AppShell(shell, this)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(intent)
    }

    /** A notification opens the player, or the downloads. */
    private fun show(intent: Intent) {
        if (!intent.hasExtra(ClementineMediaSessionNotification.EXTRA_NOTIFICATION_ID)) {
            return
        }
        if (intent.getIntExtra(ClementineMediaSessionNotification.EXTRA_NOTIFICATION_ID, 0) == -1) {
            shell.playerOpen = true
        } else {
            shell.playerOpen = false
            shell.destination = Destination.DOWNLOADS
        }
    }

    override fun onResume() {
        super.onResume()

        // Keep the screen on if asked to in the settings.
        if (App.getPreferences().getBoolean(SharedPreferencesKeys.SP_KEEP_SCREEN_ON, true) &&
            Utilities.isRemoteConnected()
        ) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        openConnectScreen = true
        val connection = App.ClementineConnection
        if (connection == null || App.Clementine == null || !connection.isConnected) {
            Log.d(TAG, "onResume - disconnect")
            setResult(ConnectActivity.RESULT_DISCONNECT)
            finish()
        } else {
            connection.setUiHandler(handler)
        }
    }

    override fun onPause() {
        super.onPause()
        App.ClementineConnection?.setUiHandler(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Disconnected: back to the connect screen, unless leaving.
        val connection = App.ClementineConnection
        if ((connection == null || App.Clementine == null || !connection.isConnected) && openConnectScreen) {
            Log.d(TAG, "onDestroy - disconnect")
            startActivity(
                Intent(this, ConnectActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val preferences = App.getPreferences()
        if (event.action == KeyEvent.ACTION_DOWN &&
            preferences.getBoolean(SharedPreferencesKeys.SP_KEY_USE_VOLUMEKEYS, true) &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            val step = preferences.getString(SharedPreferencesKeys.SP_VOLUME_INC, Clementine.DefaultVolumeInc)
                ?.toIntOrNull() ?: Clementine.DefaultVolumeInc.toInt()
            val current = App.Clementine.volume
            val volume = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) current + step else current - step
            RemoteRepository.send(ClementineMessageFactory.buildVolumeMessage(volume))
            makeToast(getString(R.string.playler_volume) + " " + volume.coerceIn(0, 100) + "%")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (App.getPreferences().getBoolean(SharedPreferencesKeys.SP_KEY_USE_VOLUMEKEYS, true) &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onSwitchClementine() {
        openConnectScreen = true
        RemoteRepository.send(ClementineMessage.getMessage(MsgType.DISCONNECT))
    }

    override fun onSettings() {
        startActivity(Intent(this, ClementineSettings::class.java))
    }

    override fun onDisconnect() {
        openConnectScreen = false
        RemoteRepository.send(ClementineMessage.getMessage(MsgType.DISCONNECT))
    }

    override fun onDownloadSong() {
        val song = App.Clementine.currentSong
        if (song == null) {
            Toast.makeText(this, R.string.player_nosong, Toast.LENGTH_LONG).show()
            return
        }
        if (!song.isLocal) {
            Toast.makeText(this, R.string.player_song_is_stream, Toast.LENGTH_LONG).show()
            return
        }
        val chooser = DownloadChooserDialog(this)
        chooser.setCallback { type ->
            val message = when (type) {
                DownloadChooserDialog.Type.SONG ->
                    ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.CurrentItem)
                DownloadChooserDialog.Type.ALBUM ->
                    ClementineMessageFactory.buildDownloadSongsMessage(DownloadItem.ItemAlbum)
                DownloadChooserDialog.Type.PLAYLIST -> ClementineMessageFactory.buildDownloadSongsMessage(
                    DownloadItem.APlaylist, App.Clementine.playlistManager.activePlaylistId)
            }
            DownloadManager.getInstance().addJob(message)
        }
        chooser.showDialog()
    }

    /** The connection ended: back to the connect screen, or out of the app. */
    private fun disconnected() {
        makeToast(getString(R.string.player_disconnected))
        setResult(if (openConnectScreen) ConnectActivity.RESULT_DISCONNECT else ConnectActivity.RESULT_QUIT)
        finish()
    }

    /** Shows [text], in place of the toast before. */
    private fun makeToast(text: String) {
        toast?.cancel()
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT).apply { show() }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
