package de.qspool.clementineremote.ui.connect

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.qspool.clementineremote.R
import de.qspool.clementineremote.ui.theme.ClementineBrand

/** What the user does on the connect screen. */
interface ConnectActions {

    /** Connect to the address typed in. */
    fun onConnect()

    /** Connect to a Clementine found on the network. */
    fun onServer(server: Server)

    /** Stop connecting. */
    fun onCancel()

    /** Look for Clementines on the network again. */
    fun onSearchAgain()

    /** Open the settings. */
    fun onSettings()

    /** Connect again, with the auth code Clementine asked for. */
    fun onAuthCode(code: Int)

    /** Ask Android for [permissions], once told why. */
    fun onRequestPermissions(permissions: List<String>)
}

/**
 * The connect screen: Clementine's header, the Clementines found on the network (or how to find
 * them), and the address to connect to by hand. While connecting, it shows how far along it is.
 */
@Composable
fun ConnectScreen(viewModel: ConnectViewModel, actions: ConnectActions) {
    val host by viewModel.host.collectAsStateWithLifecycle()
    val knownHosts by viewModel.knownHosts.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    ConnectContent(
        host = host,
        knownHosts = knownHosts,
        servers = servers,
        progress = progress,
        onHostChange = viewModel::setHost,
        actions = actions,
    )
    ConnectDialogs(dialog, actions, viewModel::dismissDialog)
}

@Composable
internal fun ConnectContent(
    host: String,
    knownHosts: List<String>,
    servers: List<Server>,
    @StringRes progress: Int?,
    onHostChange: (String) -> Unit,
    actions: ConnectActions,
    modifier: Modifier = Modifier,
) {
    val body = @Composable { bodyModifier: Modifier ->
        Body(host, knownHosts, servers, progress, onHostChange, actions, bodyModifier)
    }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (maxWidth > maxHeight) {
            // Landscape: the header down the left, the rest beside it.
            Row(Modifier.fillMaxSize()) {
                Header(actions::onSettings, fill = true, iconSize = 168.dp, Modifier.weight(1f).fillMaxHeight())
                body(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).statusBarsPadding())
            }
        } else {
            // Clementine as large as the redesign has it on a tall phone, smaller on a short one, so
            // the rest still fits without scrolling.
            val iconSize = (maxHeight - SPACE_BELOW_ICON).coerceIn(96.dp, 168.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Header(actions::onSettings, fill = false, iconSize, Modifier.fillMaxWidth())
                body(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit, fill: Boolean, iconSize: Dp, modifier: Modifier) {
    Column(
        modifier
            .background(ClementineBrand.gradient)
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onSettings, modifier = Modifier.testTag("btnSettings")) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.menu_settings),
                    tint = ClementineBrand.OnBrand,
                )
            }
        }
        if (fill) {
            // Filling the height, as in landscape: Clementine in the middle of it.
            Spacer(Modifier.weight(1f))
        }
        Image(
            painterResource(R.drawable.icon_large),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
        Text(
            stringResource(R.string.app_name),
            color = ClementineBrand.OnBrand,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() },
        )
        if (fill) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Body(
    host: String,
    knownHosts: List<String>,
    servers: List<Server>,
    @StringRes progress: Int?,
    onHostChange: (String) -> Unit,
    actions: ConnectActions,
    modifier: Modifier,
) {
    val connecting = progress != null
    Column(
        modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.connect_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(R.string.connect_on_network, Modifier.weight(1f))
                IconButton(
                    onClick = actions::onSearchAgain,
                    enabled = !connecting,
                    modifier = Modifier.testTag("btnSearchAgain"),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.connect_search_again),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (servers.isEmpty()) {
                    Searching()
                } else {
                    Column {
                        servers.forEachIndexed { index, server ->
                            ServerItem(server, enabled = !connecting, onClick = { actions.onServer(server) }, index)
                        }
                    }
                }
            }
        }

        Column(
            Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(R.string.connect_enter_address, Modifier)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HostField(host, knownHosts, enabled = !connecting, onHostChange, actions::onConnect, Modifier.weight(1f))
                Button(
                    onClick = actions::onConnect,
                    enabled = host.isNotBlank() && !connecting,
                    modifier = Modifier.testTag("btnConnect"),
                ) {
                    Text(stringResource(R.string.connectdialog_connect))
                }
            }
            if (progress != null) {
                Connecting(progress, actions::onCancel)
            }
        }

        Text(
            stringResource(R.string.connect_requirements, stringResource(R.string.clementine_version)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int, modifier: Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun ServerItem(server: Server, enabled: Boolean, onClick: () -> Unit, index: Int) {
    ListItem(
        headlineContent = { Text(server.name) },
        supportingContent = {
            Text(stringResource(R.string.connect_server_address, server.host, server.port))
        },
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_computer),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).testTag("server$index"),
    )
}

@Composable
private fun Searching() {
    Column(
        Modifier.padding(16.dp).testTag("searching"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.connect_searching), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            stringResource(R.string.connect_searching_help),
            style = MaterialTheme.typography.bodyMedium,
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
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = knownHosts.filter { it != host && it.contains(host, ignoreCase = true) }
    val showSuggestions = expanded && enabled && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = showSuggestions,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = {
                onHostChange(it.trim())
                expanded = true
            },
            label = { Text(stringResource(R.string.connect_address)) },
            placeholder = { Text("192.168.1.20") },
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
        ExposedDropdownMenu(expanded = showSuggestions, onDismissRequest = { expanded = false }) {
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
            TextButton(onClick = onCancel, modifier = Modifier.testTag("btnCancel")) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}

/** About what the rest of the screen needs in portrait, with the system bars. */
private val SPACE_BELOW_ICON = 640.dp
