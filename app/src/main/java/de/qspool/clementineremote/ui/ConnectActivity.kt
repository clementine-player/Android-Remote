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

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementineService
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.downloader.DownloadManager
import de.qspool.clementineremote.backend.mdns.ClementineMDnsDiscovery
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect
import de.qspool.clementineremote.ui.connect.ConnectActions
import de.qspool.clementineremote.ui.connect.ConnectDialog
import de.qspool.clementineremote.ui.connect.ConnectScreen
import de.qspool.clementineremote.ui.connect.ConnectViewModel
import de.qspool.clementineremote.ui.connect.Server
import de.qspool.clementineremote.ui.settings.ClementineSettings
import de.qspool.clementineremote.ui.theme.ClementineTheme
import de.qspool.clementineremote.utils.RetryWait
import de.qspool.clementineremote.utils.Utilities
import java.net.InetAddress

/**
 * The connect screen, drawn in Compose ([ConnectScreen]). This activity finds Clementines on the
 * network, connects to the one picked, and opens the player once connected.
 */
class ConnectActivity : ComponentActivity(), ConnectActions {

    private lateinit var preferences: SharedPreferences

    private val handler = ConnectActivityHandler(this)

    private val state: ConnectViewModel by viewModels()

    private var authCode = 0

    private var discovery: ClementineMDnsDiscovery? = null

    private var doAutoConnect = true

    /**
     * While auto-connecting, the network name of the Clementine being connected to, to look for
     * on the network if its saved address doesn't work.
     */
    private var autoConnectName: String? = null

    /** Whether connecting was canceled: what the attempt says afterwards isn't shown. */
    private var canceled = false


    /** The network name of the Clementine being connected to, if it was picked from the network. */
    private var serverName: String? = null

    private lateinit var knownIps: MutableSet<String>

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Drawn wholly in Compose, which keeps clear of the system bars and the keyboard itself.
        // The status bar sits on Clementine's gradient, so its icons are light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        preferences = App.getPreferences()
        // A copy: the set the preferences return must not be changed.
        knownIps = LinkedHashSet(preferences.getStringSet(SharedPreferencesKeys.SP_KNOWN_IP, null).orEmpty())

        if (savedInstanceState == null) {
            state.setHost(preferences.getString(SharedPreferencesKeys.SP_KEY_IP, "").orEmpty())
        }
        state.setKnownHosts(knownIps)

