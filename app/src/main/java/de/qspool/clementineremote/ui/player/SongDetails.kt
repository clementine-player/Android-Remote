package de.qspool.clementineremote.ui.player

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.player.MySong
import de.qspool.clementineremote.utils.Utilities

/** The song details page: everything Clementine says about the song, and its rating. */
@Composable
fun SongDetails(viewModel: PlayerViewModel = viewModel()) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rated = stringResource(R.string.song_info_rated)
    SongDetailsContent(
        nowPlaying.song,
        onRate = { stars ->
            viewModel.rate(stars.toFloat())
            Toast.makeText(context, rated.replace("\$stars\$", stars.toString()), Toast.LENGTH_SHORT)
                .show()
        },
    )
}

@Composable
internal fun SongDetailsContent(song: MySong?, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    var zoomed by remember { mutableStateOf(false) }
    val art = remember(song == null, song?.artData) { song?.artData?.let { song.art.asImageBitmap() } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val artModifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp))
            if (art == null) {
                Image(
                    painterResource(R.drawable.icon_large),
                    contentDescription = stringResource(R.string.cd_cover_image),
                    modifier = artModifier,
                )
            } else {
                // Tap to see the cover full size.
                Image(
                    art,
                    contentDescription = stringResource(R.string.cd_cover_image),
                    contentScale = ContentScale.Crop,
                    modifier = artModifier.clickable { zoomed = true }.testTag("siArt"),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    song?.title ?: stringResource(R.string.player_nosong),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("siTitle"),
                )
                if (song != null) {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (song == null) {
            return@Column
        }

        Rating(song.rating * 5, onRate)
        HorizontalDivider()

        Detail(R.string.song_info_album, song.album)
        Detail(R.string.song_info_genre, song.genre)
        Detail(R.string.song_info_year, song.year)
        Detail(R.string.song_info_track, song.track.takeIf { it > 0 }?.toString())
        Detail(R.string.song_info_disc, song.disc.takeIf { it > 0 }?.toString())
        Detail(R.string.song_info_length, song.prettyLength)
        Detail(R.string.song_info_playcount, song.playcount.toString())
        Detail(R.string.song_info_size, song.size.takeIf { it > 0 }?.let { Utilities.humanReadableBytes(it, true) })
        Detail(R.string.song_info_filename, song.filename)
    }

    if (zoomed && art != null) {
        Dialog(
            onDismissRequest = { zoomed = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Image(
                art,
                contentDescription = stringResource(R.string.cd_cover_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(16.dp).aspectRatio(1f)
                    .clickable { zoomed = false },
            )
        }
    }
}

/** Clementine's rating in half stars; tapping a star rates the song that many stars. */
@Composable
private fun Rating(stars: Float, onRate: (Int) -> Unit) {
    Column {
        Text(
            stringResource(R.string.song_info_rating),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** One of the song's details, if Clementine knows it. */
@Composable
private fun Detail(label: Int, value: String?) {
    if (value.isNullOrBlank()) {
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
