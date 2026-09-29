package de.qspool.clementineremote.ui.internet

import com.google.protobuf.ByteString
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseAddAction
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseAddResult
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseChildren
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseNode
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseNodeKind
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowsePlayability
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestBrowse
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseBrowse
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseBrowseAdd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Browsing Clementine's Internet sidebar: which levels show, how pages of nodes come together,
 * going back up when a node is gone, starting again on a new connection, and what a tap does.
 */
class InternetBrowserTest {

    private val sent = mutableListOf<ClementineMessage>()

    private val browser = InternetBrowser { sent += it }

    /** The browse requests sent, as (node id, offset), and forgets them. */
    private fun asked(): List<Pair<String, Int>> {
        val asked = sent.filter { it.messageType == MsgType.REQUEST_BROWSE }
            .map { it.message.requestBrowse.let { r: RequestBrowse -> r.nodeId to r.offset } }
        sent.clear()
        return asked
    }

    private fun node(
        id: String,
        kind: BrowseNodeKind = BrowseNodeKind.BROWSE_NODE_KIND_FOLDER,
        children: Boolean = true,
        addable: Boolean = false,
    ): BrowseNode = BrowseNode.newBuilder()
        .setNodeId(id)
        .setTitle("Title $id")
        .setKind(kind)
        .setChildren(if (children) BrowseChildren.BROWSE_CHILDREN_SOME else BrowseChildren.BROWSE_CHILDREN_NONE)
        .setPlayability(if (addable) BrowsePlayability.BROWSE_PLAYABILITY_ADDABLE else BrowsePlayability.BROWSE_PLAYABILITY_NONE)
        .build()

    private fun nodes(from: Int, count: Int) = (from until from + count).map { node("n$it") }

    private fun answer(
        nodeId: String,
        nodes: List<BrowseNode> = emptyList(),
        offset: Int = 0,
        total: Int = offset + nodes.size,
        state: BrowseState = BrowseState.BROWSE_STATE_READY,
        message: String? = null,
    ) {
        browser.onBrowse(ResponseBrowse.newBuilder()
            .setNodeId(nodeId)
            .setState(state)
            .addAllNodes(nodes)
            .setOffset(offset)
            .setTotalCount(total)
            .apply { message?.let { setMessage(it) } }
            .build())
    }

    private fun addResult(ids: List<String>, result: BrowseAddResult) =
        browser.onAddResult(ResponseBrowseAdd.newBuilder().addAllNodeIds(ids).setResult(result).build())

    /** Connected, with the services shown: "soma" and "subsonic". */
    private fun showServices() {
        browser.reset(supported = true)
        answer("", listOf(
            node("soma", BrowseNodeKind.BROWSE_NODE_KIND_SERVICE),
            node("subsonic", BrowseNodeKind.BROWSE_NODE_KIND_SERVICE)))
        sent.clear()
    }

    @Test
    fun asksForTheServicesOnlyWhenClementineCanBeBrowsed() {
        browser.reset(supported = false)
        assertTrue(sent.isEmpty())
        assertEquals(LevelStatus.LOADING, browser.shown.status)

        browser.reset(supported = true)
        assertEquals(listOf("" to 0), asked())
        // Before the first answer, the level is loading, with nothing in it.
        assertNull(browser.shown.opened)
        assertEquals(LevelStatus.LOADING, browser.shown.status)
        assertTrue(browser.shown.nodes.isEmpty())
    }

    @Test
    fun showsTheServices() {
        browser.reset(supported = true)
        val icon = ByteString.copyFrom(byteArrayOf(1, 2, 3))
        answer("", listOf(
            node("soma", BrowseNodeKind.BROWSE_NODE_KIND_SERVICE).toBuilder().setIconPng(icon).build(),
            node("radio", BrowseNodeKind.BROWSE_NODE_KIND_SERVICE)))

        val shown = browser.shown
        assertEquals(LevelStatus.READY, shown.status)
        assertEquals(listOf("soma", "radio"), shown.nodes.map { it.id })
        assertEquals(icon, shown.nodes[0].icon)
        assertNull(shown.nodes[1].icon)
        assertEquals(NodeKind.SERVICE, shown.nodes[0].kind)
        assertEquals("Title soma", shown.nodes[0].title)
        assertNull(shown.nodes[0].subtitle)
    }

