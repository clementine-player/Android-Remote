package de.qspool.clementineremote.ui.player

import android.content.Context
import android.net.TrafficStats
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.utils.Utilities
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** How the connection to Clementine is doing. */
internal data class ConnectionStats(
    /** Clementine's host and port. */
    val address: String,
    val version: String,
    /** How long the connection has been open, as hours, minutes and seconds. */
    val uptime: String,
    /** Sent and received since connecting, and the average rate; null where Android can't say. */
    val traffic: String?,
)

/** The connection page: where Clementine is, since when, its version, traffic and volume. */
@Composable
fun ConnectionInfo(viewModel: PlayerViewModel = viewModel()) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The uptime and traffic change by themselves, so they're read twice a second.
    val stats by produceState(readConnectionStats(context)) {
        while (true) {
            delay(STATS_INTERVAL_MILLIS)
            value = readConnectionStats(context)
        }
    }
    ConnectionInfoContent(stats, nowPlaying.volume, onVolume = viewModel::setVolume)
}

@Composable
internal fun ConnectionInfoContent(
    stats: ConnectionStats,
    volume: Int,
    onVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.icon_large),
                contentDescription = stringResource(R.string.cd_clementine_icon),
                modifier = Modifier.size(64.dp),
            )
            Column {
                Text(
                    stringResource(R.string.connection_connected_to),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stats.address,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("cnAddress"),
                )
                Text(
                    stats.uptime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("cnTime"),
                )
            }
        }
        HorizontalDivider()

        if (stats.version.isNotBlank()) {
            Info(R.string.connection_version, stats.version, "cnVersion")
        }
        Info(
            R.string.connection_traffic,
            stats.traffic ?: stringResource(R.string.connection_traffic_unsupported),
            "cnTraffic",
        )

        Column {
            Text(
                stringResource(R.string.connection_volume),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // While dragging, the thumb follows the finger, and Clementine follows the thumb.
            var dragging by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = dragging ?: volume.toFloat(),
                onValueChange = {
                    if (it.roundToInt() != (dragging ?: volume.toFloat()).roundToInt()) {
                        onVolume(it.roundToInt())
                    }
                    dragging = it
                },
                onValueChangeFinished = { dragging = null },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth().testTag("cnVolume"),
            )
        }
    }
}

@Composable
private fun Info(label: Int, value: String, tag: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
    }
}

internal fun readConnectionStats(context: Context): ConnectionStats {
    val preferences = App.getPreferences()
    val address = preferences.getString(SharedPreferencesKeys.SP_KEY_IP, "") + ":" +
        preferences.getString(SharedPreferencesKeys.SP_KEY_PORT, "")
    val version = App.Clementine.version ?: ""
    val connection = App.ClementineConnection
        ?: return ConnectionStats(address, version, uptime = "", traffic = null)

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
    return ConnectionStats(address, version, uptime, traffic)
}

private const val STATS_INTERVAL_MILLIS = 500L
