package de.qspool.clementineremote.backend.sync

import android.database.sqlite.SQLiteDatabase
import androidx.annotation.WorkerThread
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncSourceKind

/**
 * The sources a rule can keep from Clementine's library, as Clementine names them in
 * SyncSource.source_id: "library/album/<album artist>/<album>", "library/artist/<artist>" and
 * "playlist/<id>", each part percent-encoded as Clementine encodes it (QUrl::toPercentEncoding).
 */
object SyncSources {

    fun album(albumArtist: String, album: String) = "library/album/${encode(albumArtist)}/${encode(album)}"

    fun artist(artist: String) = "library/artist/${encode(artist)}"

    fun playlist(id: Int) = "playlist/$id"

    /** A rule keeping everything an album has. */
    fun albumRule(
        serverId: String,
        albumArtist: String,
        album: String,
        refreshPolicy: RefreshPolicy = RefreshPolicy.NEVER,
    ) = SyncRule(
        serverId = serverId,
        sourceId = album(albumArtist, album),
        title = album,
        kind = SyncSourceKind.SYNC_SOURCE_KIND_ALBUM,
        refreshPolicy = refreshPolicy,
    )

    /** A rule keeping every song by an artist. */
    fun artistRule(serverId: String, artist: String, refreshPolicy: RefreshPolicy = RefreshPolicy.NEVER) = SyncRule(
        serverId = serverId,
        sourceId = artist(artist),
        title = artist,
        kind = SyncSourceKind.SYNC_SOURCE_KIND_ARTIST,
        refreshPolicy = refreshPolicy,
    )

    /** A rule keeping the songs of one of Clementine's playlists, as it changes. */
    fun playlistRule(
        serverId: String,
        id: Int,
        name: String,
        refreshPolicy: RefreshPolicy = RefreshPolicy.ON_CONNECT,
    ) = SyncRule(
        serverId = serverId,
        sourceId = playlist(id),
        title = name,
        kind = SyncSourceKind.SYNC_SOURCE_KIND_PLAYLIST,
        refreshPolicy = refreshPolicy,
    )

    /**
     * The albums of the library's songs where each of [fields] has the value [values] gives it, as
     * the library's levels select them: an album artist (the artist where there's none) and an
     * album. [fields] are column names of the library's songs table. Reads the disk.
     */
    @WorkerThread
    fun albums(library: SQLiteDatabase, fields: List<String>, values: List<String>): List<Pair<String, String>> {
        require(fields.size == values.size && fields.all { it.matches(COLUMN) })
        val where = fields.joinToString(" AND ") { "$it = ?" }
        return library.rawQuery(
            "SELECT DISTINCT CASE WHEN albumartist <> '' THEN albumartist ELSE artist END AS album_artist, album " +
                "FROM songs WHERE album <> '' AND album_artist <> ''" +
                (if (where.isEmpty()) "" else " AND $where") +
                " ORDER BY album_artist, album",
            values.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
            }
        }
    }

    /** As QUrl::toPercentEncoding: every UTF-8 byte but letters, digits and -._~ as %XX. */
    internal fun encode(part: String): String {
        val out = StringBuilder()
        for (byte in part.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xff
            if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
            ) {
                out.append(c.toChar())
            } else {
                out.append('%').append(HEX[c shr 4]).append(HEX[c and 0xf])
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
    private val COLUMN = Regex("[a-z_]+")
}
