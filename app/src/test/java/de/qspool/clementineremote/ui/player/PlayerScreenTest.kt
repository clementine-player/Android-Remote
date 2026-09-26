package de.qspool.clementineremote.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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

/** The player and the song details sheet show Clementine's state, and hand on what the user does. */
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

    private val done = mutableListOf<String>()

    private val actions = object : PlayerActions {
        override fun onCollapse() {
            done += "collapse"
        }

        override fun onDetails(lyrics: Boolean) {
            done += if (lyrics) "lyrics" else "details"
        }

        override fun onQueue() {
            done += "queue"
        }

        override fun onDownload() {
            done += "download"
        }
    }

    private fun showPlayer(nowPlaying: NowPlaying = nowPlaying(), playingFrom: String? = "Playlist 1", lastFm: Boolean = true) {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                PlayerContent(
                    nowPlaying,
                    playingFrom = playingFrom,
                    lastFm = lastFm,
                    actions = actions,
                    onSeek = { done += "seek $it" },
                    onVolume = { done += "volume $it" },
                    onStop = { done += "stop" },
                    onLove = { done += "love" },
                    onBan = { done += "ban" },
                    controls = {},
                )
            }
        }
    }

    @Test
    fun showsTheSong() {
        showPlayer()

        compose.onNodeWithTag("playingFrom").assertTextEquals("Playlist 1")
        compose.onNodeWithTag("tvTitle").assertTextEquals("Clair de lune")
        compose.onNodeWithTag("tvArtist").assertTextEquals("Claude Debussy")
        compose.onNodeWithTag("tvAlbum").assertTextEquals("Suite bergamasque")
        compose.onNodeWithTag("tvPosition").assertTextEquals("1:30")
        compose.onNodeWithTag("tvLength").assertTextEquals("5:00")
        compose.onNodeWithTag("sbPosition").assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(90f, 0f..300f)))
        // The volume is a button, not a slider beside the seek bar.
        compose.onNodeWithContentDescription("Clementine volume, 50%").assertIsDisplayed()
        compose.onNodeWithTag("volumeSlider").assertDoesNotExist()
    }

    @Test
    fun withoutASongSaysSo() {
        showPlayer(nowPlaying(song = null))

        compose.onNodeWithTag("tvTitle").assertTextEquals("No Song playing right now")
        compose.onNodeWithTag("sbPosition").assertIsNotEnabled()
        compose.onNodeWithTag("btnLove").assertDoesNotExist()
    }

    @Test
    fun streamsCannotBeSought() {
        val stream = MySong().apply {
            title = "Radio"
            length = 0
            isLocal = false
        }
        showPlayer(nowPlaying(song = stream))

        compose.onNodeWithTag("sbPosition").assertIsNotEnabled()
        compose.onNodeWithTag("tvPosition").assertTextEquals("[S] 1:30")
    }

    @Test
    fun buttonsHandOnWhatTheUserDoes() {
        showPlayer()

        compose.onNodeWithTag("imgArt").performClick()
        compose.onNodeWithTag("btnDetails").performClick()
        compose.onNodeWithTag("btnQueue").performClick()
        compose.onNodeWithTag("btnDownload").performClick()
        compose.onNodeWithTag("btnLove").performClick()
        // Loved once only.
        compose.onNodeWithTag("btnLove").assertIsOn().performClick()
        compose.onNodeWithTag("btnPlayerMore").performClick()
        compose.onNodeWithTag("menuStop").performClick()
        compose.onNodeWithTag("btnPlayerMore").performClick()
        compose.onNodeWithTag("menuBan").performClick()
        compose.onNodeWithTag("btnCollapse").performClick()

        assertEquals(
            listOf("lyrics", "details", "queue", "download", "love", "stop", "ban", "collapse"),
            done)
    }

    @Test
    fun theVolumeButtonPopsUpAnUprightSlider() {
        showPlayer()

        compose.onNodeWithTag("btnVolume").performClick()
        compose.onNodeWithTag("volumeLevel").assertTextEquals("50%")
        compose.onNodeWithTag("volumeSlider")
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(50f, 0f..100f)))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        // Shown at once, before Clementine answers.
        compose.onNodeWithTag("volumeLevel").assertTextEquals("80%")
        compose.onNodeWithTag("btnVolumeUp").performClick()
        compose.onNodeWithTag("btnVolumeDown").performClick()
        compose.onNodeWithTag("btnVolumeDown").performClick()

        assertEquals(listOf("volume 80", "volume 85", "volume 80", "volume 75"), done)
        compose.onNodeWithContentDescription("Clementine volume, 75%").assertExists()
    }

    @Test
    fun withoutLastFmNeitherLovesNorBans() {
        showPlayer(lastFm = false)

        compose.onNodeWithTag("btnLove").assertDoesNotExist()
        compose.onNodeWithTag("btnPlayerMore").performClick()
        compose.onNodeWithTag("menuStop").assertIsDisplayed()
        compose.onNodeWithTag("menuBan").assertDoesNotExist()
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

    private fun showDetails(song: MySong?, lyrics: Lyrics = Lyrics.NotAsked, lyricsShown: Boolean = false, onRate: (Int) -> Unit = {}) {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                var shown by remember { mutableStateOf(lyricsShown) }
                SongDetailsContent(
                    song,
                    lyrics,
                    lyricsShown = shown,
                    onShowLyrics = { shown = it },
                    onRequestLyrics = { done += "request lyrics" },
                    onRate = onRate,
                )
            }
        }
    }

    @Test
    fun songDetailsShowWhatClementineKnows() {
        val rated = mutableListOf<Int>()
        song.track = 3
        song.disc = 0
        song.playcount = 12
        song.rating = 0.7f
        showDetails(song, onRate = { rated += it })

        compose.onNodeWithTag("siTitle").assertTextEquals("Clair de lune")
        compose.onNodeWithText("Claude Debussy · Suite bergamasque").assertIsDisplayed()
        compose.onNodeWithText("Classical").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1905").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12").performScrollTo().assertIsDisplayed()
        // No disc number from Clementine, so no disc row.
        compose.onNodeWithText("Disc").assertDoesNotExist()

        compose.onNodeWithTag("siStar4").performScrollTo().performClick()
        assertEquals(listOf(4), rated)
    }

    @Test
    fun songDetailsWithoutASongSaySo() {
        showDetails(null)

        compose.onNodeWithTag("siTitle").assertTextEquals("No Song playing right now")
        compose.onNodeWithTag("siRating").assertDoesNotExist()
    }

    @Test
    fun lyricsAreAskedForWhenFirstShown() {
        showDetails(song)

        compose.onNodeWithTag("siLyricsTab").performClick()

        compose.onNodeWithTag("lyricsLoading").assertIsDisplayed()
        assertEquals(listOf("request lyrics"), done)
    }

    @Test
    fun showsTheLyricsFound() {
        showDetails(song, Lyrics.Found("lyrics.wikia.com", "La la la"), lyricsShown = true)

        compose.onNodeWithTag("lyrics").assertTextEquals("La la la")
        compose.onNodeWithTag("siDetailsTab").performClick()
        compose.onNodeWithTag("siRating").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun saysWhenNoLyricsWereFound() {
        showDetails(song, Lyrics.None, lyricsShown = true)

        compose.onNodeWithTag("lyricsNone").assertTextEquals("No lyrics found.")
    }
}
