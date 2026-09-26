package de.qspool.clementineremote.ui.player

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.player.MySong
import de.qspool.clementineremote.utils.Utilities
import kotlin.math.roundToInt

/** What the player's buttons do, beyond the transport controls. */
interface PlayerActions {
    /** Goes back to the screen the player was opened from. */
    fun onCollapse()

    /** Opens the song's details, or its lyrics. */
    fun onDetails(lyrics: Boolean)

    /** Shows the playlist playing. */
    fun onQueue()

    /** Downloads the song, its album or its playlist. */
    fun onDownload()
}

/**
 * The player, full screen: where Clementine plays from, the artwork, the song (and loving it on
 * Last.fm), the seek bar and the controls, then the song's details and lyrics, the volume, the
 * queue and downloading. Tapping the artwork shows the lyrics.
 */
@Composable
fun PlayerScreen(actions: PlayerActions, viewModel: PlayerViewModel = viewModel()) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<Toast?>(null) }
    fun show(@StringRes text: Int) {
        // One at a time, so tapping through the modes shows the latest one.
        toast?.cancel()
        toast = Toast.makeText(context, text, Toast.LENGTH_SHORT).apply { show() }
    }
    val lastFm = remember { viewModel.lastFm() }
    PlayerContent(
        nowPlaying,
        playingFrom = remember(nowPlaying.song) { viewModel.playingFrom() },
        lastFm = lastFm,
        actions = actions,
        onSeek = viewModel::seekTo,
        onVolume = viewModel::setVolume,
        onStop = viewModel::stop,
        onLove = {
            viewModel.love()
            show(R.string.track_loved)
        },
        onBan = {
            viewModel.ban()
            show(R.string.track_banned)
        },
        controls = {
            PlayerControlsContent(
                nowPlaying,
                onPlayPause = viewModel::playPause,
                onStopAfterCurrent = {
                    viewModel.stopAfterCurrent()
                    show(R.string.player_stop_after_current)
                },
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onShuffle = { show(shuffleLabel(viewModel.cycleShuffle())) },
                onRepeat = { show(repeatLabel(viewModel.cycleRepeat())) },
            )
        },
    )
}

@Composable
internal fun PlayerContent(
    nowPlaying: NowPlaying,
    playingFrom: String?,
    lastFm: Boolean,
    actions: PlayerActions,
    onSeek: (Int) -> Unit,
    onVolume: (Int) -> Unit,
    onStop: () -> Unit,
    onLove: () -> Unit,
    onBan: () -> Unit,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding().testTag("player")) {
        TopRow(playingFrom, lastFm, actions::onCollapse, onStop, onBan)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val song = nowPlaying.song
            // The artwork is whatever square is left over, so short screens keep every control.
            val below: @Composable ColumnScope.() -> Unit = {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SongInfo(song, TextAlign.Start, Modifier.weight(1f))
                    if (lastFm && song != null) {
                        LoveButton(song, onLove)
                    }
                }
                SeekBar(nowPlaying, onSeek, Modifier.padding(horizontal = 24.dp))
                controls()
                BottomRow(actions, nowPlaying.volume, onVolume)
            }
            if (maxWidth > maxHeight) {
                // Landscape: the artwork beside the rest.
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(
                        song, { actions.onDetails(lyrics = true) },
                        Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
                            .fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { below() }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Artwork(
                        song, { actions.onDetails(lyrics = true) },
                        Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)
                            .weight(1f, fill = false)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .align(Alignment.CenterHorizontally),
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) { below() }
                }
            }
        }
    }
}

@Composable
private fun TopRow(
    playingFrom: String?,
    lastFm: Boolean,
    onCollapse: () -> Unit,
    onStop: () -> Unit,
    onBan: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCollapse, modifier = Modifier.testTag("btnCollapse")) {
            Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.player_collapse))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!playingFrom.isNullOrBlank()) {
                Text(
                    stringResource(R.string.player_playing_from),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    playingFrom,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("playingFrom"),
                )
            }
        }
        Box {
            var open by remember { mutableStateOf(false) }
            IconButton(onClick = { open = true }, modifier = Modifier.testTag("btnPlayerMore")) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.shell_more))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasker_stop)) },
                    onClick = {
                        open = false
                        onStop()
                    },
                    modifier = Modifier.testTag("menuStop"),
                )
                if (lastFm) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_ban)) },
                        onClick = {
                            open = false
                            onBan()
                        },
                        modifier = Modifier.testTag("menuBan"),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoveButton(song: MySong, onLove: () -> Unit) {
    // A song can be loved only once; the song keeps whether it was.
    var loved by remember(song) { mutableStateOf(song.isLoved) }
    IconToggleButton(
        checked = loved,
        onCheckedChange = {
            if (!loved) {
                loved = true
                onLove()
            }
        },
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.testTag("btnLove"),
    ) {
        Icon(
            painterResource(if (loved) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
            stringResource(R.string.player_love),
        )
    }
}

@Composable
private fun BottomRow(actions: PlayerActions, volume: Int, onVolume: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { actions.onDetails(lyrics = false) }, modifier = Modifier.testTag("btnDetails")) {
            Icon(painterResource(R.drawable.ic_lyrics), stringResource(R.string.player_details))
        }
        VolumeButton(volume, onVolume)
        IconButton(onClick = actions::onQueue, modifier = Modifier.testTag("btnQueue")) {
            Icon(painterResource(R.drawable.ic_queue_music), stringResource(R.string.nav_queue))
        }
        IconButton(onClick = actions::onDownload, modifier = Modifier.testTag("btnDownload")) {
            Icon(painterResource(R.drawable.ic_download), stringResource(R.string.player_download_song))
        }
    }
}

