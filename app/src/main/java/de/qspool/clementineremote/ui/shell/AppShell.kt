package de.qspool.clementineremote.ui.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.ui.downloads.DownloadsScreen
import de.qspool.clementineremote.ui.downloads.DownloadsViewModel
import de.qspool.clementineremote.ui.library.LibraryScreen
import de.qspool.clementineremote.ui.library.LibraryViewModel
import de.qspool.clementineremote.ui.player.PlayerActions
import de.qspool.clementineremote.ui.player.PlayerScreen
import de.qspool.clementineremote.ui.player.PlayerViewModel
import de.qspool.clementineremote.ui.player.SongDetailsSheet
import de.qspool.clementineremote.ui.queue.QueueScreen
import de.qspool.clementineremote.ui.queue.QueueViewModel
import de.qspool.clementineremote.ui.search.SearchScreen
import de.qspool.clementineremote.ui.search.SearchViewModel

/** The screens the navigation bar switches between. */
enum class Destination(@StringRes val label: Int, @DrawableRes val icon: Int, val tag: String) {
    QUEUE(R.string.nav_queue, R.drawable.ic_queue_music, "navQueue"),
    LIBRARY(R.string.library_title, R.drawable.ic_library_music, "navLibrary"),
    SEARCH(R.string.menu_search, R.drawable.ic_search, "navSearch"),
    DOWNLOADS(R.string.downloads_title, R.drawable.ic_download, "navDownloads"),
}

/** Where the app is: the screen shown, and whether the player is open over it. */
class ShellViewModel : ViewModel() {
    var destination by mutableStateOf(Destination.QUEUE)

    var playerOpen by mutableStateOf(false)
}

/** What the shell asks of the activity. */
interface ShellActions : ConnectionActions {
    /** Downloads the song playing, its album or its playlist. */
    fun onDownloadSong(what: DownloadWhat)
}

/**
 * The app once connected: the queue, library, search and downloads, switched between with the
 * navigation bar (a rail on wide screens), the connection chip at the top, and the mini player
 * at the bottom, which opens the player full screen. The chip opens the connection sheet; the
 * player opens the song details sheet.
 */
@Composable
fun AppShell(shell: ShellViewModel, actions: ShellActions) {
    val player: PlayerViewModel = viewModel()
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    var connectionOpen by rememberSaveable { mutableStateOf(false) }
    // The song details sheet, and whether it shows the lyrics; null while closed.
    var details by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var choosingDownload by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val layout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    Box(Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            layoutType = layout,
            navigationSuiteItems = {
                Destination.entries.forEach { destination ->
                    item(
                        selected = shell.destination == destination,
                        onClick = { shell.destination = destination },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        modifier = Modifier.testTag(destination.tag),
                    )
                }
            },
        ) {
            // The navigation bar keeps clear of the system's bar below it; a rail doesn't.
            val sides = if (layout == NavigationSuiteType.NavigationBar) {
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            } else {
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            }
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(sides))) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Destinations(shell, onConnection = { connectionOpen = true })
                }
                if (nowPlaying.song != null) {
                    MiniPlayer(
                        nowPlaying,
                        onOpen = { shell.playerOpen = true },
                        onPlayPause = player::playPause,
                        onNext = player::next,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = shell.playerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            BackHandler { shell.playerOpen = false }
            Surface(Modifier.fillMaxSize()) {
                PlayerScreen(
                    object : PlayerActions {
                        override fun onCollapse() {
                            shell.playerOpen = false
                        }

                        override fun onDetails(lyrics: Boolean) {
                            details = lyrics
                        }

                        override fun onQueue() {
                            shell.destination = Destination.QUEUE
                            shell.playerOpen = false
                        }

                        override fun onDownload() {
                            // Only songs in Clementine's library can be downloaded, not streams.
                            val song = nowPlaying.song
                            val problem = when {
                                song == null -> R.string.player_nosong
                                !song.isLocal -> R.string.player_song_is_stream
                                else -> null
                            }
                            if (problem == null) {
                                choosingDownload = true
                            } else {
                                Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    player,
                )
            }
        }
    }

    details?.let { lyrics ->
        SongDetailsSheet(showLyrics = lyrics, onDismiss = { details = null }, viewModel = player)
    }
    if (connectionOpen) {
        ConnectionSheet(actions, onDismiss = { connectionOpen = false })
    }
    if (choosingDownload) {
        DownloadChooser(
            onChoose = {
                choosingDownload = false
                actions.onDownloadSong(it)
            },
            onDismiss = { choosingDownload = false },
        )
    }
}

/** The screen [ShellViewModel.destination] names, with its top bar; back goes up, then home. */
@Composable
private fun Destinations(shell: ShellViewModel, onConnection: () -> Unit) {
    val queue: QueueViewModel = viewModel()
    val library: LibraryViewModel = viewModel()
    val search: SearchViewModel = viewModel()
    val downloads: DownloadsViewModel = viewModel()
    val libraryState by library.state.collectAsStateWithLifecycle()
    val searchState by search.state.collectAsStateWithLifecycle()
    // Clementine's computer, or its address if it doesn't say.
    val host = remember {
        App.Clementine.hostname?.takeIf { it.isNotBlank() }
            ?: App.getPreferences().getString(SharedPreferencesKeys.SP_KEY_IP, null)?.takeIf { it.isNotBlank() }
    }

    // Back leaves search results, then the screen, for the queue.
    BackHandler(shell.destination != Destination.QUEUE) { shell.destination = Destination.QUEUE }
    BackHandler(shell.destination == Destination.LIBRARY && libraryState.levels.size > 1) { library.back() }
    BackHandler(shell.destination == Destination.SEARCH && searchState.levels.size > 1) { search.back() }

    Column(Modifier.fillMaxSize()) {
        when (shell.destination) {
            Destination.QUEUE -> {
                // Fetch the songs of playlists not downloaded yet.
                LifecycleResumeEffect(queue) {
                    queue.load()
                    onPauseOrDispose {}
                }
                var clearing by remember { mutableStateOf(false) }
                FilterableTopBar(host, onConnection, queue::setFilter) {
                    Box {
                        var open by remember { mutableStateOf(false) }
                        IconButton(onClick = { open = true }, modifier = Modifier.testTag("btnQueueMore")) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.shell_more))
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_download_playlist)) },
                                onClick = {
                                    open = false
                                    queue.downloadPlaylist()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_clear)) },
                                onClick = {
                                    open = false
                                    clearing = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_close)) },
                                onClick = {
                                    open = false
                                    queue.closePlaylist()
                                },
                            )
                        }
                    }
                }
                QueueScreen(queue)
                if (clearing) {
                    AlertDialog(
                        onDismissRequest = { clearing = false },
                        title = { Text(stringResource(R.string.playlist_clear)) },
                        text = { Text(stringResource(R.string.playlist_clear_content)) },
                        dismissButton = {
                            TextButton(onClick = { clearing = false }) { Text(stringResource(R.string.dialog_cancel)) }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                clearing = false
                                queue.clearPlaylist()
                            }) { Text(stringResource(R.string.playlist_clear_confirm)) }
                        },
                    )
                }
            }
            Destination.LIBRARY -> {
                FilterableTopBar(host, onConnection, library::setFilter)
                LibraryScreen(library)
            }
            Destination.SEARCH -> {
                TopBar(host, onConnection)
                SearchScreen(search)
            }
            Destination.DOWNLOADS -> {
                TopBar(host, onConnection)
                DownloadsScreen(downloads)
            }
        }
    }
}

