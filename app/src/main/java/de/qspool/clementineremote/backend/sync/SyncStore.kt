package de.qspool.clementineremote.backend.sync

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.WorkerThread

/** When a rule asks Clementine for its songs again. */
enum class RefreshPolicy(val key: String) {
    /** Once, when the rule is made: a song, album or artist. */
    NEVER("never"),

    /** Every time the phone connects, at most hourly: hand-edited playlists. */
    ON_CONNECT("on_connect"),

    /** Once [SyncRule.refreshIntervalHours] have passed: smart playlists. */
    INTERVAL("interval"),

    /** Once [SyncRule.refreshAfterPercent] of its songs have been played on the phone. */
    AFTER_LISTENING("after_listening");

    companion object {
        fun fromKey(key: String?): RefreshPolicy = entries.firstOrNull { it.key == key } ?: NEVER
    }
}

/** How a play on the phone ended, as Clementine's PlayOutcome. */
enum class PlayOutcome(val key: String) {
    COMPLETED("completed"),
    SKIPPED("skipped");

    companion object {
        fun fromKey(key: String?): PlayOutcome = entries.firstOrNull { it.key == key } ?: COMPLETED
    }
}

/**
 * What to keep on the phone: a source on one Clementine, when to refresh it, and how much of it.
 * [serverId] is the Clementine's server_id, and [sourceId] is opaque, as Clementine gave it.
 * Quality fields left null use the phone's sync settings.
 */
data class SyncRule(
    val id: Long = 0,
    val serverId: String,
    val sourceId: String,
    val title: String,
    /** SyncSourceKind's number, for the rule's icon. */
    val kind: Int,
    val refreshPolicy: RefreshPolicy,
    val refreshIntervalHours: Int = 0,
    val refreshAfterPercent: Int = 0,
    /** 0: no limit. */
    val limitItems: Int = 0,
    val limitBytes: Long = 0,
    /** SyncQuality and SyncLossless numbers. */
    val quality: Int? = null,
    val lossless: Int? = null,
    val maxBitrateKbps: Int? = null,
    val preferredMimeType: String? = null,
    /** For random sources: sent with each request until the rule refreshes. */
    val seed: Long = 0,
    /** Where it shows in the list, and which rule's songs fit first in the budget. */
    val position: Int = 0,
    /** Unix milliseconds; 0 until it first syncs. */
    val lastSyncedAt: Long = 0,
)

/**
 * A song on the phone. [itemKey] and [version] are Clementine's; [uri] is where the file is, a
 * content:// URI in MediaStore. [metadata] is the SongMetadata from the manifest, serialized,
 * so the offline library reads it rather than the file's tags.
 */
data class SyncedItem(
    val serverId: String,
    val itemKey: String,
    val version: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** SyncDelivery's number. */
    val delivery: Int,
    /** The cover's [SyncedArt.artKey], or null for none. */
    val artKey: String?,
    val metadata: ByteArray,
    val pinned: Boolean = false,
    /** Unix milliseconds. */
    val addedAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is SyncedItem && serverId == other.serverId && itemKey == other.itemKey &&
            version == other.version && uri == other.uri && mimeType == other.mimeType &&
            sizeBytes == other.sizeBytes && delivery == other.delivery &&
            artKey == other.artKey && metadata.contentEquals(other.metadata) &&
            pinned == other.pinned && addedAt == other.addedAt

    override fun hashCode(): Int = 31 * serverId.hashCode() + itemKey.hashCode()
}

/** A cover, kept once however many songs share it. [artKey] is the SHA-256 Clementine sent. */
data class SyncedArt(val artKey: String, val uri: String, val sizeBytes: Long)

/** A song played on the phone, kept until Clementine accepts it. */
data class PlayLogEntry(
    val id: Long = 0,
    val serverId: String,
    val itemKey: String,
    /** Unix seconds, when it started. */
    val playedAt: Long,
    val listenedMs: Long,
    val outcome: PlayOutcome,
    /** 0 to 1, if rated on the phone. */
    val rating: Float? = null,
)

/**
 * The phone's record of what it syncs: rules, the songs and covers on the phone, which rules want
 * which songs, and plays made on the phone. Every row belongs to one Clementine, by server_id,
 * so another Clementine's songs are kept apart. A song is stored once however many rules want it.
 */
