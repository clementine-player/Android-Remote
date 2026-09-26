package de.qspool.clementineremote.ui.tasker

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import de.qspool.clementineremote.R
import de.qspool.clementineremote.ui.TaskerSettings

/** What a Tasker (Locale plug-in) action does, as the plug-in bundle stores it. */
enum class TaskerAction(val code: Int, @StringRes val label: Int) {
    CONNECT(TaskerSettings.ACTION_CONNECT, R.string.tasker_connect),
    DISCONNECT(TaskerSettings.ACTION_DISCONNECT, R.string.tasker_disconnect),
    PLAY(TaskerSettings.ACTION_PLAY, R.string.tasker_play),
    PAUSE(TaskerSettings.ACTION_PAUSE, R.string.tasker_pause),
    PLAY_PAUSE(TaskerSettings.ACTION_PLAYPAUSE, R.string.tasker_playpause),
    STOP(TaskerSettings.ACTION_STOP, R.string.tasker_stop),
    NEXT(TaskerSettings.ACTION_NEXT, R.string.tasker_next),
    ;

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: CONNECT
    }
}

/** The action being set up: kept over a rotation, and read when it's saved. */
class TaskerViewModel : ViewModel() {
    /** Whether it has been filled in, from the action edited or the last connection. */
    var filled = false

    var action by mutableStateOf(TaskerAction.CONNECT)
    var host by mutableStateOf("")
    var port by mutableStateOf("")
    var authCode by mutableStateOf("")

    /** Bumped when saving finds the port invalid, to show why. */
    var portErrors by mutableIntStateOf(0)

    /** The port, if Clementine can listen on it. */
    val validPort: Int? get() = port.toIntOrNull()?.takeIf { it in 1024..65535 }
}

/** The Tasker action's settings: what to do, and for Connect, where to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskerScreen(form: TaskerViewModel, onDone: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onDone, modifier = Modifier.testTag("btnTaskerDone")) {
                        Text(stringResource(R.string.done))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tasker_intro), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                TaskerAction.entries.forEach { action ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(form.action == action, role = Role.RadioButton) { form.action = action }
                            .testTag("tasker_${action.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = form.action == action, onClick = null)
                        Text(stringResource(action.label), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text(
                stringResource(R.string.tasker_ip_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            val connecting = form.action == TaskerAction.CONNECT
            OutlinedTextField(
                form.host,
                onValueChange = { form.host = it.trim() },
                label = { Text(stringResource(R.string.connectdialog_ip_hint)) },
                singleLine = true,
                enabled = connecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("taskerIp"),
            )
            val portError = form.portErrors > 0 && form.validPort == null
            OutlinedTextField(
                form.port,
                onValueChange = { form.port = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.tasker_port_hint)) },
                singleLine = true,
                enabled = connecting,
                isError = portError,
                supportingText = if (portError) ({ Text(stringResource(R.string.pref_port_error)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("taskerPort"),
            )
            OutlinedTextField(
                form.authCode,
                onValueChange = { form.authCode = it.filter(Char::isDigit).take(9) },
                label = { Text(stringResource(R.string.tasker_auth_hint)) },
                singleLine = true,
                enabled = connecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("taskerAuth"),
            )
        }
    }
}
