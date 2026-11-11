package de.qspool.clementineremote.backend.sync

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread
import de.qspool.clementineremote.backend.downloader.DownloadStorage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.AudioFormat
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncItem
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncManifestState
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** How the engine talks to the Clementine it syncs with, while connected to it. */
@WorkerThread
interface SyncConnection {
    /** The Clementine's server_id. */
    val serverId: String

    /** Asks Clementine for a rule's manifest, and waits for it. */
    @Throws(IOException::class)
    fun manifest(request: RequestSyncManifest): ResponseSyncManifest

    /** Opens a path Clementine gave, such as a song's /d/ or cover's /a/ URL, on its HTTP server. */
    @Throws(IOException::class)
    fun open(path: String): InputStream
}

/** What a sync did for each rule, by rule id. */
data class SyncResult(
    val rules: Map<Long, RuleResult>,
    /** Songs deleted because no rule wants them any more. */
    val removed: Int,
)

sealed interface RuleResult {
    /** [downloaded] new or changed songs; [skipped] are the songs Clementine left out. */
    data class Synced(val songs: Int, val downloaded: Int, val skipped: Int) : RuleResult

    /** Clementine needs setting up first; [message] says how, in the user's language. */
    data class NeedsSetup(val message: String) : RuleResult

    /** The rule's source no longer exists on Clementine. Its songs are kept. */
    data object Gone : RuleResult

    /** The rule couldn't sync, for now: it's tried again next time. */
    data class Failed(val error: IOException) : RuleResult
}

/**
 * Brings what's on the phone in line with the rules for one Clementine: asks for each rule's
 * manifest, downloads the songs that are new or changed into [storage] (Music/Clementine/Synced/)
 * with their covers into [covers], and deletes the songs that no rule wants any more. A song in
 * several rules is downloaded once, and a cover many songs share is too. Conversions come later.
 *
 * [supportedFormats] are what the phone plays, as for streaming, and [delete] removes a song
 * [storage] saved, by its URI. [onProgress] hears how many of a rule's songs to download have
 * been, before the first and after each one. Once [stopped] says so, as when Android ends a
 * background sync, it downloads nothing more: the rule being synced fails, the rules after it
 * aren't tried, and every song downloaded so far stays.
 */
@WorkerThread
class SyncEngine(
    private val store: SyncStore,
    private val storage: DownloadStorage,
    private val covers: File,
    private val delete: (Uri) -> Unit,
    private val supportedFormats: List<AudioFormat>,
    private val now: () -> Long = System::currentTimeMillis,
    private val onProgress: (ruleId: Long, done: Int, total: Int) -> Unit = { _, _, _ -> },
    private val stopped: () -> Boolean = { false },
) {

    fun sync(connection: SyncConnection): SyncResult {
        val serverId = connection.serverId
        val results = LinkedHashMap<Long, RuleResult>()
        for (rule in store.rules(serverId)) {
            if (stopped()) break
            // A rule kept as it is has its songs once it has synced. The other policies refresh
            // on every sync for now; intervals and listening come later.
            if (rule.refreshPolicy == RefreshPolicy.NEVER && rule.lastSyncedAt > 0) continue
            results[rule.id] = try {
                syncRule(connection, rule)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't sync \"${rule.title}\"", e)
                RuleResult.Failed(e)
            }
        }

        // Only once every rule has had its say: a song moving from one rule to another stays.
        return SyncResult(results, removeUnwanted(store, serverId, delete))
    }

    private fun syncRule(connection: SyncConnection, rule: SyncRule): RuleResult {
        val manifest = connection.manifest(request(rule))
        when (manifest.state) {
            SyncManifestState.SYNC_MANIFEST_STATE_NEEDS_SETUP ->
                return RuleResult.NeedsSetup(manifest.message)
            SyncManifestState.SYNC_MANIFEST_STATE_GONE -> return RuleResult.Gone
            else -> {}
        }

        // A playlist can have a song more than once; the phone keeps it once, where it first comes.
        val wanted = manifest.itemsList.distinctBy { it.itemKey }.map { item ->
            val have = store.item(rule.serverId, item.itemKey)
            Triple(item, have, have == null || have.version != item.version)
        }
        val total = wanted.count { it.third }
        Log.i(TAG, "\"${rule.title}\": ${wanted.size} songs, $total to download")
        if (total > 0) onProgress(rule.id, 0, total)
        var downloaded = 0
        val keys = ArrayList<String>()
        try {
            for ((item, have, changed) in wanted) {
                if (changed) {
                    if (stopped()) throw IOException("The sync was stopped")
                    download(connection, rule.serverId, item, have)
                    downloaded++
                    onProgress(rule.id, downloaded, total)
                } else if (have != null && have.artKey != item.artKey()) {
                    // The same song with another cover, or one kept before covers were.
                    store.putItem(have.copy(artKey = art(connection, item)))
                }
                keys += item.itemKey
            }
        } catch (e: IOException) {
            // Keep the songs downloaded so far as well as those it had, so a sync cut short
            // carries on next time rather than starting again.
            val had = store.ruleItems(rule.id).map { it.itemKey }
            store.setRuleItems(rule.id, rule.serverId, (had + keys).distinct())
            throw e
        }
        store.setRuleItems(rule.id, rule.serverId, keys)
        store.updateRule(rule.copy(lastSyncedAt = now()))
        return RuleResult.Synced(keys.size, downloaded, manifest.skipped)
    }

    private fun request(rule: SyncRule): RequestSyncManifest {
        val builder = RequestSyncManifest.newBuilder()
            .setSourceId(rule.sourceId)
            .addAllSupportedFormats(supportedFormats)
            .setSeed(rule.seed)
        if (rule.limitItems > 0) builder.limitItems = rule.limitItems
        if (rule.limitBytes > 0) builder.limitBytes = rule.limitBytes
        return builder.build()
    }

    /** Downloads [item], replacing the older version [have] if there is one. */
    private fun download(connection: SyncConnection, serverId: String, item: SyncItem, have: SyncedItem?) {
        val song = item.song
        val dir = SyncFiles.relativeDir(song)
        val name = SyncFiles.freeName(storage, dir, SyncFiles.fileName(song, item.mimeType), have?.uri)

        val timing = DownloadTiming(item.itemKey, item.sizeBytes)
        val pending = storage.create(dir, name)
        timing.phase("created")
        val uri = try {
            connection.open(item.url).use { input ->
                timing.phase("opened")
                copy(input, pending.outputStream, timing)
            }
            timing.phase("copied")
            pending.commit().also { timing.phase("committed") }
        } catch (e: IOException) {
            timing.failed(e)
            pending.abort()
            throw e
        } catch (e: RuntimeException) {
            // MediaStore reports some failures, such as a full volume, as runtime exceptions.
            timing.failed(e)
            pending.abort()
            throw IOException(e)
        }
        timing.done()

        if (have != null && have.uri != uri.toString()) {
            try {
                delete(Uri.parse(have.uri))
            } catch (ignored: RuntimeException) {
            }
        }
        store.putItem(
            SyncedItem(
                serverId = serverId,
                itemKey = item.itemKey,
                version = item.version,
                uri = uri.toString(),
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                delivery = item.delivery,
                artKey = art(connection, item),
                metadata = song.toByteArray(),
                pinned = have?.pinned ?: false,
                addedAt = have?.addedAt ?: now(),
            )
        )
    }

    /**
     * The key of [item]'s cover, downloading it if the phone doesn't have it yet; null if it has
     * none, or it couldn't be downloaded, so it's tried again next time. A song without a cover of
     * its own from Clementine shows the one in its file, if any.
     */
    private fun art(connection: SyncConnection, item: SyncItem): String? {
        val key = item.artKey() ?: return null
        val file = SyncFiles.artFile(covers, key) ?: return null
        if (store.art(key) != null) return key

        // Downloaded beside it first, so a download cut short leaves no half a cover.
        val partial = File(covers, ".${UUID.randomUUID()}.part")
        return try {
            covers.mkdirs()
            connection.open(item.art.url).use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            if (!partial.renameTo(file)) throw IOException("Couldn't save the cover $key")
            store.putArt(SyncedArt(key, Uri.fromFile(file).toString(), file.length()))
            key
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't download the cover of ${item.itemKey}", e)
            partial.delete()
            null
        }
    }
}

