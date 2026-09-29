package de.qspool.clementineremote.ui.internet

import com.google.protobuf.ByteString
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseAddAction
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseAddResult
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseChildren
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseNode
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseNodeKind
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowsePlayability
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseBrowse
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseBrowseAdd

/** What a node of Clementine's Internet sidebar is, for its icon. */
enum class NodeKind { SERVICE, FOLDER, TRACK, STREAM, SMART_PLAYLIST }

/** A node of the tree Clementine's Internet sidebar shows: a service, or something below one. */
data class InternetNode(
    /** Clementine's id for it, valid on this connection only. */
    val id: String,
    val title: String,
    /** A second line, such as a track's artist; null when there's none. */
    val subtitle: String? = null,
    val kind: NodeKind = NodeKind.FOLDER,
    /** Whether it opens, to the nodes below it. */
    val hasChildren: Boolean = false,
    /** Whether it can go on the playlist. */
    val addable: Boolean = false,
    /** A service's own icon, as a PNG. */
    val icon: ByteString? = null,
) {
    companion object {
        fun of(node: BrowseNode) = InternetNode(
            id = node.nodeId,
            title = node.title,
            subtitle = node.subtitle.takeIf { node.hasSubtitle() && it.isNotBlank() },
            kind = when (node.kind) {
                BrowseNodeKind.BROWSE_NODE_KIND_SERVICE -> NodeKind.SERVICE
                BrowseNodeKind.BROWSE_NODE_KIND_TRACK -> NodeKind.TRACK
                BrowseNodeKind.BROWSE_NODE_KIND_STREAM -> NodeKind.STREAM
                BrowseNodeKind.BROWSE_NODE_KIND_SMART_PLAYLIST -> NodeKind.SMART_PLAYLIST
                else -> NodeKind.FOLDER
            },
            hasChildren = node.children == BrowseChildren.BROWSE_CHILDREN_SOME,
            addable = node.playability == BrowsePlayability.BROWSE_PLAYABILITY_ADDABLE,
            icon = node.iconPng.takeIf { node.hasIconPng() && it.size() > 0 },
        )
    }
}

/** Where a level's nodes stand. */
enum class LevelStatus {
    /** Still loading: before Clementine's first answer, or while it loads more. */
    LOADING,
    READY,

    /** The service has to be set up on the computer first. */
    NEEDS_SETUP,
}

/** A level of the tree: the services, or the nodes below one that was opened. */
data class InternetLevel(
    /** The node opened; null for the services. */
    val opened: InternetNode? = null,
    val status: LevelStatus = LevelStatus.LOADING,
    /** The nodes loaded so far, from the first. */
    val nodes: List<InternetNode> = emptyList(),
    /** How many nodes there are in all. */
    val totalCount: Int = 0,
    /** With [LevelStatus.NEEDS_SETUP], Clementine's sentence saying how. */
    val message: String? = null,
) {
    /** Clementine's id for this level: empty for the services. */
    val id: String get() = opened?.id.orEmpty()

    /** Whether there are more nodes than those loaded. */
    val hasMore: Boolean get() = totalCount > nodes.size
}

/** What tapping a node does. */
sealed interface TapAction {
    /** Opens it, to the nodes below it. */
    data object Open : TapAction

    /** Puts it on the playlist. */
    data class Add(val action: BrowseAddAction) : TapAction

    /** Nothing: it neither opens nor can go on the playlist. */
    data object None : TapAction
}

/** What to tell the user after putting something on the playlist. */
enum class AddMessage {
    /** Added to the end of the playlist. */
    ADDED,

    /** Queued to play next. */
    PLAYING_NEXT,

    /** Clementine couldn't put it on the playlist. */
    NOT_PLAYABLE,

    /** It's no longer in Clementine. */
    GONE,
}

/**
 * Browses Clementine's Internet sidebar, level by level, over the connection: asks Clementine
 * for a level's nodes ([send]), and follows its answers. Clementine keeps sending the level last
 * asked for as it changes, so going back up asks for the level shown again. Node ids last only as
 * long as the connection: a new one ([reset]) starts again from the services.
 *
 * Not thread safe: its methods are called on one thread, the main thread in the app.
 */
class InternetBrowser(private val send: (ClementineMessage) -> Unit) {

    /** The levels opened, from the services down; the last is shown. Never empty. */
    var levels: List<InternetLevel> = listOf(InternetLevel())
        private set

    val shown: InternetLevel get() = levels.last()

    /** Whether Clementine can be browsed. */
    var supported: Boolean = false
        private set

    /** The offset of the next page asked for the level shown, until it comes. */
    private var pageAsked: Int? = null

    /** What was put on the playlist, and how, until Clementine says how it went. */
    private val adding = ArrayDeque<Pair<List<String>, BrowseAddAction>>()

    /**
     * A new connection, to a Clementine that can be browsed or not: forgets every id, and shows
     * the services, asking for them if it can.
     */
    fun reset(supported: Boolean) {
        this.supported = supported
        levels = listOf(InternetLevel())
        pageAsked = null
        adding.clear()
        if (supported) {
            ask(shown)
        }
    }

