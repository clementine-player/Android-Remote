package de.qspool.clementineremote.ui.player

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.player.MySong
import de.qspool.clementineremote.utils.Utilities

/**
 * The song details, in a sheet over the player: what Clementine says about the song and its
 * rating, or its lyrics, which Clementine looks up when they're first shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SongDetailsSheet(showLyrics: Boolean, onDismiss: () -> Unit, viewModel: PlayerViewModel = viewModel()) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    var lyricsShown by rememberSaveable { mutableStateOf(showLyrics) }
    val context = LocalContext.current
    val rated = stringResource(R.string.song_info_rated)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The sheet is a window of its own: its test tags are resource IDs too, for UI Automator.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        SongDetailsContent(
            nowPlaying.song,
            lyrics,
            lyricsShown = lyricsShown,
            onShowLyrics = { lyricsShown = it },
            onRequestLyrics = viewModel::requestLyrics,
            onRate = { stars ->
                viewModel.rate(stars.toFloat())
                Toast.makeText(context, rated.replace("\$stars\$", stars.toString()), Toast.LENGTH_SHORT)
                    .show()
            },
        )
    }
}

@Composable
internal fun SongDetailsContent(
    song: MySong?,
    lyrics: Lyrics,
    lyricsShown: Boolean,
    onShowLyrics: (Boolean) -> Unit,
    onRequestLyrics: () -> Unit,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            Text(
                song?.title ?: stringResource(R.string.player_nosong),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("siTitle"),
            )
            if (song != null) {
                Text(
                    listOf(song.artist, song.album).filter { !it.isNullOrBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (song == null) {
            return@Column
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !lyricsShown,
                onClick = { onShowLyrics(false) },
                label = { Text(stringResource(R.string.details_tab)) },
                modifier = Modifier.testTag("siDetailsTab"),
            )
            FilterChip(
                selected = lyricsShown,
                onClick = { onShowLyrics(true) },
                label = { Text(stringResource(R.string.lyrics_tab)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_lyrics), null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("siLyricsTab"),
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
            if (lyricsShown) {
                LyricsText(lyrics, onRequestLyrics)
            } else {
                Details(song, onRate)
            }
        }
    }
}

@Composable
private fun Details(song: MySong, onRate: (Int) -> Unit) {
    Detail(R.string.song_info_album, song.album)
    Detail(R.string.song_info_genre, song.genre)
    Detail(R.string.song_info_year, song.year)
    Detail(R.string.song_info_track, song.track.takeIf { it > 0 }?.toString())
    Detail(R.string.song_info_disc, song.disc.takeIf { it > 0 }?.toString())
    Detail(R.string.song_info_length, song.prettyLength)
    Detail(R.string.song_info_playcount, song.playcount.toString())
    Detail(R.string.song_info_size, song.size.takeIf { it > 0 }?.let { Utilities.humanReadableBytes(it, true) })
    Detail(R.string.song_info_filename, song.filename)
    Rating(song.rating * 5, onRate)
}

/** One of the song's details, if Clementine knows it. */
@Composable
private fun Detail(label: Int, value: String?) {
    if (value.isNullOrBlank()) {
        return
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp),
            )
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Clementine's rating in half stars; tapping a star rates the song that many stars. */
@Composable
private fun Rating(stars: Float, onRate: (Int) -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.song_info_rating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Row(Modifier.testTag("siRating")) {
            for (star in 1..5) {
                val icon = when {
                    stars >= star - 0.25f -> R.drawable.ic_player_star
                    stars >= star - 0.75f -> R.drawable.ic_player_star_half
                    else -> R.drawable.ic_player_star_border
                }
                IconButton(onClick = { onRate(star) }, modifier = Modifier.testTag("siStar$star")) {
                    Icon(
                        painterResource(icon),
                        contentDescription = pluralStringResource(R.plurals.song_info_stars, star, star),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsText(lyrics: Lyrics, onRequest: () -> Unit) {
    // Clementine is asked for them the first time they're shown.
    LaunchedEffect(lyrics == Lyrics.NotAsked) {
        if (lyrics == Lyrics.NotAsked) {
            onRequest()
        }
    }
    when (lyrics) {
        Lyrics.NotAsked, Lyrics.Loading -> Row(
            Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("lyricsLoading"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.player_download_lyrics), style = MaterialTheme.typography.bodyLarge)
        }
        Lyrics.None -> Text(
            stringResource(R.string.player_no_lyrics),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp).testTag("lyricsNone"),
        )
        is Lyrics.Found -> Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                lyrics.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                lyrics.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp).testTag("lyrics"),
            )
        }
    }
}
