package de.qspool.clementineremote.backend.sync

import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncDelivery
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncLossless
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncQuality
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncSourceKind
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The phone's record of sync rules, synced songs, covers and plays. */
@RunWith(RobolectricTestRunner::class)
class SyncStoreTest {

    private lateinit var store: SyncStore

    @Before
    fun setUp() {
        // An in-memory database, so each test starts empty.
        store = SyncStore(RuntimeEnvironment.getApplication(), name = null)
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun rule(sourceId: String, serverId: String = SERVER, position: Int = 0) = SyncRule(
        serverId = serverId,
        sourceId = sourceId,
        title = sourceId,
        kind = SyncSourceKind.SYNC_SOURCE_KIND_ALBUM,
        refreshPolicy = RefreshPolicy.NEVER,
        position = position,
    )

    private fun item(
        itemKey: String,
        serverId: String = SERVER,
        version: String = "1",
        sizeBytes: Long = 1000,
        artKey: String? = null,
    ) = SyncedItem(
        serverId = serverId,
        itemKey = itemKey,
        version = version,
        uri = "content://media/external/audio/media/$itemKey",
        mimeType = "audio/flac",
        sizeBytes = sizeBytes,
        delivery = SyncDelivery.SYNC_DELIVERY_ORIGINAL,
        artKey = artKey,
        metadata = byteArrayOf(1, 2, 3),
        addedAt = 1_700_000_000_000,
    )

    @Test
    fun opensEmpty() {
        assertEquals(emptyList<SyncRule>(), store.rules(SERVER))
        assertEquals(emptyList<SyncedItem>(), store.items(SERVER))
        assertEquals(0, store.usedBytes())
    }

    @Test
    fun keepsRulesWithEveryField() {
        val added = SyncRule(
            serverId = SERVER,
            sourceId = "smart/songs/0b6e",
            title = "Newest tracks",
            kind = SyncSourceKind.SYNC_SOURCE_KIND_SMART_PLAYLIST,
            refreshPolicy = RefreshPolicy.AFTER_LISTENING,
            refreshIntervalHours = 24,
            refreshAfterPercent = 80,
            limitItems = 50,
            limitBytes = 2_000_000_000,
            quality = SyncQuality.SYNC_QUALITY_ALWAYS_CONVERT,
            lossless = SyncLossless.SYNC_LOSSLESS_CONVERT,
            maxBitrateKbps = 160,
            preferredMimeType = "audio/ogg; codecs=opus",
            seed = 42,
            position = 3,
            lastSyncedAt = 1_700_000_000_000,
        )
        val id = store.addRule(added)

        assertEquals(added.copy(id = id), store.rule(id))
        assertEquals(listOf(added.copy(id = id)), store.rules(SERVER))
    }

    @Test
    fun leavesQualityUnsetForThePhonesSettings() {
        val id = store.addRule(rule("playlist/1"))
        val read = store.rule(id)!!
        assertNull(read.quality)
        assertNull(read.lossless)
        assertNull(read.maxBitrateKbps)
        assertNull(read.preferredMimeType)
    }

    @Test
    fun listsRulesInOrderForOneClementine() {
        store.addRule(rule("playlist/2", position = 1))
        store.addRule(rule("playlist/1", position = 0))
        store.addRule(rule("playlist/9", serverId = OTHER_SERVER))

        assertEquals(listOf("playlist/1", "playlist/2"), store.rules(SERVER).map { it.sourceId })
        assertEquals(listOf("playlist/9"), store.rules(OTHER_SERVER).map { it.sourceId })
    }

    @Test
    fun updatesRules() {
        val id = store.addRule(rule("playlist/1"))
        store.updateRule(store.rule(id)!!.copy(lastSyncedAt = 5, seed = 7))

        val read = store.rule(id)!!
        assertEquals(5, read.lastSyncedAt)
        assertEquals(7, read.seed)
    }

    @Test
    fun keepsItemsAndReplacesVersions() {
        store.putItem(item("library/1"))
        assertEquals(item("library/1"), store.item(SERVER, "library/1"))
        assertArrayEquals(byteArrayOf(1, 2, 3), store.item(SERVER, "library/1")!!.metadata)

        store.putItem(item("library/1", version = "2", sizeBytes = 2000))
        assertEquals("2", store.item(SERVER, "library/1")!!.version)
        assertEquals(1, store.items(SERVER).size)
    }

    @Test
    fun keepsAnotherClementinesItemsApart() {
        store.putItem(item("library/1"))
        store.putItem(item("library/1", serverId = OTHER_SERVER, version = "9"))

        assertEquals("1", store.item(SERVER, "library/1")!!.version)
        assertEquals("9", store.item(OTHER_SERVER, "library/1")!!.version)
    }

    @Test
    fun keepsEachRulesOrder() {
        val ruleId = store.addRule(rule("library/album/Pixies/Doolittle"))
        for (key in listOf("library/3", "library/1", "library/2")) store.putItem(item(key))
        store.setRuleItems(ruleId, SERVER, listOf("library/3", "library/1", "library/2"))

        assertEquals(
            listOf("library/3", "library/1", "library/2"),
            store.ruleItems(ruleId).map { it.itemKey }
        )
    }

    @Test
    fun aSongInTwoRulesIsKeptUntilNeitherWantsIt() {
        val album = store.addRule(rule("library/album/Pixies/Doolittle"))
        val playlist = store.addRule(rule("playlist/1"))
        store.putItem(item("library/1"))
        store.putItem(item("library/2"))
        store.setRuleItems(album, SERVER, listOf("library/1", "library/2"))
        store.setRuleItems(playlist, SERVER, listOf("library/1"))
        assertEquals(emptyList<SyncedItem>(), store.unwantedItems(SERVER))

        store.removeRule(album)
        assertEquals(listOf("library/2"), store.unwantedItems(SERVER).map { it.itemKey })

        store.removeRule(playlist)
        assertEquals(
            setOf("library/1", "library/2"),
            store.unwantedItems(SERVER).map { it.itemKey }.toSet()
        )
    }

    @Test
    fun pinnedSongsAreKept() {
        val ruleId = store.addRule(rule("playlist/1"))
        store.putItem(item("library/1"))
        store.setRuleItems(ruleId, SERVER, listOf("library/1"))
        store.setPinned(SERVER, "library/1", true)

        store.removeRule(ruleId)
        assertEquals(emptyList<SyncedItem>(), store.unwantedItems(SERVER))
        assertTrue(store.item(SERVER, "library/1")!!.pinned)
    }

    @Test
    fun removingAnItemRemovesItFromItsRules() {
        val ruleId = store.addRule(rule("playlist/1"))
        store.putItem(item("library/1"))
        store.putItem(item("library/2"))
        store.setRuleItems(ruleId, SERVER, listOf("library/1", "library/2"))

        store.removeItem(SERVER, "library/1")
        assertEquals(listOf("library/2"), store.ruleItems(ruleId).map { it.itemKey })
    }

    @Test
    fun keepsEachCoverOnce() {
        store.putArt(SyncedArt("abc", "file:///art/abc.jpg", 50))
        store.putArt(SyncedArt("abc", "file:///art/abc.jpg", 50))
        store.putItem(item("library/1", artKey = "abc"))
        store.putItem(item("library/2", artKey = "abc"))

        assertEquals(SyncedArt("abc", "file:///art/abc.jpg", 50), store.art("abc"))
        assertEquals(2000 + 50L, store.usedBytes())
        assertEquals(emptyList<SyncedArt>(), store.unusedArt())

        store.removeItem(SERVER, "library/1")
        store.removeItem(SERVER, "library/2")
        assertEquals(listOf("abc"), store.unusedArt().map { it.artKey })
        store.removeArt("abc")
        assertNull(store.art("abc"))
    }

    @Test
    fun keepsPlaysUntilAccepted() {
        val first = store.logPlay(
            PlayLogEntry(
                serverId = SERVER, itemKey = "library/1", playedAt = 100, listenedMs = 180_000,
                outcome = PlayOutcome.COMPLETED,
            )
        )
        store.logPlay(
            PlayLogEntry(
                serverId = SERVER, itemKey = "library/2", playedAt = 200, listenedMs = 5_000,
                outcome = PlayOutcome.SKIPPED, rating = 0.8f,
            )
        )

        val log = store.playLog(SERVER)
        assertEquals(listOf("library/1", "library/2"), log.map { it.itemKey })
        assertNull(log[0].rating)
        assertEquals(0.8f, log[1].rating!!, 0.0001f)
        assertEquals(PlayOutcome.SKIPPED, log[1].outcome)

        store.removePlays(listOf(first))
        assertEquals(listOf("library/2"), store.playLog(SERVER).map { it.itemKey })
        assertEquals(emptyList<PlayLogEntry>(), store.playLog(OTHER_SERVER))
    }

    private companion object {
        const val SERVER = "4f1c2d6e-8a0b-4c8e-9b7a-2e3f5d6c7b8a"
        const val OTHER_SERVER = "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a"
    }
}
