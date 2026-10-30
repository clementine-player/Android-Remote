package de.qspool.clementineremote.backend.sync

import android.net.Uri
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncDelivery
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Songs kept on the phone show as their tags have them. */
@RunWith(RobolectricTestRunner::class)
class KeptSongTest {

    private fun item(song: SongMetadata, uri: String = "content://media/external/audio/media/7") = SyncedItem(
        serverId = "studio", itemKey = "k", version = "1", uri = uri, mimeType = "audio/flac",
        sizeBytes = 1, delivery = SyncDelivery.SYNC_DELIVERY_UNSPECIFIED, artKey = null, metadata = song.toByteArray(), addedAt = 0,
    )

    @Test
    fun readsTheTags() {
        val song = SongMetadata.newBuilder()
            .setTitle("So What").setArtist("Miles Davis").setAlbum("Kind of Blue").setPrettyLength("9:22")
            .build()
        assertEquals(
            KeptSong("k", "So What", "Miles Davis", "Kind of Blue", "9:22", Uri.parse("content://media/external/audio/media/7")),
            KeptSong.of(item(song)),
        )
    }

    @Test
    fun withoutTagsUsesTheAlbumArtistAndTheFileName() {
        val song = SongMetadata.newBuilder().setAlbumartist("Various Artists").build()
        val kept = KeptSong.of(item(song, "file:///music/01%20Intro.flac"))
        assertEquals("01 Intro", kept.title)
        assertEquals("Various Artists", kept.artist)
        assertEquals("", kept.length)
    }
}