/** The top bar: the connection chip, and the screen's actions. */
@Composable
private fun TopBar(host: String?, onConnection: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        ConnectionChip(host, onConnection, Modifier.padding(end = 4.dp))
        actions()
    }
}

/**
 * A top bar with a search button that turns it into a field, which filters what the screen
 * shows ([onFilter]); closing it shows everything again.
 */
@Composable
private fun FilterableTopBar(
    host: String?,
    onConnection: () -> Unit,
    onFilter: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    fun close() {
        filter = null
        onFilter("")
    }
    BackHandler(filter != null) { close() }
    val text = filter
    if (text == null) {
        TopBar(host, onConnection) {
            IconButton(onClick = { filter = "" }, modifier = Modifier.testTag("btnFilter")) {
                Icon(painterResource(R.drawable.ic_search), stringResource(R.string.menu_search))
            }
            actions()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = {
                    filter = it
                    onFilter(it)
                },
                placeholder = { Text(stringResource(R.string.playlist_search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).testTag("filterField"),
            )
            IconButton(onClick = ::close, modifier = Modifier.testTag("btnFilterClose")) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.shell_search_close))
            }
        }
    }
}

/** The Clementine connected to; opens the connection sheet. */
@Composable
internal fun ConnectionChip(host: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = host ?: stringResource(R.string.fragment_title_connection)
    val description = stringResource(R.string.shell_connected_to, name)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(32.dp).semantics { contentDescription = description }.testTag("connectionChip"),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_computer),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The song playing, above the navigation bar: opens the player, plays or pauses, skips. */
@Composable
internal fun MiniPlayer(
    nowPlaying: NowPlaying,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = nowPlaying.song ?: return
    val openLabel = stringResource(R.string.shell_open_player, song.title.orEmpty())
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().height(64.dp),
    ) {
        Box {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(onClickLabel = openLabel, onClick = onOpen)
                        .testTag("miniPlayer"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val art = remember(song.artData) { song.artData?.let { song.art.asImageBitmap() } }
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (art == null) {
                            Icon(
                                painterResource(R.drawable.ic_music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("miniTitle"),
                        )
                        Text(
                            song.artist.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    onClick = onPlayPause,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(width = 56.dp, height = 48.dp).testTag("miniPlayPause"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (nowPlaying.isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                            contentDescription = stringResource(
                                if (nowPlaying.isPlaying) R.string.notification_pause else R.string.notification_play),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.testTag("miniNext")) {
                    Icon(painterResource(R.drawable.ic_player_next), stringResource(R.string.notification_next))
                }
            }
            val length = song.length
            if (length > 0) {
                // Only a glance at the position, which the player shows with its seek bar: kept out
                // of accessibility, where its node would stand over the play/pause button.
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = 16.dp).height(3.dp)
                        .clearAndSetSemantics {},
                ) {
                    LinearProgressIndicator(
                        progress = { (nowPlaying.positionSeconds.toFloat() / length).coerceIn(0f, 1f) },
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