class SyncStore(context: Context, name: String? = DATABASE_NAME) :
    SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE rules (" +
                " id INTEGER PRIMARY KEY," +
                " server_id TEXT NOT NULL," +
                " source_id TEXT NOT NULL," +
                " title TEXT NOT NULL," +
                " kind INTEGER NOT NULL," +
                " refresh_policy TEXT NOT NULL," +
                " refresh_interval_hours INTEGER NOT NULL DEFAULT 0," +
                " refresh_after_percent INTEGER NOT NULL DEFAULT 0," +
                " limit_items INTEGER NOT NULL DEFAULT 0," +
                " limit_bytes INTEGER NOT NULL DEFAULT 0," +
                " quality INTEGER," +
                " lossless INTEGER," +
                " max_bitrate_kbps INTEGER," +
                " preferred_mime_type TEXT," +
                " seed INTEGER NOT NULL DEFAULT 0," +
                " position INTEGER NOT NULL DEFAULT 0," +
                " last_synced_at INTEGER NOT NULL DEFAULT 0," +
                " UNIQUE (server_id, source_id))"
        )
        db.execSQL(
            "CREATE TABLE items (" +
                " server_id TEXT NOT NULL," +
                " item_key TEXT NOT NULL," +
                " version TEXT NOT NULL," +
                " uri TEXT NOT NULL," +
                " mime_type TEXT NOT NULL," +
                " size_bytes INTEGER NOT NULL," +
                " delivery INTEGER NOT NULL," +
                " art_key TEXT," +
                " metadata BLOB NOT NULL," +
                " pinned INTEGER NOT NULL DEFAULT 0," +
                " added_at INTEGER NOT NULL," +
                " PRIMARY KEY (server_id, item_key))"
        )
        // Which rules want which songs, in each rule's order. A song no rule wants, and that
        // isn't pinned, is deleted.
        db.execSQL(
            "CREATE TABLE rule_items (" +
                " rule_id INTEGER NOT NULL REFERENCES rules(id) ON DELETE CASCADE," +
                " server_id TEXT NOT NULL," +
                " item_key TEXT NOT NULL," +
                " position INTEGER NOT NULL," +
                " PRIMARY KEY (rule_id, item_key)," +
                " FOREIGN KEY (server_id, item_key) REFERENCES items(server_id, item_key)" +
                "   ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX rule_items_by_item ON rule_items (server_id, item_key)")
        db.execSQL(
            "CREATE TABLE art (" +
                " art_key TEXT PRIMARY KEY," +
                " uri TEXT NOT NULL," +
                " size_bytes INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE play_log (" +
                " id INTEGER PRIMARY KEY," +
                " server_id TEXT NOT NULL," +
                " item_key TEXT NOT NULL," +
                " played_at INTEGER NOT NULL," +
                " listened_ms INTEGER NOT NULL," +
                " outcome TEXT NOT NULL," +
                " rating REAL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Later versions migrate from here, keeping what's on the phone.
    }

    @WorkerThread
    fun addRule(rule: SyncRule): Long =
        writableDatabase.insertOrThrow("rules", null, rule.toValues())

    @WorkerThread
    fun updateRule(rule: SyncRule) {
        writableDatabase.update("rules", rule.toValues(), "id = ?", arrayOf(rule.id.toString()))
    }

    /** Removes the rule; its songs go once no other rule wants them (see [unwantedItems]). */
    @WorkerThread
    fun removeRule(id: Long) {
        writableDatabase.delete("rules", "id = ?", arrayOf(id.toString()))
    }

    @WorkerThread
    fun rules(serverId: String): List<SyncRule> =
        readableDatabase.query(
            "rules", null, "server_id = ?", arrayOf(serverId), null, null, "position, id"
        ).use { it.all(::readRule) }

    @WorkerThread
    fun rule(id: Long): SyncRule? =
        readableDatabase.query("rules", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) readRule(it) else null }

    /** Adds the song, or replaces it with a new version, keeping which rules want it. */
    @WorkerThread
    fun putItem(item: SyncedItem) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = item.toValues()
            val updated = db.update(
                "items", values, "server_id = ? AND item_key = ?",
                arrayOf(item.serverId, item.itemKey)
            )
            if (updated == 0) db.insertOrThrow("items", null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @WorkerThread
    fun item(serverId: String, itemKey: String): SyncedItem? =
        readableDatabase.query(
            "items", null, "server_id = ? AND item_key = ?", arrayOf(serverId, itemKey),
            null, null, null
        ).use { if (it.moveToFirst()) readItem(it) else null }

    @WorkerThread
    fun items(serverId: String): List<SyncedItem> =
        readableDatabase.query(
            "items", null, "server_id = ?", arrayOf(serverId), null, null, "added_at, item_key"
        ).use { it.all(::readItem) }

    @WorkerThread
    fun setPinned(serverId: String, itemKey: String, pinned: Boolean) {
        writableDatabase.update(
            "items", ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "server_id = ? AND item_key = ?", arrayOf(serverId, itemKey)
        )
    }

    @WorkerThread
    fun removeItem(serverId: String, itemKey: String) {
        writableDatabase.delete(
            "items", "server_id = ? AND item_key = ?", arrayOf(serverId, itemKey)
        )
    }

    /**
     * Sets the songs [ruleId] wants, in its order, replacing what it wanted before. Each must be
     * in [items] already.
     */
    @WorkerThread
    fun setRuleItems(ruleId: Long, serverId: String, itemKeys: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("rule_items", "rule_id = ?", arrayOf(ruleId.toString()))
            itemKeys.forEachIndexed { position, itemKey ->
                db.insertOrThrow("rule_items", null, ContentValues().apply {
                    put("rule_id", ruleId)
                    put("server_id", serverId)
                    put("item_key", itemKey)
                    put("position", position)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** The songs [ruleId] wants that are on the phone, in its order. */
    @WorkerThread
    fun ruleItems(ruleId: Long): List<SyncedItem> =
        readableDatabase.rawQuery(
            "SELECT items.* FROM rule_items JOIN items USING (server_id, item_key)" +
                " WHERE rule_id = ? ORDER BY position",
            arrayOf(ruleId.toString())
        ).use { it.all(::readItem) }

    /** Songs on the phone that no rule wants and that aren't pinned: the ones to delete. */
    @WorkerThread
    fun unwantedItems(serverId: String): List<SyncedItem> =
        readableDatabase.rawQuery(
            "SELECT * FROM items WHERE server_id = ? AND pinned = 0 AND NOT EXISTS" +
                " (SELECT 1 FROM rule_items r" +
                "  WHERE r.server_id = items.server_id AND r.item_key = items.item_key)",
            arrayOf(serverId)
        ).use { it.all(::readItem) }

    /** How much space the songs and covers on the phone take, across every Clementine. */
    @WorkerThread
    fun usedBytes(): Long =
        readableDatabase.rawQuery(
            "SELECT (SELECT IFNULL(SUM(size_bytes), 0) FROM items)" +
                " + (SELECT IFNULL(SUM(size_bytes), 0) FROM art)",
            null
        ).use { if (it.moveToFirst()) it.getLong(0) else 0 }

    @WorkerThread
    fun putArt(art: SyncedArt) {
        writableDatabase.insertWithOnConflict("art", null, ContentValues().apply {
            put("art_key", art.artKey)
            put("uri", art.uri)
            put("size_bytes", art.sizeBytes)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @WorkerThread
    fun art(artKey: String): SyncedArt? =
        readableDatabase.query("art", null, "art_key = ?", arrayOf(artKey), null, null, null)
            .use {
                if (it.moveToFirst()) {
                    SyncedArt(it.string("art_key")!!, it.string("uri")!!, it.long("size_bytes"))
                } else {
                    null
                }
            }

    /** Covers no song on the phone uses any more. */
    @WorkerThread
    fun unusedArt(): List<SyncedArt> =
        readableDatabase.rawQuery(
            "SELECT * FROM art WHERE art_key NOT IN" +
                " (SELECT art_key FROM items WHERE art_key IS NOT NULL)",
            null
        ).use { cursor ->
            cursor.all {
                SyncedArt(it.string("art_key")!!, it.string("uri")!!, it.long("size_bytes"))
            }
        }

    @WorkerThread
    fun removeArt(artKey: String) {
        writableDatabase.delete("art", "art_key = ?", arrayOf(artKey))
    }

    @WorkerThread
    fun logPlay(entry: PlayLogEntry): Long =
        writableDatabase.insertOrThrow("play_log", null, ContentValues().apply {
            put("server_id", entry.serverId)
            put("item_key", entry.itemKey)
            put("played_at", entry.playedAt)
            put("listened_ms", entry.listenedMs)
            put("outcome", entry.outcome.key)
            if (entry.rating != null) put("rating", entry.rating) else putNull("rating")
        })

    /** Plays not yet accepted by [serverId], oldest first. */
    @WorkerThread
    fun playLog(serverId: String): List<PlayLogEntry> =
        readableDatabase.query(
            "play_log", null, "server_id = ?", arrayOf(serverId), null, null, "played_at, id"
        ).use { cursor ->
            cursor.all {
                PlayLogEntry(
                    id = it.long("id"),
                    serverId = it.string("server_id")!!,
                    itemKey = it.string("item_key")!!,
                    playedAt = it.long("played_at"),
                    listenedMs = it.long("listened_ms"),
                    outcome = PlayOutcome.fromKey(it.string("outcome")),
                    rating = it.getColumnIndexOrThrow("rating").let { column ->
                        if (it.isNull(column)) null else it.getFloat(column)
                    },
                )
            }
        }

    /** Drops plays Clementine has accepted. */
    @WorkerThread
    fun removePlays(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.delete(
            "play_log", "id IN (${ids.joinToString(",") { "?" }})",
            ids.map { it.toString() }.toTypedArray()
        )
    }

    private fun SyncRule.toValues() = ContentValues().apply {
        put("server_id", serverId)
        put("source_id", sourceId)
        put("title", title)
        put("kind", kind)
        put("refresh_policy", refreshPolicy.key)
        put("refresh_interval_hours", refreshIntervalHours)
        put("refresh_after_percent", refreshAfterPercent)
        put("limit_items", limitItems)
        put("limit_bytes", limitBytes)
        putOrNull("quality", quality)
        putOrNull("lossless", lossless)
        putOrNull("max_bitrate_kbps", maxBitrateKbps)
        put("preferred_mime_type", preferredMimeType)
        put("seed", seed)
        put("position", position)
        put("last_synced_at", lastSyncedAt)
    }

    private fun SyncedItem.toValues() = ContentValues().apply {
        put("server_id", serverId)
        put("item_key", itemKey)
        put("version", version)
        put("uri", uri)
        put("mime_type", mimeType)
        put("size_bytes", sizeBytes)
        put("delivery", delivery)
        put("art_key", artKey)
        put("metadata", metadata)
        put("pinned", if (pinned) 1 else 0)
        put("added_at", addedAt)
    }

    private fun readRule(cursor: Cursor) = SyncRule(
        id = cursor.long("id"),
        serverId = cursor.string("server_id")!!,
        sourceId = cursor.string("source_id")!!,
        title = cursor.string("title")!!,
        kind = cursor.int("kind"),
        refreshPolicy = RefreshPolicy.fromKey(cursor.string("refresh_policy")),
        refreshIntervalHours = cursor.int("refresh_interval_hours"),
        refreshAfterPercent = cursor.int("refresh_after_percent"),
        limitItems = cursor.int("limit_items"),
        limitBytes = cursor.long("limit_bytes"),
        quality = cursor.intOrNull("quality"),
        lossless = cursor.intOrNull("lossless"),
        maxBitrateKbps = cursor.intOrNull("max_bitrate_kbps"),
        preferredMimeType = cursor.string("preferred_mime_type"),
        seed = cursor.long("seed"),
        position = cursor.int("position"),
        lastSyncedAt = cursor.long("last_synced_at"),
    )

    private fun readItem(cursor: Cursor) = SyncedItem(
        serverId = cursor.string("server_id")!!,
        itemKey = cursor.string("item_key")!!,
        version = cursor.string("version")!!,
        uri = cursor.string("uri")!!,
        mimeType = cursor.string("mime_type")!!,
        sizeBytes = cursor.long("size_bytes"),
        delivery = cursor.int("delivery"),
        artKey = cursor.string("art_key"),
        metadata = cursor.getBlob(cursor.getColumnIndexOrThrow("metadata")),
        pinned = cursor.int("pinned") != 0,
        addedAt = cursor.long("added_at"),
    )

    companion object {
        const val DATABASE_NAME = "Sync.db"
        private const val DATABASE_VERSION = 1

        private fun ContentValues.putOrNull(key: String, value: Int?) {
            if (value != null) put(key, value) else putNull(key)
        }

        private fun Cursor.string(column: String): String? =
            getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

        private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

        private fun Cursor.intOrNull(column: String): Int? =
            getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }

        private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

        private fun <T> Cursor.all(read: (Cursor) -> T): List<T> {
            val rows = ArrayList<T>(count)
            while (moveToNext()) rows.add(read(this))
            return rows
        }
    }
}