@Composable
private fun Artwork(song: MySong?, onClick: () -> Unit, modifier: Modifier) {
    // Decoded once per cover, and faded over when the cover changes.
    val art = remember(song == null, song?.artData) { song?.art?.asImageBitmap() }
    Crossfade(
        targetState = art,
        animationSpec = tween(ARTWORK_FADE_MILLIS),
        label = "artwork",
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = stringResource(R.string.lyrics_tab), onClick = onClick)
            .testTag("imgArt"),
    ) { bitmap ->
        if (bitmap == null) {
            Image(
                painterResource(R.drawable.icon_large),
                contentDescription = stringResource(R.string.cd_cover_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(32.dp),
            )
        } else {
            Image(
                bitmap,
                contentDescription = stringResource(R.string.cd_cover_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SongInfo(song: MySong?, align: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            song?.title ?: stringResource(R.string.player_nosong),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("tvTitle"),
        )
        if (song == null) {
            return@Column
        }
        Text(
            song.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("tvArtist"),
        )
        Text(
            song.album,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("tvAlbum"),
        )
    }
}

@Composable
private fun SeekBar(nowPlaying: NowPlaying, onSeek: (Int) -> Unit, modifier: Modifier) {
    val song = nowPlaying.song
    val length = song?.length ?: 0
    val position = if (nowPlaying.state == Clementine.State.STOP) 0 else nowPlaying.positionSeconds
    // While dragging, the thumb follows the finger; Clementine is told on release.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: position

    // Positions read left to right in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth()) {
            Slider(
                value = (dragging ?: position.toFloat()).coerceIn(0f, length.toFloat()),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onSeek(it.roundToInt()) }
                    dragging = null
                },
                valueRange = 0f..length.coerceAtLeast(1).toFloat(),
                enabled = length > 0,
                modifier = Modifier.fillMaxWidth().testTag("sbPosition"),
            )
            if (song != null) {
                Row(Modifier.fillMaxWidth()) {
                    val stream = if (song.isLocal) "" else stringResource(R.string.player_stream) + " "
                    Text(
                        stream + Utilities.PrettyTime(shown),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("tvPosition"),
                    )
                    Spacer(Modifier.weight(1f))
                    if (length > 0) {
                        Text(
                            Utilities.PrettyTime(length),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("tvLength"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerControlsContent(
    nowPlaying: NowPlaying,
    onPlayPause: () -> Unit,
    onStopAfterCurrent: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Media controls keep their order in right-to-left languages.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeButton(
                checked = nowPlaying.shuffle != Clementine.ShuffleMode.OFF,
                icon = R.drawable.ic_player_shuffle,
                description = shuffleLabel(nowPlaying.shuffle),
                onClick = onShuffle,
                tag = "btnShuffle",
            )
            IconButton(onPrevious, Modifier.size(56.dp).testTag("btnPrev")) {
                Icon(
                    painterResource(R.drawable.ic_player_previous),
                    contentDescription = stringResource(R.string.notification_previous),
                    modifier = Modifier.size(32.dp),
                )
            }
            PlayPauseButton(nowPlaying.isPlaying, onPlayPause, onStopAfterCurrent)
            IconButton(onNext, Modifier.size(56.dp).testTag("btnNext")) {
                Icon(
                    painterResource(R.drawable.ic_player_next),
                    contentDescription = stringResource(R.string.notification_next),
                    modifier = Modifier.size(32.dp),
                )
            }
            ModeButton(
                checked = nowPlaying.repeat != Clementine.RepeatMode.OFF,
                icon = if (nowPlaying.repeat == Clementine.RepeatMode.TRACK) {
                    R.drawable.ic_player_repeat_one
                } else {
                    R.drawable.ic_player_repeat
                },
                description = repeatLabel(nowPlaying.repeat),
                onClick = onRepeat,
                tag = "btnRepeat",
            )
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .size(width = 96.dp, height = 72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.player_stop_after_current),
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .testTag("btnPlaypause"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play),
            contentDescription = stringResource(
                if (playing) R.string.notification_pause else R.string.notification_play),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ModeButton(
    checked: Boolean,
    icon: Int,
    description: Int,
    onClick: () -> Unit,
    tag: String,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.size(48.dp).testTag(tag),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(description))
    }
}

@StringRes
internal fun shuffleLabel(mode: Clementine.ShuffleMode): Int = when (mode) {
    Clementine.ShuffleMode.OFF -> R.string.shuffle_off
    Clementine.ShuffleMode.ALL -> R.string.shuffle_all
    Clementine.ShuffleMode.INSIDE_ALBUM -> R.string.shuffle_inside_album
    Clementine.ShuffleMode.ALBUMS -> R.string.shuffle_albums
}

@StringRes
internal fun repeatLabel(mode: Clementine.RepeatMode): Int = when (mode) {
    Clementine.RepeatMode.OFF -> R.string.repeat_off
    Clementine.RepeatMode.TRACK -> R.string.repeat_track
    Clementine.RepeatMode.ALBUM -> R.string.repeat_album
    Clementine.RepeatMode.PLAYLIST -> R.string.repeat_playlist
}

private const val ARTWORK_FADE_MILLIS = 750
