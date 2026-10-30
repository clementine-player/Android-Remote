package de.qspool.clementineremote.backend.sync

import android.net.Uri
import com.google.protobuf.InvalidProtocolBufferException
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata

/** A song kept on this phone, to show and play. */
data class KeptSong(
    val itemKey: String,
    val title: String,
    val artist: String,
    val album: String,
    /** As Clementine writes it, such as "5:36"; empty if it didn't say. */
    val length: String,
    val uri: Uri,
) {
    companion object {
        /** [item] as its tags have it; a song without a title is named after its file. */
        fun of(item: SyncedItem): KeptSong {
            val song = try {
                SongMetadata.parseFrom(item.metadata)
            } catch (e: InvalidProtocolBufferException) {
                SongMetadata.getDefaultInstance()
            }
            val uri = Uri.parse(item.uri)
            return KeptSong(
                itemKey = item.itemKey,
                title = song.title.ifEmpty { uri.lastPathSegment?.substringBeforeLast('.').orEmpty() },
                artist = song.artist.ifEmpty { song.albumartist },
                album = song.album,
                length = song.prettyLength,
                uri = uri,
            )
        }
    }
}
