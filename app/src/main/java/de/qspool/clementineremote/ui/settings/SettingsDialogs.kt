package de.qspool.clementineremote.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R

/** Who made the remote. */
@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val links = linkStyles()
    TextDialog(stringResource(R.string.pref_about_title), "aboutDialog", onDismiss) {
        Credit(stringResource(R.string.dialog_about_authors), AnnotatedString("Andreas Muttscheller"))
        Credit(
            stringResource(R.string.dialog_about_supporters),
            AnnotatedString(
                "David Sansome (Clementine-Dev)\nJohn Maguire (Clementine-Dev)\nArnaud Bienner (Clementine-Dev)",
            ),
        )
        Credit(
            stringResource(R.string.dialog_about_others),
            AnnotatedString.fromHtml(
                "Thanks to all the <a href=\"https://github.com/clementine-player/Android-Remote/graphs/contributors\">" +
                    "contributors</a> and <a href=\"https://www.transifex.com/projects/p/clementine-remote/\">translators</a>!",
                links,
            ),
        )
    }
}

@Composable
private fun Credit(heading: String, names: AnnotatedString) {
    Column {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        Text(names, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The remote's own licence, the GPL. */
@Composable
internal fun LicenseDialog(onDismiss: () -> Unit) {
    TextDialog(stringResource(R.string.pref_license_title), "licenseDialog", onDismiss) {
        Image(painterResource(R.drawable.gplv3), contentDescription = stringResource(R.string.pref_license_summary))
        Text(stringResource(R.string.dialog_license_top), style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text(stringResource(R.string.gpl), style = MaterialTheme.typography.bodySmall)
    }
}

/** The licences of the open source software in the remote, from `res/raw/opensource.html`. */
@Composable
internal fun OpenSourceDialog(onDismiss: () -> Unit) {
    val resources = LocalContext.current.resources
    val licenses = remember(resources) {
        resources.openRawResource(R.raw.opensource).bufferedReader().use { parseLicenses(it.readText()) }
    }
    val links = linkStyles()
    TextDialog(stringResource(R.string.pref_opensource), "openSourceDialog", onDismiss) {
        licenses.forEach { license ->
            Text(
                buildAnnotatedString { withLink(LinkAnnotation.Url(license.url, links)) { append(license.name) } },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(license.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

internal data class OpenSourceLicense(val name: String, val url: String, val text: String)

/** The licences in the HTML: each a linked `<h3>` heading and its `<pre>` text. */
internal fun parseLicenses(html: String): List<OpenSourceLicense> =
    Regex("""<h3>\s*<a href="([^"]+)">([^<]+)</a>\s*</h3>\s*<pre>(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(html)
        .map { OpenSourceLicense(it.groupValues[2].trim(), it.groupValues[1], it.groupValues[3].trim()) }
        .toList()

@Composable
private fun linkStyles() = TextLinkStyles(
    SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
)

/** A dialog of scrolling text, closed with its one button. */
@Composable
private fun TextDialog(title: String, tag: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag(tag),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}
