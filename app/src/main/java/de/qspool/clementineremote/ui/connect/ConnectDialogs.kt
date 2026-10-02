package de.qspool.clementineremote.ui.connect

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R

/** A dialog over the connect screen. */
sealed interface ConnectDialog {

    /** Something to tell the user, such as why connecting failed; [html] if [text] is marked up. */
    data class Message(val title: String, val text: String, val html: Boolean = false) : ConnectDialog

    /** Clementine wants the auth code shown in its network remote settings. */
    data object AuthCode : ConnectDialog

    /** Why the app asks for [permissions], before Android asks. */
    data class Permissions(val permissions: List<String>) : ConnectDialog

    /**
     * Android 17's local network permission was refused, without which the app can't reach
     * Clementine: it can be allowed in the app's settings.
     */
    data object LocalNetworkDenied : ConnectDialog
}

/** Shows [dialog]; [onDismiss] closes it. */
@Composable
internal fun ConnectDialogs(dialog: ConnectDialog?, actions: ConnectActions, onDismiss: () -> Unit) {
    when (dialog) {
        null -> {}
        is ConnectDialog.Message -> MessageDialog(dialog, onDismiss)
        ConnectDialog.AuthCode -> AuthCodeDialog(onDismiss) {
            onDismiss()
            actions.onAuthCode(it)
        }
        is ConnectDialog.Permissions -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.permissions_required_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).testTag("permissionsText"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.permissions_intro))
                    dialog.permissions.mapNotNull(::permissionReason).forEach { Text(stringResource(it)) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        actions.onRequestPermissions(dialog.permissions)
                    },
                    modifier = Modifier.testTag("btnPermissionsContinue"),
                ) { Text(stringResource(R.string.dialog_continue)) }
            },
        )
        ConnectDialog.LocalNetworkDenied -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.local_network_denied_title)) },
            text = { Text(stringResource(R.string.local_network_denied_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        actions.onOpenAppSettings()
                    },
                    modifier = Modifier.testTag("btnOpenSettings"),
                ) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
            },
        )
    }
}

/** What [permission] is for, in the words the app asks for it with. */
private fun permissionReason(permission: String): Int? = when (permission) {
    Manifest.permission.ACCESS_LOCAL_NETWORK -> R.string.permission_local_network
    Manifest.permission.POST_NOTIFICATIONS -> R.string.permission_notifications
    Manifest.permission.READ_PHONE_STATE -> R.string.permission_phone
    Manifest.permission.WRITE_EXTERNAL_STORAGE -> R.string.permission_storage
    else -> null
}

@Composable
private fun MessageDialog(message: ConnectDialog.Message, onDismiss: () -> Unit) {
    val links = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(message.title) },
        text = {
            Text(
                if (message.html) AnnotatedString.fromHtml(message.text, links) else AnnotatedString(message.text),
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("messageText"),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("btnMessageClose")) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
}

/** Asks for the auth code; OK only once it's a number. */
@Composable
private fun AuthCodeDialog(onDismiss: () -> Unit, onCode: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val code = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.input_auth_code)) },
        text = {
            OutlinedTextField(
                text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_CODE_DIGITS) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.testTag("authCodeField"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { code?.let(onCode) },
                enabled = code != null,
                modifier = Modifier.testTag("btnAuthCodeOk"),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** As many digits as always fit an Int, which the protocol sends the code as. */
private const val MAX_CODE_DIGITS = 9
