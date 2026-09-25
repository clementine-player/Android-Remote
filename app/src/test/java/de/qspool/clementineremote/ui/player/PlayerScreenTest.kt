package de.qspool.clementineremote.ui.player

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.player.MySong
import de.qspool.clementineremote.ui.theme.ClementineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The player page and controls show Clementine's state, and hand on what the user does. */
@RunWith(RobolectricTestRunner::class)
class PlayerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val song = MySong().apply {
        id = 7
        title = "Clair de lune"
        artist = "Claude Debussy"
        album = "Suite bergamasque"
        genre = "Classical"
        year = "1905"
        length = 300
        isLocal = true
    }

    private fun nowPlaying(
        song: MySong? = this.song,
        state: Clementine.State = Clementine.State.PLAY,
        shuffle: Clementine.ShuffleMode = Clementine.ShuffleMode.OFF,
        repeat: Clementine.RepeatMode = Clementine.RepeatMode.OFF,
    ) = NowPlaying(song, state, positionSeconds = 90, volume = 50, shuffle, repeat)

    @Test
    fun showsTheSong() {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                NowPlayingContent(nowPlaying(), onArtClick = {}, onSeek = {})
            }
        }

        compose.onNodeWithTag("tvTitle").assertTextEquals("Clair de lune")
        compose.onNodeWithTag("tvArtist").assertTextEquals("Claude Debussy")
        compose.onNodeWithTag("tvAlbum").assertTextEquals("Suite bergamasque")
        compose.onNodeWithText("Classical · 1905").assertIsDisplayed()
        compose.onNodeWithTag("tvPosition").assertTextEquals("1:30")
        compose.onNodeWithTag("tvLength").assertTextEquals("5:00")
        compose.onNodeWithTag("sbPosition").assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(90f, 0f..300f)))
    }

    @Test
    fun withoutASongSaysSo() {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                NowPlayingContent(nowPlaying(song = null), onArtClick = {}, onSeek = {})
            }
        }

        compose.onNodeWithTag("tvTitle").assertTextEquals("No Song playing right now")
        compose.onNodeWithTag("sbPosition").assertIsNotEnabled()
    }

    @Test
    fun streamsCannotBeSought() {
        val stream = MySong().apply {
            title = "Radio"
            length = 0
            isLocal = false
        }
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                NowPlayingContent(nowPlaying(song = stream), onArtClick = {}, onSeek = {})
            }
        }

        compose.onNodeWithTag("sbPosition").assertIsNotEnabled()
        compose.onNodeWithTag("tvPosition").assertTextEquals("[S] 1:30")
    }

    @Test
    fun tappingTheArtworkAsksForLyrics() {
        var asked = 0
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                NowPlayingContent(nowPlaying(), onArtClick = { asked++ }, onSeek = {})
            }
        }

        compose.onNodeWithTag("imgArt").performClick()

        assertEquals(1, asked)
    }

    @Test
    fun controlsHandOnWhatTheUserDoes() {
        val done = mutableListOf<String>()
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                PlayerControlsContent(
                    nowPlaying(),
                    onPlayPause = { done += "playPause" },
                    onStopAfterCurrent = { done += "stopAfter" },
                    onPrevious = { done += "previous" },
                    onNext = { done += "next" },
                    onShuffle = { done += "shuffle" },
                    onRepeat = { done += "repeat" },
                )
            }
        }

        compose.onNodeWithTag("btnShuffle").performClick()
        compose.onNodeWithTag("btnPrev").performClick()
        compose.onNodeWithTag("btnPlaypause").performClick()
        compose.onNodeWithTag("btnPlaypause").performTouchInput { longClick() }
        compose.onNodeWithTag("btnNext").performClick()
        compose.onNodeWithTag("btnRepeat").performClick()

        assertEquals(
            listOf("shuffle", "previous", "playPause", "stopAfter", "next", "repeat"), done)
    }

    @Test
    fun controlsShowTheState() {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                PlayerControlsContent(
                    nowPlaying(
                        state = Clementine.State.PAUSE,
                        shuffle = Clementine.ShuffleMode.ALBUMS,
                        repeat = Clementine.RepeatMode.OFF),
                    onPlayPause = {}, onStopAfterCurrent = {}, onPrevious = {}, onNext = {},
                    onShuffle = {}, onRepeat = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithTag("btnShuffle").assertIsOn()
        compose.onNodeWithContentDescription("Shuffle albums").assertIsDisplayed()
        compose.onNodeWithTag("btnRepeat").assertIsOff()
        compose.onNodeWithContentDescription("Don't repeat").assertIsDisplayed()
    }
}