        // The last auth code.
        authCode = preferences.getInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, 0)

        setContent {
            ClementineTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
                // Test tags as resource IDs, for UI Automator.
                Surface(Modifier.semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.surface) {
                    ConnectScreen(state, this)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Connected already: open the player.
        if (!state.isConnecting && App.ClementineConnection?.isConnected == true) {
            showPlayerDialog()
            return
        }

        // Discovery is multicast on the network this phone is on, which Android 17 only allows
        // with the local network permission. Auto-connecting isn't: a saved address may be one
        // Android doesn't count as local, over a VPN say, and it's for Android to refuse it.
        if (hasLocalNetwork()) {
            // mDNS discovery runs even when auto-connecting, so that if the saved address no
            // longer works, the Clementines on the network are there to pick from, and the last
            // one can be found there by name.
            discovery = ClementineMDnsDiscovery(handler).also { it.discoverServices() }
        }
        state.setSearching(hasLocalNetwork())

        if (preferences.getBoolean(SharedPreferencesKeys.SP_KEY_AC, true) && doAutoConnect) {
            // Delayed, so the service has time to start.
            handler.postDelayed({ autoConnect() }, AUTO_CONNECT_DELAY_MILLIS)
        }
        doAutoConnect = true

        // Remove notifications still shown.
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.cancel(ClementineMediaSessionNotification.NOTIFIFCATION_ID)
        notifications.cancel(DownloadManager.NOTIFICATION_ID_DOWNLOADS)
        notifications.cancel(DownloadManager.NOTIFICATION_ID_DOWNLOADS_FINISHED)
    }

    override fun onPause() {
        super.onPause()
        discovery?.stopServiceDiscovery()
    }

    override fun onPostResume() {
        super.onPostResume()

        // Once per install: Android stops showing its prompt after a refusal, so asking again
        // only denies it again. After that the connect screen says what's missing instead.
        val missing = missingPermissions()
        if (missing.isNotEmpty() && !preferences.getBoolean(SharedPreferencesKeys.SP_PERMISSIONS_ASKED, false)) {
            state.showDialog(ConnectDialog.Permissions(missing.toList()))
        }
    }

    /** The runtime permissions the app uses that have not been granted yet. */
    fun missingPermissions(): Array<String> {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                // Finding and talking to Clementine at all.
                add(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
            // Lowers Clementine's volume during calls.
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                // Downloads to folders outside the app's own; not needed from Android 10.
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // The player controls and download progress notifications.
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    // Only Android 17 and later have the local network permission among the results.
    @SuppressLint("InlinedApi")
    private val permissionRequest = registerForActivityResult(RequestMultiplePermissions()) { results ->
        val localNetwork = results[Manifest.permission.ACCESS_LOCAL_NETWORK] ?: return@registerForActivityResult
        // onResume comes next either way, and starts looking on the network if it can.
        state.setSearching(localNetwork)
    }

    override fun onRequestPermissions(permissions: List<String>) {
        preferences.edit { putBoolean(SharedPreferencesKeys.SP_PERMISSIONS_ASKED, true) }
        permissionRequest.launch(permissions.toTypedArray())
    }

    override fun onOpenAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
        )
    }

    /**
     * Android 17 lets apps targeting it reach the local network, Clementine included, only with
     * the local network permission. Before, the INTERNET permission covers it.
     */
    private fun hasLocalNetwork(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Whether Android refused to reach [host] for want of the local network permission: it only
     * stops the app reaching the network this phone is on, so an address beyond it, over a VPN
     * say, needs nothing. Only addresses written as numbers are checked, which keeps a name's
     * lookup off this thread; a name that did need the permission fails as it did before.
     */
    private fun isLocalNetworkBlocked(host: String): Boolean {
        if (hasLocalNetwork() || !InetAddresses.isNumericAddress(host)) {
            return false
        }
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val address = InetAddress.getByName(host)
        return connectivity.getLinkProperties(network)?.routes.orEmpty().any {
            !it.hasGateway() && it.matches(address)
        }
    }

    /**
     * Asks for the local network permission, which Android asks for again or refuses at once.
     * Only on Android 17 and later, where [hasLocalNetwork] can be false.
     */
    @SuppressLint("InlinedApi")
    private fun requestLocalNetwork() {
        onRequestPermissions(listOf(Manifest.permission.ACCESS_LOCAL_NETWORK))
    }

    override fun onConnect() {
        serverName = null
        connect()
    }

    override fun onServer(server: Server) {
        state.setHost(server.host)
        serverName = server.name
        preferences.edit { putString(SharedPreferencesKeys.SP_KEY_PORT, server.port.toString()) }
        connect()
    }

    override fun onSearchAgain() {
        if (!hasLocalNetwork()) {
            requestLocalNetwork()
            return
        }
        discovery?.stopServiceDiscovery()
        state.setServers(emptyList())
        discovery = ClementineMDnsDiscovery(handler).also { it.discoverServices() }
    }

    override fun onSettings() {
        autoConnectName = null
        startActivity(Intent(this, ClementineSettings::class.java))
        doAutoConnect = false
    }

    override fun onCancel() {
        autoConnectName = null
        canceled = true
        // An attempt still under way blocks the connection thread until it times out: give up on
        // it now. Once connected, disconnecting does it.
        App.ClementineConnection?.abortConnecting()
        RemoteRepository.send(ClementineMessage.getMessage(MsgType.DISCONNECT))
        state.hideProgress()
    }

    override fun onAuthCode(code: Int) {
        authCode = code
        connect()
    }

    /** Shows how far connecting has got. */
    fun showProgress(@StringRes progress: Int) {
        if (!canceled) {
            state.showProgress(progress)
        }
    }

    /** Connecting has ended, whether connected or not. */
    fun connectionEnded() = state.hideProgress()

    /** Connects to the address typed in. */
    private fun connect() {
        // Not once the activity has finished.
        if (isFinishing) {
            return
        }

        autoConnectName = null
        canceled = false
        val ip = state.host.value
        knownIps.add(ip)
        state.setKnownHosts(knownIps)
        preferences.edit {
            putString(SharedPreferencesKeys.SP_KEY_IP, ip)
            putString(SharedPreferencesKeys.SP_KEY_NAME, serverName.orEmpty())
            putInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, authCode)
            putStringSet(SharedPreferencesKeys.SP_KNOWN_IP, knownIps)
        }

        state.showProgress(R.string.connectdialog_connecting)

        // Started, so it isn't stopped on unbindService.
        val serviceIntent = Intent(this, ClementineService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val clementine = (service as ClementineService.ClementineServiceBinder).clementineService
                clementine.setUiHandler(handler)
                clementine.handleServiceAction(
                    Intent(this@ConnectActivity, ClementineService::class.java)
                        .putExtra(ClementineService.SERVICE_ID, ClementineService.SERVICE_START)
                        .putExtra(ClementineService.EXTRA_STRING_IP, ip)
                        .putExtra(ClementineService.EXTRA_INT_PORT, port())
                        .putExtra(ClementineService.EXTRA_INT_AUTH, authCode),
                )
                unbindService(this)
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }, BIND_AUTO_CREATE)
    }

    private fun port(): Int =
        preferences.getString(SharedPreferencesKeys.SP_KEY_PORT, null)?.toIntOrNull() ?: Clementine.DefaultPort

    /** Connected to Clementine: open the player. */
    fun showPlayerDialog() {
        autoConnectName = null
        discovery?.stopServiceDiscovery()
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, MainActivity::class.java).setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION,
            ),
            ID_PLAYER_DIALOG,
        )
    }

    /** Couldn't connect to Clementine: say what's likely wrong. */
    fun noConnection() {
        if (isFinishing || canceled) {
            return
        }
        if (autoConnectName != null) {
            // Not at its saved address: look for it on the network by name instead. Delayed, so
            // the service has time to stop.
            handler.postDelayed({ autoConnect(state.servers.value) }, AUTO_CONNECT_DELAY_MILLIS)
            return
        }
        @Suppress("DEPRECATION")
        val ip = applicationContext.getSystemService(WifiManager::class.java).connectionInfo.ipAddress
        val message = when {
            // Android drops this silently, so nothing else says why the attempt went nowhere.
            isLocalNetworkBlocked(state.host.value) -> getString(R.string.local_network_on_link)
            !Utilities.onWifi() -> getString(R.string.wifi_disabled)
            !Utilities.ToInetAddress(ip).isSiteLocalAddress -> getString(R.string.no_private_ip)
            else -> getString(R.string.check_ip, getString(R.string.clementine_version))
        }
        state.showDialog(ConnectDialog.Message(getString(R.string.connectdialog_error), message))
    }

    /** Clementine speaks an older protocol: it needs updating. */
    fun oldProtoVersion() {
        autoConnectName = null
        state.showDialog(
            ConnectDialog.Message(
                getString(R.string.error_versions),
                getString(R.string.old_proto, getString(R.string.clementine_version)),
            ),
        )
    }

    /** Clementine closed the connection; if for the auth code, ask for it. */
    fun disconnected(clementineMessage: ClementineMessage) {
        // Restart the background service.
        startService(
            Intent(this, ClementineService::class.java)
                .putExtra(ClementineService.SERVICE_ID, ClementineService.SERVICE_START),
        )

        if (!clementineMessage.isErrorMessage) {
            // Clementine answered, so it was found.
            autoConnectName = null
            val reason = clementineMessage.message.responseDisconnect.reasonDisconnect
            if (reason == ReasonDisconnect.Wrong_Auth_Code || reason == ReasonDisconnect.Not_Authenticated) {
                state.showDialog(ConnectDialog.AuthCode)
            } else if (reason == ReasonDisconnect.Not_Local_Network) {
                state.showDialog(
                    ConnectDialog.Message(getString(R.string.not_local_network_title), getString(R.string.not_local_network)),
                )
            } else if (reason == ReasonDisconnect.Too_Many_Wrong_Auth_Codes) {
                // Not the auth code dialog: Clementine wouldn't check a code yet.
                val wait = RetryWait.describe(resources, clementineMessage.message.responseDisconnect)
                state.showDialog(
                    ConnectDialog.Message(
                        getString(R.string.too_many_wrong_auth_codes_title),
                        getString(R.string.too_many_wrong_auth_codes, wait),
                    ),
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ID_PLAYER_DIALOG) {
            if (resultCode == RESULT_CANCELED || resultCode == RESULT_QUIT) {
                finish()
            } else {
                doAutoConnect = false
            }
        }
    }

    /** The Clementines found on the network changed. */
    fun serviceFound() {
        val servers = discovery?.services.orEmpty().mapNotNull { service ->
            service.inet4Addresses.firstOrNull()?.let { Server(service.name, it.hostAddress.orEmpty(), service.port) }
        }
        state.setServers(servers)
        autoConnect(servers)
    }

    /**
     * With auto-connect on, connects to the Clementine last connected to at its saved address, as
     * that's quickest when the address hasn't changed. If it can't be reached there and it was
     * picked from the network, it's looked for there by name instead.
     */
    private fun autoConnect() {
        val ip = preferences.getString(SharedPreferencesKeys.SP_KEY_IP, null)
        if (isFinishing || ip.isNullOrEmpty() || state.isConnecting || App.ClementineConnection?.isConnected == true) {
            return
        }
        val name = preferences.getString(SharedPreferencesKeys.SP_KEY_NAME, null)?.ifEmpty { null }
        state.setHost(ip)
        serverName = name
        connect()
        autoConnectName = name
    }

    /**
     * While auto-connecting, connects to the last Clementine at its new address if it's among
     * [servers]. If its saved address is still being tried, gives up on that rather than waiting
     * for it to time out; [noConnection] then connects to the new address.
     */
    private fun autoConnect(servers: List<Server>) {
        val name = autoConnectName ?: return
        val server = servers.firstOrNull { it.name == name } ?: return
        if (isFinishing || App.ClementineConnection?.isConnected == true) {
            return
        }
        if (state.isConnecting) {
            if (server.host != state.host.value) {
                App.ClementineConnection?.abortConnecting()
            }
            return
        }
        onServer(server)
    }

    companion object {
        const val RESULT_DISCONNECT = 1
        const val RESULT_QUIT = 2
        private const val ID_PLAYER_DIALOG = 1
        private const val AUTO_CONNECT_DELAY_MILLIS = 250L
    }
}
