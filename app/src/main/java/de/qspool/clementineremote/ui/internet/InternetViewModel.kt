package de.qspool.clementineremote.ui.internet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.RemoteRepository.BrowseMessage
import de.qspool.clementineremote.backend.RemoteRepository.Browsing
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseAddAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The Internet screen's state: the levels opened, from the services down. */
data class InternetState(
    val levels: List<InternetLevel> = listOf(InternetLevel()),
) {
    val shown: InternetLevel get() = levels.last()
}

/**
 * Browses Clementine's Internet sidebar (its services, such as SomaFM or Subsonic, and what's
 * below them) and puts what's picked on the playlist, following the connection: each new one
 * starts again from the services.
 */
class InternetViewModel(
    send: (ClementineMessage) -> Unit = RemoteRepository::send,
    private val browsing: StateFlow<Browsing> = RemoteRepository.browsing,
    private val messages: Flow<BrowseMessage> = RemoteRepository.browseMessages,
    /** Whether Clementine is playing, so a tap adds to the playlist rather than plays. */
    private val playing: () -> Boolean = { RemoteRepository.nowPlaying.value.isPlaying },
) : ViewModel() {

    private val browser = InternetBrowser(send)

    private val _state = MutableStateFlow(InternetState())

    val state: StateFlow<InternetState> = _state.asStateFlow()

    private val _added = Channel<AddMessage>(Channel.BUFFERED)

    /** What to tell the user after putting something on the playlist. */
    val added: Flow<AddMessage> = _added.receiveAsFlow()

    /** The connection browsed; answers from another are ignored. */
    private var connection = -1

    init {
        // Listening before asking for anything.
        viewModelScope.launch {
            messages.collect { message ->
                if (message.connection != connection) {
                    return@collect
                }
                when (message) {
                    is BrowseMessage.Browse -> update { browser.onBrowse(message.response) }
                    is BrowseMessage.AddResult -> {
                        var result: AddMessage? = null
                        update { result = browser.onAddResult(message.response) }
                        result?.let { _added.trySend(it) }
                    }
                }
            }
        }
        viewModelScope.launch {
            browsing.collect {
                if (it.connection != connection) {
                    connection = it.connection
                    update { browser.reset(it.supported) }
                }
            }
        }
    }

    /** Asks for the level shown again: when the screen shows, and when pulled down. */
    fun refresh() = update { browser.refresh() }

    /** Taps a node: opens it, plays it or adds it to the playlist, or nothing. */
    fun tap(node: InternetNode) = update { browser.tap(node, playing()) }

    /** Puts a node on the playlist, as [action] says. */
    fun add(node: InternetNode, action: BrowseAddAction) = update { browser.add(node, action) }

    /** Goes up a level; false at the services. */
    fun back(): Boolean {
        var went = false
        update { went = browser.back() }
        return went
    }

    /** The last node loaded is shown: loads more, if there are more. */
    fun loadMore() = update { browser.loadMore() }

    private inline fun update(change: () -> Unit) {
        change()
        _state.value = InternetState(browser.levels)
    }
}
