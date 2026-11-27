package de.qspool.clementineremote.backend.sync

import android.content.Context
import de.qspool.clementineremote.backend.downloader.DownloadStorage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata
import java.io.File

/** Where synced songs go, under the download location: Synced/<artist>/<album>/<track> <title>. */
object SyncFiles {

    const val DIR = "Synced/"

    /** Characters file systems or MediaStore don't take in a name. */
    private val UNSAFE = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")

    private const val MAX_NAME = 100

    fun relativeDir(song: SongMetadata): String {
        val artist = song.albumartist.ifBlank { song.artist }
        return DIR + part(artist, "Unknown artist") + "/" + part(song.album, "Unknown album") + "/"
    }

    /** The app's own folder of synced covers, apart from the songs: they aren't music. */
    fun coversDir(context: Context) = File(context.filesDir, "covers")

    /**
     * Where the cover with [key] goes in [covers]; null for a key that isn't a hash, so it can't
     * name a file somewhere else.
     */
    fun artFile(covers: File, key: String): File? {
        if (key.isEmpty() || key.length > 128 || !key.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return null
        }
        return File(covers, key.lowercase())
    }

    fun fileName(song: SongMetadata, mimeType: String): String {
        val title = part(song.title, "Untitled")
        val name = if (song.track > 0) "%02d %s".format(song.track, title) else title
        return name + "." + extension(mimeType)
    }

    /**
     * [name] in [dir], or with a number added if another song has it already. [own] is the
     * URI of the song being replaced, which may keep its name.
     */
    fun freeName(storage: DownloadStorage, dir: String, name: String, own: String?): String {
        // Guard against dotless filenames (e.g., fallback names without an extension).
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val extension = if (dot >= 0) name.substring(dot) else ""
        var candidate = name
        var n = 2
        while (true) {
            val saved = storage.find(dir, candidate) ?: return candidate
            if (saved.uri.toString() == own) return candidate
            candidate = "$base ($n)$extension"
            n++
        }
    }

    fun extension(mimeType: String): String {
        val type = mimeType.substringBefore(';').trim().lowercase()
        val codecs = mimeType.substringAfter("codecs=", "").trim().lowercase()
        return when (type) {
            "audio/mpeg" -> "mp3"
            "audio/flac" -> "flac"
            "audio/mp4" -> "m4a"
            "audio/aac" -> "aac"
            "audio/wav" -> "wav"
            "audio/aiff" -> "aiff"
            "audio/ogg" -> if (codecs == "opus") "opus" else "ogg"
            else -> "audio"
        }
    }

    private fun part(text: String, otherwise: String): String {
        // Trim spaces/dots both before and after take(MAX_NAME): truncation can expose an internal
        // space or dot at the end, which FAT filesystems and MediaStore reject or silently strip.
        val safe = text.replace(UNSAFE, "_")
            .trim { it <= ' ' || it == '.' }
            .take(MAX_NAME)
            .trimEnd { it <= ' ' || it == '.' }
        return safe.ifEmpty { otherwise }
    }
}
