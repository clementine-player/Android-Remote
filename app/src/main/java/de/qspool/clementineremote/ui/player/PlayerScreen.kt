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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

/** The player's pages, in the order of their tabs. */
internal val PLAYER_PAGES = listOf(
    R.string.fragment_title_player,
    R.string.fragment_title_details,
    R.string.fragment_title_connection,
)

/**
 * The player: tabs for the player, song details and connection pages, and the controls below
 * them. [onPageChanged] hears which page is shown, starting with the first.
 */
@Composable
fun PlayerScreen(
    onArtClick: () -> Unit,
    onPageChanged: (Int) -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val pagerState = rememberPagerState { PLAYER_PAGES.size }
    val scope = rememberCoroutineScope()
    val pageChanged by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { pageChanged(it) }
    }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent) {
            PLAYER_PAGES.forEachIndexed { page, title ->
                Tab(
                    selected = pagerState.currentPage == page,
                    onClick = { scope.launch { pagerState.animateScrollToPage(page) } },
                    text = { Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("tab$page"),
                )
            }
        }
        HorizontalPager(pagerState, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> NowPlaying(onArtClick, viewModel)
                1 -> SongDetails(viewModel)
                else -> ConnectionInfo(viewModel)
            }
        }
        PlayerControls(viewModel)
    }
}

/**
 * The player page: the artwork, the song and the seek bar. Tapping the artwork asks for the
 * song's lyrics ([onArtClick]).
 */
@Composable
fun NowPlaying(onArtClick: () -> Unit, viewModel: PlayerViewModel = viewModel()) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    NowPlayingContent(nowPlaying, onArtClick = onArtClick, onSeek = viewModel::seekTo)
}

/**
 * The player's controls: shuffle, previous, play/pause, next and repeat. A long press on
 * play/pause toggles stopping after the current song.
 */
@Composable
fun PlayerControls(viewModel: PlayerViewModel = viewModel()) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<Toast?>(null) }
    fun show(@StringRes text: Int) {
        // One at a time, so tapping through the modes shows the latest one.
        toast?.cancel()
        toast = Toast.makeText(context, text, Toast.LENGTH_SHORT).apply { show() }
    }
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
}

@Composable
internal fun NowPlayingContent(
    nowPlaying: NowPlaying,
    onArtClick: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        if (maxWidth > maxHeight) {
            // Landscape: the artwork beside the song.
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    nowPlaying.song, onArtClick,
                    Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SongInfo(nowPlaying.song, TextAlign.Start)
                    SeekBar(nowPlaying, onSeek)
                }
            }
        } else {
            // As wide as the page, or smaller if the page is too short for it and the song.
            val artworkHeight = (maxHeight - SONG_AND_SEEK_BAR_HEIGHT).coerceAtLeast(0.dp)
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Artwork(
                    nowPlaying.song, onArtClick,
                    Modifier.heightIn(max = artworkHeight).aspectRatio(1f))
                SongInfo(nowPlaying.song, TextAlign.Center)
                SeekBar(nowPlaying, onSeek)
            }
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
            .clickable(onClickLabel = stringResource(R.string.player_download_lyrics), onClick = onClick)
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
private fun SongInfo(song: MySong?, align: TextAlign) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            song?.title ?: stringResource(R.string.player_nosong),
            style = MaterialTheme.typography.headlineSmall,
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
        val details = listOf(song.genre, song.year).filter { !it.isNullOrBlank() }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SeekBar(nowPlaying: NowPlaying, onSeek: (Int) -> Unit) {
    val song = nowPlaying.song
    val length = song?.length ?: 0
    val position = if (nowPlaying.state == Clementine.State.STOP) 0 else nowPlaying.positionSeconds
    // While dragging, the thumb follows the finger; Clementine is told on release.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: position

    // Positions read left to right in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth()) {
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
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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

/** About what the song, the seek bar and the space between them take below the artwork. */
private val SONG_AND_SEEK_BAR_HEIGHT = 208.dp
