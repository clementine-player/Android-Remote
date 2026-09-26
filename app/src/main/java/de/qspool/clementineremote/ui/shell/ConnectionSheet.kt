package de.qspool.clementineremote.ui.shell

import android.content.Context
import android.net.TrafficStats
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.utils.Utilities
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

/** The Clementine connected to, and how the connection is doing. */
internal data class ConnectionStats(
    /** Clementine's computer. */
    val host: String,
    val ip: String,
    val port: Int,
    val version: String,
    /** How long the connection has been open, as hours, minutes and seconds. */
    val uptime: String,
    /** Sent and received since connecting, and the average rate; null where Android can't say. */
    val traffic: String?,
)

/** What the connection sheet's items do. */
interface ConnectionActions {
    /** Disconnects, to connect to another Clementine. */
    fun onSwitchClementine()

    fun onSettings()

    /** Disconnects and leaves the app. */
    fun onDisconnect()
}

/**
 * The connection, in a sheet the connection chip opens: the Clementine connected to, its
 * address and version, how long and how much has been sent, and switching to another
 * Clementine, the settings and disconnecting.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ConnectionSheet(actions: ConnectionActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // The uptime and traffic change by themselves, so they're read twice a second.
    val stats by produceState(readConnectionStats(context)) {
        while (true) {
            delay(STATS_INTERVAL_MILLIS)
            value = readConnectionStats(context)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The sheet is a window of its own: its test tags are resource IDs too, for UI Automator.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        ConnectionSheetContent(stats, object : ConnectionActions {
            override fun onSwitchClementine() {
                onDismiss()
                actions.onSwitchClementine()
            }

            override fun onSettings() {
                onDismiss()
                actions.onSettings()
            }

            override fun onDisconnect() {
                onDismiss()
                actions.onDisconnect()
            }
        })
    }
}

@Composable
internal fun ConnectionSheetContent(stats: ConnectionStats, actions: ConnectionActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(
            Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_computer), null, Modifier.size(28.dp))
            }
            Column {
                Text(
                    stringResource(R.string.navigation_drawer_clementine_on, stats.host),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }.testTag("cnHost"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_check_circle),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.connection_connected),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 24.dp)) {
            Fact(R.string.connect_address, stringResource(R.string.connect_server_address, stats.ip, stats.port), "cnAddress")
            if (stats.version.isNotBlank()) {
                Fact(R.string.connection_version, stats.version, "cnVersion")
            }
            if (stats.uptime.isNotBlank()) {
                Fact(R.string.connection_uptime, stats.uptime, "cnTime")
            }
            Fact(
                R.string.connection_traffic,
                stats.traffic ?: stringResource(R.string.connection_traffic_unsupported),
                "cnTraffic",
            )
        }

        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Item(R.drawable.ic_computer, R.string.connection_switch, R.string.connection_switch_summary, "btnSwitch", actions::onSwitchClementine)
        Item(R.drawable.ic_settings, R.string.menu_settings, null, "btnSettings", actions::onSettings)
        Item(R.drawable.ic_logout, R.string.tasker_disconnect, null, "btnDisconnect", actions::onDisconnect)
    }
}

@Composable
private fun Fact(label: Int, value: String, tag: String) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(136.dp),
            )
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).testTag(tag))
        }
    }
}

@Composable
private fun Item(icon: Int, title: Int, summary: Int?, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
        leadingContent = { Icon(painterResource(icon), null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).testTag(tag),
    )
}

internal fun readConnectionStats(context: Context): ConnectionStats {
    val preferences = App.getPreferences()
    val ip = preferences.getString(SharedPreferencesKeys.SP_KEY_IP, "").orEmpty()
    val port = preferences.getString(SharedPreferencesKeys.SP_KEY_PORT, "")?.toIntOrNull() ?: 0
    val host = App.Clementine.hostname?.takeIf { it.isNotBlank() } ?: ip.ifBlank { context.getString(R.string.fragment_title_connection) }
    val version = App.Clementine.version ?: ""
    val connection = App.ClementineConnection
        ?: return ConnectionStats(host, ip, port, version, uptime = "", traffic = null)

    val millis = System.currentTimeMillis() - connection.startTime
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val uptime = String.format(
        Locale.US, "%02d:%02d:%02d",
        TimeUnit.MILLISECONDS.toHours(millis),
        TimeUnit.MILLISECONDS.toMinutes(millis) % 60,
        seconds % 60,
    )

    val uid = context.applicationInfo.uid
    val received = TrafficStats.getUidRxBytes(uid)
    val traffic = if (received == TrafficStats.UNSUPPORTED.toLong()) {
        null
    } else {
        val tx = TrafficStats.getUidTxBytes(uid) - connection.startTx
        val rx = received - connection.startRx
        val perSecond = if (seconds > 0) (tx + rx) / seconds else 0
        Utilities.humanReadableBytes(tx, true) + " / " + Utilities.humanReadableBytes(rx, true) +
            " (" + Utilities.humanReadableBytes(perSecond, true) + "/s)"
    }
    return ConnectionStats(host, ip, port, version, uptime, traffic)
}

private const val STATS_INTERVAL_MILLIS = 500L