    @Test
    fun readsWhatANodeIs() {
        val track = InternetNode.of(node("t", BrowseNodeKind.BROWSE_NODE_KIND_TRACK, children = false, addable = true)
            .toBuilder().setSubtitle("Erik Satie").build())
        assertEquals(NodeKind.TRACK, track.kind)
        assertEquals("Erik Satie", track.subtitle)
        assertFalse(track.hasChildren)
        assertTrue(track.addable)

        // Unspecified is a folder; unspecified children and playability are none.
        val unspecified = InternetNode.of(BrowseNode.newBuilder().setNodeId("u").setTitle("U").build())
        assertEquals(NodeKind.FOLDER, unspecified.kind)
        assertFalse(unspecified.hasChildren)
        assertFalse(unspecified.addable)

        assertEquals(NodeKind.STREAM, InternetNode.of(node("s", BrowseNodeKind.BROWSE_NODE_KIND_STREAM)).kind)
        assertEquals(NodeKind.SMART_PLAYLIST, InternetNode.of(node("p", BrowseNodeKind.BROWSE_NODE_KIND_SMART_PLAYLIST)).kind)
    }

    @Test
    fun opensANodeAndGoingBackAsksForTheLevelAgain() {
        showServices()
        val soma = browser.shown.nodes[0]

        browser.open(soma)
        assertEquals(listOf("soma" to 0), asked())
        assertEquals(soma, browser.shown.opened)
        assertEquals(LevelStatus.LOADING, browser.shown.status)

        answer("soma", listOf(node("groove", BrowseNodeKind.BROWSE_NODE_KIND_STREAM, children = false, addable = true)))
        assertEquals(listOf("groove"), browser.shown.nodes.map { it.id })

        // Back up: the services, still shown, and asked for again so Clementine watches them.
        assertTrue(browser.back())
        assertEquals(listOf("" to 0), asked())
        assertEquals(listOf("soma", "subsonic"), browser.shown.nodes.map { it.id })
        assertFalse(browser.back())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun onlyNodesWithChildrenOpen() {
        showServices()
        browser.open(InternetNode("leaf", "Leaf", hasChildren = false, addable = true))
        assertEquals(1, browser.levels.size)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun ignoresAnswersForAnotherLevel() {
        showServices()
        browser.open(browser.shown.nodes[0])

        // The services changed, but Clementine now watches SomaFM: a late answer.
        answer("", nodes(0, 3))
        assertTrue(browser.shown.nodes.isEmpty())
        assertEquals(LevelStatus.LOADING, browser.shown.status)
    }

    @Test
    fun statesOfALevel() {
        showServices()
        browser.open(browser.shown.nodes[1])

        answer("subsonic", state = BrowseState.BROWSE_STATE_LOADING)
        assertEquals(LevelStatus.LOADING, browser.shown.status)

        answer("subsonic", state = BrowseState.BROWSE_STATE_NEEDS_SETUP,
            message = "Set up Subsonic in Clementine's settings on the computer")
        assertEquals(LevelStatus.NEEDS_SETUP, browser.shown.status)
        assertEquals("Set up Subsonic in Clementine's settings on the computer", browser.shown.message)

        answer("subsonic", nodes(0, 2), state = BrowseState.BROWSE_STATE_LOADING)
        assertEquals(LevelStatus.LOADING, browser.shown.status)
        assertEquals(2, browser.shown.nodes.size)
        assertNull(browser.shown.message)

        answer("subsonic")
        assertEquals(LevelStatus.READY, browser.shown.status)
        assertTrue(browser.shown.nodes.isEmpty())
    }

    @Test
    fun goneGoesBackUpAndAsksForTheLevelThere() {
        showServices()
        browser.open(browser.shown.nodes[0])
        answer("soma", nodes(0, 2))
        browser.open(browser.shown.nodes[1])
        sent.clear()

        answer("n1", state = BrowseState.BROWSE_STATE_GONE)
        assertEquals("soma", browser.shown.id)
        assertEquals(listOf("soma" to 0), asked())

        // SomaFM went too: up again, to the services.
        answer("soma", state = BrowseState.BROWSE_STATE_GONE)
        assertNull(browser.shown.opened)
        assertEquals(listOf("" to 0), asked())
    }

    @Test
    fun loadsMorePagesAndKeepsThem() {
        showServices()
        browser.open(browser.shown.nodes[0])
        sent.clear()

        answer("soma", nodes(0, 500), total = 1200)
        assertTrue(browser.shown.hasMore)

        // The last row shows: the next page, once.
        browser.loadMore()
        browser.loadMore()
        assertEquals(listOf("soma" to 500), asked())

        answer("soma", nodes(500, 500), offset = 500, total = 1200)
        assertEquals(1000, browser.shown.nodes.size)
        browser.loadMore()
        assertEquals(listOf("soma" to 1000), asked())
        answer("soma", nodes(1000, 200), offset = 1000, total = 1200)
        assertEquals((0 until 1200).map { "n$it" }, browser.shown.nodes.map { it.id })
        assertFalse(browser.shown.hasMore)

        // Everything's loaded: nothing more to ask for.
        browser.loadMore()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aPageReplacesTheRowsFromItsOffsetAndTheListIsCutToTheTotal() {
        showServices()
        browser.open(browser.shown.nodes[0])
        answer("soma", nodes(0, 500), total = 1000)
        answer("soma", nodes(500, 500), offset = 500, total = 1000)

        // The watched page changed: its rows are replaced, the others kept.
        answer("soma", listOf(node("new500"), node("new501")) + nodes(502, 498), offset = 500, total = 1000)
        assertEquals("n499", browser.shown.nodes[499].id)
        assertEquals("new500", browser.shown.nodes[500].id)
        assertEquals("n999", browser.shown.nodes[999].id)

        // Refreshed from the first page, with fewer in all: cut to the new total.
        answer("soma", nodes(0, 500).map { it.toBuilder().setTitle("Renamed").build() }, total = 600)
        assertEquals(600, browser.shown.nodes.size)
        assertEquals(600, browser.shown.totalCount)
        assertEquals("Renamed", browser.shown.nodes[0].title)
        assertEquals("new500", browser.shown.nodes[500].id)
        assertFalse(browser.shown.hasMore)
    }

    @Test
    fun mergeLeavesNoGaps() {
        val loaded = listOf(InternetNode("a", "A"), InternetNode("b", "B"))
        assertNull(InternetBrowser.merge(loaded, 3, listOf(InternetNode("d", "D")), 4))
        assertEquals(listOf("a", "b", "c"),
            InternetBrowser.merge(loaded, 2, listOf(InternetNode("c", "C")), 3)!!.map { it.id })
        assertEquals(listOf("x"), InternetBrowser.merge(loaded, 0, listOf(InternetNode("x", "X")), 1)!!.map { it.id })
        assertEquals(emptyList<InternetNode>(), InternetBrowser.merge(loaded, 0, emptyList(), 0))
    }

    @Test
    fun aNewConnectionStartsAgainFromTheServices() {
        showServices()
        browser.open(browser.shown.nodes[0])
        answer("soma", nodes(0, 2))
        browser.add(browser.shown.nodes[0].copy(addable = true), BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NEXT)
        sent.clear()

        browser.reset(supported = true)
        assertEquals(1, browser.levels.size)
        assertNull(browser.shown.opened)
        assertTrue(browser.shown.nodes.isEmpty())
        assertEquals(LevelStatus.LOADING, browser.shown.status)
        assertEquals(listOf("" to 0), asked())
        // What was being added on the old connection is forgotten: an answer is taken as appended.
        assertEquals(AddMessage.ADDED, addResult(listOf("n0"), BrowseAddResult.BROWSE_ADD_RESULT_ADDED))

        // An older Clementine: nothing to ask for.
        browser.reset(supported = false)
        assertTrue(sent.isEmpty())
        browser.refresh()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun tapOpensPlaysOrAdds() {
        val folder = InternetNode("f", "Folder", hasChildren = true)
        val album = InternetNode("a", "Album", hasChildren = true, addable = true)
        val stream = InternetNode("s", "Stream", kind = NodeKind.STREAM, addable = true)
        val nothing = InternetNode("n", "Nothing")

        assertEquals(TapAction.Open, InternetBrowser.tapAction(folder, playing = false))
        // Something that opens opens, even if it can go on the playlist.
        assertEquals(TapAction.Open, InternetBrowser.tapAction(album, playing = true))
        // A track or stream plays, unless Clementine is playing: then it's added.
        assertEquals(TapAction.Add(BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NOW), InternetBrowser.tapAction(stream, playing = false))
        assertEquals(TapAction.Add(BrowseAddAction.BROWSE_ADD_ACTION_APPEND), InternetBrowser.tapAction(stream, playing = true))
        assertEquals(TapAction.None, InternetBrowser.tapAction(nothing, playing = false))
    }

    @Test
    fun tapSendsWhatItDoes() {
        showServices()
        browser.tap(InternetNode("s", "Stream", kind = NodeKind.STREAM, addable = true), playing = true)
        val add = sent.single().message
        assertEquals(MsgType.REQUEST_BROWSE_ADD, add.type)
        assertEquals(listOf("s"), add.requestBrowseAdd.nodeIdsList)
        assertEquals(BrowseAddAction.BROWSE_ADD_ACTION_APPEND, add.requestBrowseAdd.action)
        sent.clear()

        browser.tap(InternetNode("n", "Nothing"), playing = false)
        assertTrue(sent.isEmpty())

        browser.tap(browser.shown.nodes[0], playing = false)
        assertEquals(listOf("soma" to 0), asked())
    }

    @Test
    fun saysHowAddingWent() {
        showServices()
        val stream = InternetNode("s", "Stream", kind = NodeKind.STREAM, addable = true)
        val add = { action: BrowseAddAction -> browser.add(stream, action) }

        add(BrowseAddAction.BROWSE_ADD_ACTION_APPEND)
        assertEquals(AddMessage.ADDED, addResult(listOf("s"), BrowseAddResult.BROWSE_ADD_RESULT_ADDED))
        add(BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NEXT)
        assertEquals(AddMessage.PLAYING_NEXT, addResult(listOf("s"), BrowseAddResult.BROWSE_ADD_RESULT_ADDED))
        // Playing shows in the player.
        add(BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NOW)
        assertNull(addResult(listOf("s"), BrowseAddResult.BROWSE_ADD_RESULT_ADDED))
        add(BrowseAddAction.BROWSE_ADD_ACTION_REPLACE)
        assertNull(addResult(listOf("s"), BrowseAddResult.BROWSE_ADD_RESULT_ADDED))

        add(BrowseAddAction.BROWSE_ADD_ACTION_APPEND)
        assertEquals(AddMessage.NOT_PLAYABLE, addResult(listOf("s"), BrowseAddResult.BROWSE_ADD_RESULT_NOT_PLAYABLE))
        sent.clear()

        // Gone: said, and the level is asked for again.
        add(BrowseAddAction.BROWSE_ADD_ACTION_APPEND)
        sent.clear()
        assertEquals(AddMessage.GONE, addResult(listOf("s"), BrowseAddResult.BROWSE_ADD_RESULT_GONE))
        assertEquals(listOf("" to 0), asked())
    }

    @Test
    fun answersAreMatchedToWhatWasAdded() {
        showServices()
        browser.add(InternetNode("a", "A", addable = true), BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NEXT)
        browser.add(InternetNode("b", "B", addable = true), BrowseAddAction.BROWSE_ADD_ACTION_APPEND)

        assertEquals(AddMessage.ADDED, addResult(listOf("b"), BrowseAddResult.BROWSE_ADD_RESULT_ADDED))
        assertEquals(AddMessage.PLAYING_NEXT, addResult(listOf("a"), BrowseAddResult.BROWSE_ADD_RESULT_ADDED))
    }

    @Test
    fun onlyWhatCanGoOnThePlaylistIsAdded() {
        showServices()
        browser.add(InternetNode("f", "Folder", hasChildren = true), BrowseAddAction.BROWSE_ADD_ACTION_APPEND)
        assertTrue(sent.isEmpty())
    }
}
