package de.qspool.clementineremote.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.test.core.app.ApplicationProvider
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.player.MySong
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The widget shows the song and its controls while connected, and otherwise how to connect, as
 * the old widget did.
 */
@RunWith(RobolectricTestRunner::class)
class ClementineWidgetTest {

    private lateinit var nowPlaying: RemoteRepository.NowPlaying

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.Clementine.currentSong = MySong().apply {
            title = "Clair de lune"
            artist = "Claude Debussy"
            album = "Suite bergamasque"
        }
        App.Clementine.state = Clementine.State.PLAY
        RemoteRepository.refresh()
        nowPlaying = RemoteRepository.nowPlaying.value
    }

    @Test
    fun connectedShowsTheSongAndItsControls() {
        val state = widgetState(ConnectionStatus.CONNECTED, nowPlaying, "10.0.2.2")
        assertEquals(WidgetText.Plain("Clair de lune"), state.title)
        assertEquals(WidgetText.Plain("Claude Debussy · Suite bergamasque"), state.subtitle)
        assertEquals(WidgetTap.OPEN_PLAYER, state.tap)
        assertEquals(true, state.controls)
        assertEquals(true, state.playing)
    }

    @Test
    fun disconnectedConnectsToTheLastClementineOrOpensTheApp() {
        val known = widgetState(ConnectionStatus.DISCONNECTED, nowPlaying, "10.0.2.2")
        assertEquals(WidgetText.Resource(R.string.widget_connect_to), known.title)
        assertEquals(WidgetText.Plain("10.0.2.2"), known.subtitle)
        assertEquals(WidgetTap.CONNECT, known.tap)
        assertEquals(false, known.controls)

        val unknown = widgetState(ConnectionStatus.IDLE, nowPlaying, null)
        assertEquals(WidgetText.Resource(R.string.widget_not_connected), unknown.title)
        assertEquals(WidgetTap.OPEN_CONNECT, unknown.tap)

        val failed = widgetState(ConnectionStatus.NO_CONNECTION, nowPlaying, "10.0.2.2")
        assertEquals(WidgetText.Resource(R.string.widget_couldnt_connect), failed.title)
        assertEquals(WidgetTap.OPEN_CONNECT, failed.tap)
    }

    private fun show(size: DpSize) = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(size)
        provideComposable {
            GlanceTheme(colors = ClementineWidget.colors) {
                WidgetContent(widgetState(ConnectionStatus.CONNECTED, nowPlaying, "10.0.2.2"))
            }
        }
        onNode(hasTestTag("widgetTitle")).assert(hasText("Clair de lune"))
        onNode(hasTestTag("widgetPlayPause")).assertExists()
        // Playing, so it pauses.
        onNode(hasContentDescription("Pause")).assertExists()
        if (size.width >= ClementineWidget.WIDE.width) {
            onNode(hasTestTag("widgetNext")).assertExists()
        } else {
            onNode(hasTestTag("widgetNext")).assertDoesNotExist()
        }
    }

    @Test
    fun wideHasNextAndNarrowKeepsToPlayPause() {
        show(DpSize(300.dp, 72.dp))
        show(DpSize(180.dp, 72.dp))
    }
}
