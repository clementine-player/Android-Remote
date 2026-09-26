package de.qspool.clementineremote.ui.connect

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A Clementine found on the network. */
data class Server(val name: String, val host: String, val port: Int)

/**
 * The connect screen's state. [de.qspool.clementineremote.ui.ConnectActivity] still does the
 * connecting and discovery, and sets this state as they go.
 */
class ConnectViewModel : ViewModel() {

    private val _host = MutableStateFlow("")

    /** The address typed in. */
    val host: StateFlow<String> = _host.asStateFlow()

    private val _knownHosts = MutableStateFlow<List<String>>(emptyList())

    /** Addresses connected to before, to suggest. */
    val knownHosts: StateFlow<List<String>> = _knownHosts.asStateFlow()

    private val _servers = MutableStateFlow<List<Server>>(emptyList())

    /** The Clementines found on the network. */
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _progress = MutableStateFlow<Int?>(null)

    /** While connecting, what's happening; null otherwise. */
    val progress: StateFlow<Int?> = _progress.asStateFlow()

    /** Dialogs waiting to be shown after the one showing. */
    private val queued = ArrayDeque<ConnectDialog>()

    private val _dialog = MutableStateFlow<ConnectDialog?>(null)

    /** The dialog shown over the screen, if any. */
    val dialog: StateFlow<ConnectDialog?> = _dialog.asStateFlow()

    /** Shows [dialog] once any shown or waiting are dismissed, unless it's among them already. */
    fun showDialog(dialog: ConnectDialog) {
        when {
            _dialog.value == null -> _dialog.value = dialog
            _dialog.value != dialog && dialog !in queued -> queued.addLast(dialog)
        }
    }

    /** Closes the dialog shown, for the next one waiting. */
    fun dismissDialog() {
        _dialog.value = queued.removeFirstOrNull()
    }

    fun setHost(host: String) {
        _host.value = host
    }

    fun setKnownHosts(hosts: Collection<String>) {
        _knownHosts.value = hosts.filter { it.isNotBlank() }
    }

    fun setServers(servers: List<Server>) {
        _servers.value = servers
    }

    /** Shows that connecting is under way, and what it's doing. */
    fun showProgress(@StringRes progress: Int) {
        _progress.value = progress
    }

    /** Connecting has ended, one way or another. */
    fun hideProgress() {
        _progress.value = null
    }

    val isConnecting: Boolean
        get() = _progress.value != null
}
