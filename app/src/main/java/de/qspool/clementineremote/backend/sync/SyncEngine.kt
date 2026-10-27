package de.qspool.clementineremote.backend.sync

import android.net.Uri
import androidx.annotation.WorkerThread
import de.qspool.clementineremote.backend.downloader.DownloadStorage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.AudioFormat
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncItem
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncManifestState
import java.io.IOException
import java.io.InputStream

/** How the engine talks to the Clementine it syncs with, while connected to it. */
@WorkerThread
interface SyncConnection {
    /** The Clementine's server_id. */
    val serverId: String

    /** Asks Clementine for a rule's manifest, and waits for it. */
    @Throws(IOException::class)
    fun manifest(request: RequestSyncManifest): ResponseSyncManifest

    /** Opens a path Clementine gave, such as a song's /d/ URL, on its HTTP server. */
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
 * manifest, downloads the songs that are new or changed into [storage] (Music/Clementine/Synced/),
 * and deletes the songs that no rule wants any more. A song in several rules is downloaded once.
 * Covers and conversions come later.
 *
 * [supportedFormats] are what the phone plays, as for streaming, and [delete] removes a song
 * [storage] saved, by its URI.
 */
@WorkerThread
class SyncEngine(
    private val store: SyncStore,
    private val storage: DownloadStorage,
    private val delete: (Uri) -> Unit,
    private val supportedFormats: List<AudioFormat>,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun sync(connection: SyncConnection): SyncResult {
        val serverId = connection.serverId
        val results = LinkedHashMap<Long, RuleResult>()
        for (rule in store.rules(serverId)) {
            results[rule.id] = try {
                syncRule(connection, rule)
            } catch (e: IOException) {
                RuleResult.Failed(e)
            }
        }

        // Only once every rule has had its say: a song moving from one rule to another stays.
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
        return SyncResult(results, removed)
    }

    private fun syncRule(connection: SyncConnection, rule: SyncRule): RuleResult {
        val manifest = connection.manifest(request(rule))
        when (manifest.state) {
            SyncManifestState.SYNC_MANIFEST_STATE_NEEDS_SETUP ->
                return RuleResult.NeedsSetup(manifest.message)
            SyncManifestState.SYNC_MANIFEST_STATE_GONE -> return RuleResult.Gone
            else -> {}
        }

        var downloaded = 0
        val keys = ArrayList<String>()
        try {
            for (item in manifest.itemsList) {
                val have = store.item(rule.serverId, item.itemKey)
                if (have == null || have.version != item.version) {
                    download(connection, rule.serverId, item, have)
                    downloaded++
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

        val pending = storage.create(dir, name)
        val uri = try {
            connection.open(item.url).use { input ->
                input.copyTo(pending.outputStream)
            }
            pending.commit()
        } catch (e: IOException) {
            pending.abort()
            throw e
        } catch (e: RuntimeException) {
            // MediaStore reports some failures, such as a full volume, as runtime exceptions.
            pending.abort()
            throw IOException(e)
        }

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
                delivery = item.deliveryValue,
                artKey = null,
                metadata = song.toByteArray(),
                pinned = have?.pinned ?: false,
                addedAt = have?.addedAt ?: now(),
            )
        )
    }
}
