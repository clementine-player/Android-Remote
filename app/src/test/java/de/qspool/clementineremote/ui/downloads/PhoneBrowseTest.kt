package de.qspool.clementineremote.ui.downloads

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The songs on this phone are grouped into albums and artists to browse. */
@RunWith(RobolectricTestRunner::class)
class PhoneBrowseTest {

    private fun song(title: String, artist: String, album: String = "", track: Int = 0) =
        DownloadedSong(title, artist, Uri.parse("content://media/$title"), album, track)

    private val passepied = song("Passepied", "Claude Debussy", "Suite bergamasque", 4)
    private val clair = song("Clair de lune", "Claude Debussy", "Suite bergamasque", 3)
    private val prelude = song("Prélude", "Claude Debussy", "Suite bergamasque")
    private val soWhat = song("So What", "Miles Davis", "Kind of Blue", 1)
    private val loose = song("Gymnopédie No. 1", "Erik Satie")

    @Test
    fun groupsAlbumsByTitleAndArtistInTrackOrder() {
        assertEquals(
            listOf(
                PhoneAlbum("Kind of Blue", "Miles Davis", listOf(soWhat)),
                // Without a track number, a song keeps its place before the numbered ones.
                PhoneAlbum("Suite bergamasque", "Claude Debussy", listOf(prelude, clair, passepied)),
            ),
            albums(listOf(passepied, clair, prelude, soWhat, loose)),
        )
    }

    @Test
    fun listsEachArtistOnceByName() {
        assertEquals(
            listOf("Claude Debussy", "Erik Satie", "Miles Davis"),
            artists(listOf(soWhat, passepied, loose, clair, song("Untitled", ""))),
        )
    }
}
