package de.qspool.clementineremote.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.sync.KeptRule
import de.qspool.clementineremote.backend.sync.KeptSong

/**
 * Something kept on this phone, with its songs there, to play on the phone: Play plays them in
 * order, Shuffle at random, and a song plays them from it. The song playing is marked. Rows are
 * tagged "keptSong".
 */
@Composable
internal fun KeptRuleContent(
    kept: KeptRule,
    /** Null until they're read. */
    songs: List<KeptSong>?,
    /** The item key of the song playing on this phone. */
    playing: String?,
    running: Boolean,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().testTag("keptRule")) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp).testTag("keptRuleBack")) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_back))
            }
        }
        item {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    kept.rule.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    keptDetails(kept, running),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!songs.isNullOrEmpty()) {
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPlay(0) }, modifier = Modifier.testTag("keptPlay")) {
                            Icon(painterResource(R.drawable.ic_player_play), null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.sync_play))
                        }
                        FilledTonalButton(onClick = onShuffle, modifier = Modifier.testTag("keptShuffle")) {
                            Icon(painterResource(R.drawable.ic_player_shuffle), null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.sync_shuffle))
                        }
                    }
                }
            }
        }
        itemsIndexed(songs.orEmpty(), key = { _, song -> "keptSong/${song.itemKey}" }) { index, song ->
            val isPlaying = song.itemKey == playing
            val details = listOf(song.artist, song.album).filter { it.isNotEmpty() }.joinToString(" · ")
            val color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            ListItem(
                headlineContent = { Text(song.title, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = if (details.isEmpty()) {
                    null
                } else {
                    { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                leadingContent = if (isPlaying) {
                    { Icon(painterResource(R.drawable.ic_player_play), null, tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                trailingContent = if (song.length.isEmpty()) {
                    null
                } else {
                    { Text(song.length, style = MaterialTheme.typography.bodySmall) }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clickable { onPlay(index) }
                    .semantics { selected = isPlaying }
                    .testTag("keptSong"),
            )
        }
    }
}
