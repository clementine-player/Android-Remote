package de.qspool.clementineremote.backend.sync

import android.net.Uri
import de.qspool.clementineremote.backend.downloader.DownloadStorage
import de.qspool.clementineremote.backend.downloader.SavedSong
import de.qspool.clementineremote.backend.downloader.StoredSong
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.AudioFormat
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseSyncManifest
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncArt
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncDelivery
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncSourceKind
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncItem
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncManifestState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Syncing a Clementine's rules to the phone. */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    /** Songs saved in memory, by directory and name. */
    private class FakeStorage : DownloadStorage {
        val files = LinkedHashMap<String, ByteArray>()
        private var next = 1
        private val uris = HashMap<String, Uri>()

        fun path(uri: String) = uris.entries.first { it.value.toString() == uri }.key

        override fun find(relativeDir: String, fileName: String): SavedSong? {
            val path = relativeDir + fileName
            val data = files[path] ?: return null
            return SavedSong(uris[path], data.size.toLong())
        }

        override fun freeSpace() = -1L

        override fun list(): List<StoredSong> = emptyList()

        override fun create(relativeDir: String, fileName: String): DownloadStorage.PendingSong {
            val path = relativeDir + fileName
            val out = ByteArrayOutputStream()
            return object : DownloadStorage.PendingSong {
                override fun getOutputStream(): OutputStream = out
                override fun commit(): Uri {
                    files[path] = out.toByteArray()
                    return uris.getOrPut(path) { Uri.parse("content://test/${next++}") }
                }
                override fun abort() {}
            }
        }

        fun delete(uri: Uri) {
            val path = uris.entries.first { it.value == uri }.key
            files.remove(path)
            uris.remove(path)
        }
    }

    private class FakeClementine : SyncConnection {
        override val serverId = SERVER
        val manifests = HashMap<String, ResponseSyncManifest>()
        val files = HashMap<String, ByteArray>()
        val requests = ArrayList<RequestSyncManifest>()
        val opened = ArrayList<String>()

        override fun manifest(request: RequestSyncManifest): ResponseSyncManifest {
            requests += request
            return manifests[request.sourceId] ?: throw IOException("Not connected")
        }

        override fun open(path: String): InputStream {
            opened += path
            return ByteArrayInputStream(files[path] ?: throw IOException("404 $path"))
        }
    }

    private lateinit var store: SyncStore
    private val storage = FakeStorage()
    private val clementine = FakeClementine()

    @get:Rule
    val covers = TemporaryFolder()
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        store = SyncStore(RuntimeEnvironment.getApplication(), name = null)
        engine = SyncEngine(
            store, storage, covers.root, storage::delete,
            listOf(AudioFormat.newBuilder().setMimeType("audio/flac").build()),
            now = { 1_700_000_000_000 },
        )
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun rule(
        sourceId: String,
        limitItems: Int = 0,
        refreshPolicy: RefreshPolicy = RefreshPolicy.ON_CONNECT,
    ) = store.addRule(
        SyncRule(
            serverId = SERVER, sourceId = sourceId, title = sourceId, kind = SyncSourceKind.SYNC_SOURCE_KIND_ALBUM,
            refreshPolicy = refreshPolicy, limitItems = limitItems,
        )
    )

    private fun song(key: String, version: String = "1", title: String = key, track: Int = 1): SyncItem {
        val url = "/d/token/$key-$version"
        clementine.files[url] = "$key $version".toByteArray()
        return SyncItem.newBuilder()
            .setItemKey(key)
            .setVersion(version)
            .setUrl(url)
            .setMimeType("audio/flac")
            .setSizeBytes(100)
            .setDelivery(SyncDelivery.SYNC_DELIVERY_ORIGINAL)
            .setSong(
                SongMetadata.newBuilder().setTitle(title).setArtist("Pixies")
                    .setAlbum("Doolittle").setTrack(track)
            )
            .build()
    }

    private fun manifest(sourceId: String, vararg items: SyncItem, skipped: Int = 0) {
        clementine.manifests[sourceId] = ResponseSyncManifest.newBuilder()
            .setSourceId(sourceId)
            .setState(SyncManifestState.SYNC_MANIFEST_STATE_READY)
            .addAllItems(items.asList())
            .setSkipped(skipped)
            .build()
    }

    @Test
    fun downloadsARulesSongs() {
        val album = rule("library/album/Pixies/Doolittle", limitItems = 20)
        manifest("library/album/Pixies/Doolittle", song("a", title = "Debaser"),
            song("b", title = "Tame", track = 3), skipped = 1)

        val result = engine.sync(clementine)

        assertEquals(RuleResult.Synced(songs = 2, downloaded = 2, skipped = 1), result.rules[album])
        assertEquals(listOf("a", "b"), store.ruleItems(album).map { it.itemKey })
        assertEquals("a 1", String(storage.files["Synced/Pixies/Doolittle/01 Debaser.flac"]!!))
        assertTrue("Synced/Pixies/Doolittle/03 Tame.flac" in storage.files)
        assertEquals(1_700_000_000_000, store.rule(album)!!.lastSyncedAt)

        val request = clementine.requests.single()
        assertEquals(20, request.limitItems)
        assertEquals("audio/flac", request.supportedFormatsList.single().mimeType)

        val item = store.item(SERVER, "a")!!
        assertEquals("Debaser", SongMetadata.parseFrom(item.metadata).title)
        assertEquals("Synced/Pixies/Doolittle/01 Debaser.flac", storage.path(item.uri))
    }

    @Test
    fun downloadsOnlyWhatsNewOrChanged() {
        rule("playlist/1")
        manifest("playlist/1", song("a"), song("b"))
        engine.sync(clementine)
        clementine.opened.clear()

        manifest("playlist/1", song("a"), song("b", version = "2"))
        engine.sync(clementine)

        assertEquals(listOf("/d/token/b-2"), clementine.opened)
        assertEquals("2", store.item(SERVER, "b")!!.version)
        assertEquals("b 2", String(storage.files["Synced/Pixies/Doolittle/01 b.flac"]!!))
        assertEquals(2, storage.files.size)
    }

    @Test
    fun keepsASongAPlaylistHasTwiceOnce() {
        val playlist = rule("playlist/1")
        manifest("playlist/1", song("a"), song("b"), song("a"))

        val result = engine.sync(clementine)

        assertEquals(RuleResult.Synced(songs = 2, downloaded = 2, skipped = 0), result.rules[playlist])
        assertEquals(listOf("a", "b"), store.ruleItems(playlist).map { it.itemKey })
        assertEquals(listOf("/d/token/a-1", "/d/token/b-1"), clementine.opened)
    }

    @Test
    fun saysHowManySongsItHasDownloaded() {
        val progress = ArrayList<Triple<Long, Int, Int>>()
        engine = SyncEngine(
            store, storage, covers.root, storage::delete,
            listOf(AudioFormat.newBuilder().setMimeType("audio/flac").build()),
            onProgress = { ruleId, done, total -> progress += Triple(ruleId, done, total) },
        )
        val playlist = rule("playlist/1")
        manifest("playlist/1", song("a"))
        engine.sync(clementine)
        progress.clear()

        // Only the songs it has to download count.
        manifest("playlist/1", song("a"), song("b"), song("c"))
        engine.sync(clementine)

        assertEquals(listOf(Triple(playlist, 0, 2), Triple(playlist, 1, 2), Triple(playlist, 2, 2)), progress)

        // With nothing to download, it says nothing.
        progress.clear()
        engine.sync(clementine)
        assertEquals(emptyList<Triple<Long, Int, Int>>(), progress)
    }

    @Test
    fun aRuleKeptAsItIsSyncsOnceUntilItHasItsSongs() {
        val album = rule("library/album/Pixies/Doolittle", refreshPolicy = RefreshPolicy.NEVER)
        // The first sync fails, so the next one asks again.
        assertTrue(engine.sync(clementine).rules[album] is RuleResult.Failed)

        manifest("library/album/Pixies/Doolittle", song("a"))
        assertEquals(RuleResult.Synced(1, 1, 0), engine.sync(clementine).rules[album])

        // Once it has its songs, it isn't asked for them again, and keeps them.
        manifest("library/album/Pixies/Doolittle", song("a"), song("b", title = "Tame"))
        clementine.requests.clear()
        val result = engine.sync(clementine)
        assertNull(result.rules[album])
        assertTrue(clementine.requests.isEmpty())
        assertEquals(0, result.removed)
        assertEquals(listOf("a"), store.ruleItems(album).map { it.itemKey })
    }

    @Test
    fun aSongInTwoRulesIsDownloadedOnceAndKeptWhileEitherWantsIt() {
        val album = rule("library/album/Pixies/Doolittle")
        val playlist = rule("playlist/1")
        manifest("library/album/Pixies/Doolittle", song("a"), song("b", title = "Tame"))
        manifest("playlist/1", song("a"))
        engine.sync(clementine)
        assertEquals(2, clementine.opened.size)

        // The album's dropped: its other song goes, the playlist's stays.
        store.removeRule(album)
        val result = engine.sync(clementine)
        assertEquals(1, result.removed)
        assertNotNull(store.item(SERVER, "a"))
        assertNull(store.item(SERVER, "b"))
        assertEquals(1, storage.files.size)
        assertEquals(listOf("a"), store.ruleItems(playlist).map { it.itemKey })
    }

    @Test
    fun songsLeavingARuleAreDeleted() {
        rule("playlist/1")
        manifest("playlist/1", song("a"), song("b", title = "Tame"))
        engine.sync(clementine)

        manifest("playlist/1", song("b", title = "Tame"))
        assertEquals(1, engine.sync(clementine).removed)
        assertEquals(setOf("Synced/Pixies/Doolittle/01 Tame.flac"), storage.files.keys)
    }

    @Test
    fun pinnedSongsAreKept() {
        rule("playlist/1")
        manifest("playlist/1", song("a"))
        engine.sync(clementine)
        store.setPinned(SERVER, "a", true)

        manifest("playlist/1")
        assertEquals(0, engine.sync(clementine).removed)
        assertEquals(1, storage.files.size)
    }

    @Test
    fun songsWithTheSameNameAreKeptApart() {
        rule("playlist/1")
        manifest("playlist/1", song("a", title = "Intro"), song("b", title = "Intro"))
        engine.sync(clementine)

        assertEquals(
            setOf("Synced/Pixies/Doolittle/01 Intro.flac", "Synced/Pixies/Doolittle/01 Intro (2).flac"),
            storage.files.keys,
        )
    }

    @Test
    fun goneAndUnsetUpSourcesKeepTheirSongs() {
        val gone = rule("playlist/1")
        val smart = rule("smart/library/0b6e")
        manifest("playlist/1", song("a"))
        manifest("smart/library/0b6e", song("b", title = "Tame"))
        engine.sync(clementine)

        clementine.manifests["playlist/1"] = ResponseSyncManifest.newBuilder()
            .setState(SyncManifestState.SYNC_MANIFEST_STATE_GONE).build()
        clementine.manifests["smart/library/0b6e"] = ResponseSyncManifest.newBuilder()
            .setState(SyncManifestState.SYNC_MANIFEST_STATE_NEEDS_SETUP)
            .setMessage("Not yet").build()
        val result = engine.sync(clementine)

        assertEquals(RuleResult.Gone, result.rules[gone])
        assertEquals(RuleResult.NeedsSetup("Not yet"), result.rules[smart])
        assertEquals(0, result.removed)
        assertEquals(2, storage.files.size)
    }

    @Test
    fun aFailedRuleKeepsItsSongsAndTheOthersCarryOn() {
        val first = rule("playlist/1")
        val second = rule("playlist/2")
        manifest("playlist/1", song("a"))
        manifest("playlist/2", song("b", title = "Tame"))
        engine.sync(clementine)

        // The first's new song can't be fetched.
        manifest("playlist/1", song("a"), song("c", title = "Hey"))
        clementine.files.remove("/d/token/c-1")
        manifest("playlist/2", song("b", title = "Tame"), song("d", title = "Gouge Away"))
        val result = engine.sync(clementine)

        assertTrue(result.rules[first] is RuleResult.Failed)
        assertEquals(RuleResult.Synced(songs = 2, downloaded = 1, skipped = 0), result.rules[second])
        assertEquals(listOf("a"), store.ruleItems(first).map { it.itemKey })
        assertEquals(0, result.removed)
    }

    @Test
    fun aSyncCutShortKeepsWhatItDownloaded() {
        val album = rule("library/album/Pixies/Doolittle")
        manifest("library/album/Pixies/Doolittle", song("a"), song("b", track = 2), song("c", track = 3))
        clementine.files.remove("/d/token/c-1")
        val result = engine.sync(clementine)

        assertTrue(result.rules[album] is RuleResult.Failed)
        assertEquals(listOf("a", "b"), store.ruleItems(album).map { it.itemKey })
        assertEquals(0, result.removed)

        // Next time, only what's left is downloaded.
        song("c", track = 3)
        clementine.opened.clear()
        engine.sync(clementine)
        assertEquals(listOf("/d/token/c-1"), clementine.opened)
    }

    @Test
    fun aStoppedSyncDownloadsNothingMoreAndKeepsWhatItHas() {
        var stop = false
        engine = SyncEngine(
            store, storage, covers.root, storage::delete,
            listOf(AudioFormat.newBuilder().setMimeType("audio/flac").build()),
            onProgress = { _, done, _ -> if (done == 1) stop = true },
            stopped = { stop },
        )
        val album = rule("library/album/Pixies/Doolittle")
        val playlist = rule("playlist/1")
        manifest("library/album/Pixies/Doolittle", song("a"), song("b", track = 2))
        manifest("playlist/1", song("c"))

        val result = engine.sync(clementine)

        // Stopped after the first song: the album keeps it, and the playlist isn't tried.
        assertTrue(result.rules[album] is RuleResult.Failed)
        assertNull(result.rules[playlist])
        assertEquals(listOf("a"), store.ruleItems(album).map { it.itemKey })
        assertEquals(listOf("/d/token/a-1"), clementine.opened)
        assertEquals(0, result.removed)
    }

    /** [item] with the cover [key], which Clementine serves. */
    private fun withArt(item: SyncItem, key: String): SyncItem {
        val url = "/a/token/$key"
        clementine.files[url] = "cover $key".toByteArray()
        return item.toBuilder().setArt(SyncArt.newBuilder().setKey(key).setUrl(url)).build()
    }

    private fun cover(key: String) = File(covers.root, key)

    @Test
    fun downloadsEachCoverOnce() {
        rule("smart/library/0b6e")
        manifest("smart/library/0b6e", withArt(song("a"), "c0ffee"), withArt(song("b"), "c0ffee"), song("c"))
        engine.sync(clementine)

        assertEquals(listOf("/a/token/c0ffee"), clementine.opened.filter { it.startsWith("/a/") })
        assertEquals("c0ffee", store.item(SERVER, "a")!!.artKey)
        assertEquals("c0ffee", store.item(SERVER, "b")!!.artKey)
        assertNull(store.item(SERVER, "c")!!.artKey)
        assertEquals(Uri.fromFile(cover("c0ffee")).toString(), store.art("c0ffee")!!.uri)
        assertEquals("cover c0ffee", cover("c0ffee").readText())
        // Not with the songs: covers aren't music.
        assertEquals(3, storage.files.size)

        assertEquals(Uri.fromFile(cover("c0ffee")), KeptSong.of(store.item(SERVER, "a")!!, covers.root).art)
    }

    @Test
    fun songsKeptBeforeTheirCoverGetIt() {
        val playlist = rule("playlist/1")
        manifest("playlist/1", song("a"))
        engine.sync(clementine)

        // The same song, now with a cover: only the cover's downloaded.
        manifest("playlist/1", withArt(song("a"), "beef"))
        clementine.opened.clear()
        val result = engine.sync(clementine)
        assertEquals(listOf("/a/token/beef"), clementine.opened)
        assertEquals(RuleResult.Synced(songs = 1, downloaded = 0, skipped = 0), result.rules[playlist])
        assertEquals("beef", store.item(SERVER, "a")!!.artKey)
    }

    @Test
    fun aCoverThatCantBeDownloadedIsTriedAgain() {
        val playlist = rule("playlist/1")
        val item = withArt(song("a"), "beef")
        clementine.files.remove(item.art.url)
        manifest("playlist/1", item)
        val result = engine.sync(clementine)
        // The song's kept all the same.
        assertEquals(RuleResult.Synced(songs = 1, downloaded = 1, skipped = 0), result.rules[playlist])
        assertNull(store.item(SERVER, "a")!!.artKey)

        clementine.files[item.art.url] = "cover beef".toByteArray()
        engine.sync(clementine)
        assertEquals("beef", store.item(SERVER, "a")!!.artKey)
    }

    @Test
    fun coversNoSongUsesAreDeleted() {
        rule("playlist/1")
        manifest("playlist/1", withArt(song("a"), "beef"), withArt(song("b"), "f00d"))
        engine.sync(clementine)

        // One song goes, and the other's cover changes.
        manifest("playlist/1", withArt(song("b"), "cafe"))
        engine.sync(clementine)
        assertNull(store.art("beef"))
        assertNull(store.art("f00d"))
        assertFalse(cover("beef").exists())
        assertFalse(cover("f00d").exists())
        assertEquals("cover cafe", cover("cafe").readText())
    }

    @Test
    fun namesFilesForPeople() {
        val song = SongMetadata.newBuilder().setTitle("What/Ever?").setAlbumartist("A: B")
            .setArtist("Someone").setAlbum("").build()
        assertEquals("Synced/A_ B/Unknown album/", SyncFiles.relativeDir(song))
        assertEquals("What_Ever_.opus", SyncFiles.fileName(song, "audio/ogg; codecs=opus"))
        assertEquals("mp3", SyncFiles.extension("audio/mpeg"))
        assertEquals("ogg", SyncFiles.extension("audio/ogg; codecs=vorbis"))
    }

    private companion object {
        const val SERVER = "4f1c2d6e-8a0b-4c8e-9b7a-2e3f5d6c7b8a"
    }
}
