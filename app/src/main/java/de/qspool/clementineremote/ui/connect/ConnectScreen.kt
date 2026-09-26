package de.qspool.clementineremote.ui.connect

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.qspool.clementineremote.R

/** What the user does on the connect screen. */
interface ConnectActions {

    /** Connect to the address typed in. */
    fun onConnect()

    /** Connect to a Clementine found on the network. */
    fun onServer(server: Server)

    /** Stop connecting. */
    fun onCancel()
}

/**
 * The connect screen: the Clementines found on the network, or how to find them, and the address
 * to connect to by hand. While connecting, it shows how far along it is instead.
 */
@Composable
fun ConnectScreen(viewModel: ConnectViewModel, actions: ConnectActions) {
    val host by viewModel.host.collectAsStateWithLifecycle()
    val knownHosts by viewModel.knownHosts.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    ConnectContent(
        host = host,
        knownHosts = knownHosts,
        servers = servers,
        progress = progress,
        onHostChange = viewModel::setHost,
        onConnect = actions::onConnect,
        onServer = actions::onServer,
        onCancel = actions::onCancel,
    )
}

@Composable
internal fun ConnectContent(
    host: String,
    knownHosts: List<String>,
    servers: List<Server>,
    @StringRes progress: Int?,
    onHostChange: (String) -> Unit,
    onConnect: () -> Unit,
    onServer: (Server) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panel = @Composable { panelModifier: Modifier ->
        ConnectPanel(host, knownHosts, servers, progress, onHostChange, onConnect, onServer, onCancel, panelModifier)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: Clementine beside the panel.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(Modifier.weight(1f).fillMaxHeight())
                panel(Modifier.weight(1f))
            }
        } else {
            Column {
                Logo(Modifier.weight(1f).fillMaxWidth())
                panel(Modifier)
            }
        }
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    Image(
        painterResource(R.drawable.icon_large),
        contentDescription = stringResource(R.string.cd_clementine_icon),
        contentScale = ContentScale.Fit,
        modifier = modifier.padding(32.dp),
    )
}

@Composable
private fun ConnectPanel(
    host: String,
    knownHosts: List<String>,
    servers: List<Server>,
    @StringRes progress: Int?,
    onHostChange: (String) -> Unit,
    onConnect: () -> Unit,
    onServer: (Server) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val connecting = progress != null
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.padding(16.dp),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.connectdialog_services),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (servers.isEmpty()) {
                Searching()
            } else {
                servers.forEachIndexed { index, server ->
                    ListItem(
                        headlineContent = { Text(server.name) },
                        supportingContent = { Text("${server.host}:${server.port}") },
                        leadingContent = {
                            Image(
                                painterResource(R.drawable.icon_large),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable(enabled = !connecting) { onServer(server) }
                            .padding(horizontal = 8.dp)
                            .testTag("server$index"),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HostField(host, knownHosts, enabled = !connecting, onHostChange, onConnect)
                if (progress == null) {
                    Button(
                        onClick = onConnect,
                        enabled = host.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("btnConnect"),
                    ) {
                        Text(stringResource(R.string.connectdialog_connect))
                    }
                } else {
                    Connecting(progress, onCancel)
                }
            }
        }
    }
}

@Composable
private fun Searching() {
    Column(
        Modifier.padding(horizontal = 24.dp).testTag("searching"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.connect_searching), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            stringResource(R.string.connect_searching_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The address to connect to, suggesting the ones connected to before. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostField(
    host: String,
    knownHosts: List<String>,
    enabled: Boolean,
    onHostChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = knownHosts.filter { it != host && it.contains(host, ignoreCase = true) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = {
                onHostChange(it.trim())
                expanded = true
            },
            label = { Text(stringResource(R.string.connectdialog_ip_hint)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = {
                expanded = false
                if (host.isNotBlank()) {
                    onConnect()
                }
            }),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .testTag("etIp"),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onHostChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Connecting(@StringRes progress: Int, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("connecting")) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(progress),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = onCancel, modifier = Modifier.testTag("btnCancel")) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}