/** The key of the cover Clementine sent with the song, or null if it sent none. */
private fun SyncItem.artKey(): String? = if (hasArt() && art.key.isNotEmpty()) art.key else null

/** Copies [input] to [output], timing the reads (the network) and the writes (MediaStore). */
private fun copy(input: InputStream, output: OutputStream, timing: DownloadTiming) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = timing.reading { input.read(buffer) }
        if (read < 0) return
        timing.writing { output.write(buffer, 0, read) }
        timing.copied(read)
    }
}

/**
 * Logs how long a song's download spends in each step, so a stall shows where it is: making the
 * file in MediaStore, opening the download, reading it, writing it, or committing the file. A
 * single read or write that blocks for long is logged as it ends.
 */
private class DownloadTiming(private val itemKey: String, private val expectedBytes: Long) {
    private val start = SystemClock.elapsedRealtime()
    private var phaseStart = start
    private val phases = StringBuilder()
    private var bytes = 0L
    private var readMs = 0L
    private var writeMs = 0L

    fun phase(name: String) {
        val now = SystemClock.elapsedRealtime()
        phases.append(' ').append(name).append('=').append(now - phaseStart).append("ms")
        phaseStart = now
    }

    fun <T> reading(read: () -> T): T = timed("read") { read() }.also { readMs += it.second }.first

    fun writing(write: () -> Unit) {
        writeMs += timed("write") { write() }.second
    }

    fun copied(count: Int) {
        bytes += count
    }

    private fun <T> timed(what: String, block: () -> T): Pair<T, Long> {
        val before = SystemClock.elapsedRealtime()
        val result = block()
        val took = SystemClock.elapsedRealtime() - before
        if (took >= SLOW_MS) {
            Log.w(TAG, "$itemKey: a $what took ${took}ms, at $bytes of $expectedBytes bytes")
        }
        return result to took
    }

    fun done() {
        Log.d(TAG, "$itemKey: $bytes bytes in ${SystemClock.elapsedRealtime() - start}ms (reading ${readMs}ms, writing ${writeMs}ms)$phases")
    }

    fun failed(e: Exception) {
        Log.w(TAG, "$itemKey: failed after ${SystemClock.elapsedRealtime() - start}ms at $bytes of $expectedBytes bytes " +
            "(reading ${readMs}ms, writing ${writeMs}ms)$phases: $e")
    }

    private companion object {
        const val SLOW_MS = 2_000L
    }
}

private const val TAG = "SyncEngine"

/** Deletes [serverId]'s songs that no rule wants, as after a rule is removed. Returns how many. */
@WorkerThread
fun removeUnwanted(store: SyncStore, serverId: String, delete: (Uri) -> Unit): Int {
    var removed = 0
    for (item in store.unwantedItems(serverId)) {
        try {
            delete(Uri.parse(item.uri))
        } catch (e: RuntimeException) {
            // Already gone, say if the user deleted it: forget it all the same.
        }
        store.removeItem(serverId, item.itemKey)
        removed++
    }
    for (art in store.unusedArt()) {
        Uri.parse(art.uri).path?.let { File(it).delete() }
        store.removeArt(art.artKey)
    }
    return removed
}
