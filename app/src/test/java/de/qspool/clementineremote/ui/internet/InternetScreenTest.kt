package de.qspool.clementineremote.ui.internet

import android.os.Looper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import de.qspool.clementineremote.backend.RemoteRepository.BrowseMessage
import de.qspool.clementineremote.backend.RemoteRepository.Browsing
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseAddAction
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseAddResult
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseChildren
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseNode
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseNodeKind
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseBrowse
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseBrowseAdd
import de.qspool.clementineremote.ui.theme.ClementineTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The Internet screen follows the connection, and shows each level's state. */
@RunWith(RobolectricTestRunner::class)
class InternetScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val services = listOf(
        InternetNode("soma", "SomaFM", kind = NodeKind.SERVICE, hasChildren = true),
        InternetNode("subsonic", "Subsonic", kind = NodeKind.SERVICE, hasChildren = true),
    )

    @Test
    fun followsTheConnection() {
        val sent = mutableListOf<ClementineMessage>()
        val browsing = MutableStateFlow(Browsing(supported = true, connection = 1))
        val messages = MutableSharedFlow<BrowseMessage>(extraBufferCapacity = 8)
        val internet = InternetViewModel(send = { sent += it }, browsing = browsing, messages = messages, playing = { false })
        idle()
        assertEquals(MsgType.REQUEST_BROWSE, sent.single().messageType)

        messages.tryEmit(BrowseMessage.Browse(1, ResponseBrowse.newBuilder().setNodeId("")
            .addNodes(BrowseNode.newBuilder().setNodeId("n1").setTitle("SomaFM")
                .setKind(BrowseNodeKind.BROWSE_NODE_KIND_SERVICE).setChildren(BrowseChildren.BROWSE_CHILDREN_SOME))
            .setTotalCount(1).build()))
        idle()
        assertEquals(listOf("SomaFM"), internet.state.value.shown.nodes.map { it.title })

        internet.tap(internet.state.value.shown.nodes.single())
        assertEquals(2, internet.state.value.levels.size)

        // Reconnected: the ids are forgotten, and the services asked for again.
        sent.clear()
        browsing.value = Browsing(supported = true, connection = 2)
        idle()
        assertEquals(1, internet.state.value.levels.size)
        assertTrue(internet.state.value.shown.nodes.isEmpty())
        assertEquals("", sent.single().message.requestBrowse.nodeId)

        // A late answer from the connection before is ignored.
        messages.tryEmit(BrowseMessage.Browse(1, ResponseBrowse.newBuilder().setNodeId("")
            .addNodes(BrowseNode.newBuilder().setNodeId("n1").setTitle("Old")).setTotalCount(1).build()))
        idle()
        assertTrue(internet.state.value.shown.nodes.isEmpty())
    }

    @Test
    fun saysHowAddingWent() {
        val sent = mutableListOf<ClementineMessage>()
        val messages = MutableSharedFlow<BrowseMessage>(extraBufferCapacity = 8)
        val internet = InternetViewModel(
            send = { sent += it },
            browsing = MutableStateFlow(Browsing(supported = true, connection = 1)),
            messages = messages,
            playing = { true },
        )
        val told = mutableListOf<AddMessage>()
        compose.setContent {
            LaunchedEffect(Unit) { internet.added.collect { told += it } }
        }
        idle()

        // Clementine is playing: a tapped stream is added to the end.
        internet.tap(InternetNode("s", "Groove Salad", kind = NodeKind.STREAM, addable = true))
        assertEquals(BrowseAddAction.BROWSE_ADD_ACTION_APPEND, sent.last().message.requestBrowseAdd.action)
        messages.tryEmit(BrowseMessage.AddResult(1, ResponseBrowseAdd.newBuilder().addNodeIds("s")
            .setResult(BrowseAddResult.BROWSE_ADD_RESULT_ADDED).build()))
        compose.waitForIdle()
        assertEquals(listOf(AddMessage.ADDED), told)
    }

    private fun show(state: InternetState, done: MutableList<String> = mutableListOf()): MutableList<String> {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                InternetContent(
                    state,
                    onTap = { done += "tap ${it.id}" },
                    onAdd = { node, action -> done += "add ${node.id} ${action.name}" },
                    onBack = { done += "back" },
                    onRefresh = { done += "refresh" },
                    onLoadMore = { done += "more" },
                )
            }
        }
        return done
    }

    @Test
    fun showsTheServices() {
        val done = show(InternetState(listOf(InternetLevel(status = LevelStatus.READY, nodes = services, totalCount = 2))))

        compose.onNodeWithText("Internet").assertIsDisplayed()
        compose.onNodeWithText("SomaFM").performClick()
        assertEquals(listOf("tap soma"), done)
    }

    @Test
    fun loadingBeforeTheFirstAnswer() {
        show(InternetState())
        compose.onNodeWithTag("internetLoading").assertIsDisplayed()
        compose.onNodeWithText("Loading…").assertIsDisplayed()
    }

    @Test
    fun loadingMoreShowsTheNodesWithProgress() {
        show(InternetState(listOf(InternetLevel(status = LevelStatus.LOADING, nodes = services, totalCount = 2))))
        compose.onNodeWithTag("internetProgress").assertIsDisplayed()
        compose.onNodeWithText("Subsonic").assertIsDisplayed()
    }

    @Test
    fun needsSetupAndNothingHere() {
        val subsonic = services[1]
        show(InternetState(listOf(
            InternetLevel(status = LevelStatus.READY, nodes = services, totalCount = 2),
            InternetLevel(opened = subsonic, status = LevelStatus.NEEDS_SETUP,
                message = "Set up Subsonic in Clementine's settings on the computer"),
        )))
        compose.onNodeWithText("Set up in Clementine").assertIsDisplayed()
        compose.onNodeWithText("Set up Subsonic in Clementine's settings on the computer").assertIsDisplayed()
        compose.onNodeWithTag("internetTitle").assertTextEquals("Subsonic")
    }

    @Test
    fun nothingHere() {
        show(InternetState(listOf(
            InternetLevel(status = LevelStatus.READY, nodes = services, totalCount = 2),
            InternetLevel(opened = services[0], status = LevelStatus.READY),
        )))
        compose.onNodeWithText("Nothing here").assertIsDisplayed()
        // Not something that plays: no count, no buttons.
        compose.onNodeWithTag("internetCount").assertDoesNotExist()
        compose.onNodeWithTag("internetPlayAll").assertDoesNotExist()
    }

    @Test
    fun anOpenedAlbumPlaysOrAddsItAll() {
        val album = InternetNode("album", "Gymnopédies", hasChildren = true, addable = true)
        val tracks = listOf(
            InternetNode("t1", "Gymnopédie No. 1", subtitle = "Erik Satie", kind = NodeKind.TRACK, addable = true),
            InternetNode("t2", "Gymnopédie No. 2", subtitle = "Erik Satie", kind = NodeKind.TRACK, addable = true),
        )
        val done = show(InternetState(listOf(
            InternetLevel(status = LevelStatus.READY, nodes = services, totalCount = 2),
            InternetLevel(opened = album, status = LevelStatus.READY, nodes = tracks, totalCount = 3),
        )))

        compose.onNodeWithTag("internetTitle").assertTextEquals("Gymnopédies")
        compose.onNodeWithTag("internetCount").assertTextEquals("3 items")
        compose.onNodeWithTag("internetPlayAll").performClick()
        compose.onNodeWithTag("internetAddAll").performClick()
        compose.onNodeWithTag("internetBack").performClick()
        // The last row shows, and there's more: more is asked for.
        assertEquals(
            listOf("more", "add album BROWSE_ADD_ACTION_PLAY_NOW", "add album BROWSE_ADD_ACTION_APPEND", "back"),
            done)
    }

    @Test
    fun anAlbumsCountShowsOnceClementineSays() {
        val album = InternetNode("album", "Gymnopédies", hasChildren = true, addable = true)
        show(InternetState(listOf(
            InternetLevel(status = LevelStatus.READY, nodes = services, totalCount = 2),
            InternetLevel(opened = album),
        )))
        compose.onNodeWithTag("internetLoading").assertIsDisplayed()
        compose.onNodeWithTag("internetCount").assertDoesNotExist()
        compose.onNodeWithTag("internetPlayAll").assertIsDisplayed()
    }

    @Test
    fun longPressOffersTheWaysToAdd() {
        val stream = InternetNode("s", "Groove Salad", kind = NodeKind.STREAM, addable = true)
        val done = show(InternetState(listOf(InternetLevel(status = LevelStatus.READY, nodes = listOf(stream), totalCount = 1))))

        compose.onNodeWithText("Groove Salad").performTouchInput { longClick() }
        compose.onNodeWithText("Play now").assertIsDisplayed()
        compose.onNodeWithText("Add to playlist").assertIsDisplayed()
        compose.onNodeWithText("Replace playlist").assertIsDisplayed()
        compose.onNodeWithText("Play next").performClick()
        assertEquals(listOf("add s BROWSE_ADD_ACTION_PLAY_NEXT"), done)
    }

    @Test
    fun aNodeThatDoesNothingIsShownButDoesNothing() {
        val label = InternetNode("x", "Not playable")
        val done = show(InternetState(listOf(InternetLevel(status = LevelStatus.READY, nodes = listOf(label), totalCount = 1))))

        compose.onNodeWithText("Not playable").assertIsDisplayed().performClick()
        compose.onNodeWithText("Not playable").performTouchInput { longClick() }
        assertFalse(done.any { it.startsWith("tap") || it.startsWith("add") })
    }
}