    /** Asks for the level shown again, from its first node, keeping what's shown meanwhile. */
    fun refresh() {
        if (supported) {
            pageAsked = null
            ask(shown)
        }
    }

    /** Opens [node] if it has nodes below it; it shows loading until Clementine answers. */
    fun open(node: InternetNode) {
        if (!node.hasChildren) {
            return
        }
        levels = levels + InternetLevel(opened = node)
        pageAsked = null
        ask(shown)
    }

    /** Goes up a level, asking for it again; false at the services. */
    fun back(): Boolean {
        if (levels.size <= 1) {
            return false
        }
        levels = levels.dropLast(1)
        pageAsked = null
        ask(shown)
        return true
    }

    /** The last node loaded is shown: asks for the next page, if there's more and it isn't asked for yet. */
    fun loadMore() {
        val level = shown
        if (!level.hasMore || pageAsked == level.nodes.size) {
            return
        }
        pageAsked = level.nodes.size
        ask(level, offset = level.nodes.size)
    }

    /** Taps [node]: opens it, or puts it on the playlist, or nothing (see [tapAction]). */
    fun tap(node: InternetNode, playing: Boolean) {
        when (val action = tapAction(node, playing)) {
            TapAction.Open -> open(node)
            is TapAction.Add -> add(node, action.action)
            TapAction.None -> {}
        }
    }

    /** Puts [node] (and everything below it) on the playlist, as [action] says. */
    fun add(node: InternetNode, action: BrowseAddAction) {
        if (!node.addable) {
            return
        }
        val ids = listOf(node.id)
        adding.addLast(ids to action)
        send(ClementineMessageFactory.buildBrowseAdd(ids, action))
    }

    /** Clementine's answer to a request for a level, or the level it watches, changed. */
    fun onBrowse(response: ResponseBrowse) {
        val level = shown
        // Answers for a level no longer shown: Clementine watches only the last asked for.
        if (response.nodeId != level.id) {
            return
        }
        if (response.state == BrowseState.BROWSE_STATE_GONE) {
            if (!back()) {
                // The services never go; ask for them again, all the same.
                refresh()
            }
            return
        }
        if (pageAsked == response.offset) {
            pageAsked = null
        }
        val status = when (response.state) {
            BrowseState.BROWSE_STATE_LOADING -> LevelStatus.LOADING
            BrowseState.BROWSE_STATE_NEEDS_SETUP -> LevelStatus.NEEDS_SETUP
            else -> LevelStatus.READY
        }
        val nodes = merge(level.nodes, response.offset, response.nodesList.map(InternetNode::of), response.totalCount)
            ?: return
        levels = levels.dropLast(1) + level.copy(
            status = status,
            nodes = nodes,
            totalCount = response.totalCount,
            message = response.message.takeIf { status == LevelStatus.NEEDS_SETUP && it.isNotBlank() },
        )
    }

    /**
     * How putting nodes on the playlist went: what to tell the user, if anything. When the nodes
     * are gone, the level shown is asked for again.
     */
    fun onAddResult(response: ResponseBrowseAdd): AddMessage? {
        val ids = response.nodeIdsList.toList()
        val index = adding.indexOfFirst { it.first == ids }
        val action = if (index >= 0) adding.removeAt(index).second else BrowseAddAction.BROWSE_ADD_ACTION_APPEND
        return when (response.result) {
            BrowseAddResult.BROWSE_ADD_RESULT_ADDED -> when (action) {
                BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NEXT -> AddMessage.PLAYING_NEXT
                // Playing it shows in the player.
                BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NOW, BrowseAddAction.BROWSE_ADD_ACTION_REPLACE -> null
                else -> AddMessage.ADDED
            }
            BrowseAddResult.BROWSE_ADD_RESULT_GONE -> {
                refresh()
                AddMessage.GONE
            }
            else -> AddMessage.NOT_PLAYABLE
        }
    }

    private fun ask(level: InternetLevel, offset: Int = 0) {
        send(ClementineMessageFactory.buildBrowse(level.id, offset))
    }

    companion object {
        /**
         * What tapping [node] does: opens it if it has nodes below it; otherwise, if it can go on
         * the playlist, plays it if Clementine isn't [playing], or adds it to the end if it is,
         * as tapping a song in the library does.
         */
        fun tapAction(node: InternetNode, playing: Boolean): TapAction = when {
            node.hasChildren -> TapAction.Open
            node.addable -> TapAction.Add(
                if (playing) BrowseAddAction.BROWSE_ADD_ACTION_APPEND else BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NOW)
            else -> TapAction.None
        }

        /**
         * The nodes of a level once a page of them comes: [page] replaces the nodes from [offset]
         * on, and the list is cut to [total]. Null if the page starts past the nodes loaded, which
         * would leave a gap.
         */
        fun merge(loaded: List<InternetNode>, offset: Int, page: List<InternetNode>, total: Int): List<InternetNode>? {
            if (offset < 0 || offset > loaded.size) {
                return null
            }
            val merged = loaded.take(offset) + page + loaded.drop(offset + page.size)
            return merged.take(maxOf(total, 0))
        }
    }
}
