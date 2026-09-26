package de.qspool.clementineremote.ui.connect

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
import de.qspool.clementineremote.R

/** A dialog over the connect screen. */
sealed interface ConnectDialog {

    /** Something to tell the user, such as why connecting failed; [html] if [text] is marked up. */
    data class Message(val title: String, val text: String, val html: Boolean = false) : ConnectDialog

    /** Clementine wants the auth code shown in its network remote settings. */
    data object AuthCode : ConnectDialog

    /** Why the app asks for [permissions], before Android asks. */
    data class Permissions(val permissions: List<String>) : ConnectDialog
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
            text = { Text(stringResource(R.string.permissions_required_text)) },
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
    }
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
