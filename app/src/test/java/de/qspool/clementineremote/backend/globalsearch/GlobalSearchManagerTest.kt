package de.qspool.clementineremote.backend.globalsearch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.GlobalSearchStatus
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseGlobalSearchStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Where the results of a global search are cleared. Connecting happens on the main thread, so it
 * must not be there: Android counts writing to disk on it a StrictMode violation.
 */
@RunWith(RobolectricTestRunner::class)
class GlobalSearchManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val database = GlobalSearchDatabaseHelper(context)

    /**
     * Built after the singleton is dropped, so it holds this test's app rather than an earlier
     * test's, whose database is a different file.
     */
    private lateinit var manager: GlobalSearchManager

    private fun rows(): Int =
        database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${GlobalSearchDatabaseHelper.TABLE_NAME}",
            null,
        ).use { it.moveToFirst(); it.getInt(0) }

    private fun searchStarted(id: Int) =
        manager.parseClementineMessage(
            ClementineMessage(
                Message.newBuilder()
                    .setType(MsgType.GLOBAL_SEARCH_STATUS)
                    .setResponseGlobalSearchStatus(
                        ResponseGlobalSearchStatus.newBuilder()
                            .setId(id)
                            .setStatus(GlobalSearchStatus.GlobalSearchStarted),
                    )
                    .build(),
            ),
        )

    @Before
    fun setUp() {
        GlobalSearchManager::class.java.getDeclaredField("mInstance").apply {
            isAccessible = true
            set(null, null)
        }
        manager = GlobalSearchManager.getInstance()

        database.deleteAll()
        database.writableDatabase.execSQL(
            "INSERT INTO ${GlobalSearchDatabaseHelper.TABLE_NAME}" +
                " (global_search_id, filename, is_local, filesize, url) VALUES (1, 'a.mp3', 1, 1, '')",
        )
    }

    @Test
    fun connectingLeavesTheResultsOfTheLastSearchAlone() {
        manager.reset()

        assertEquals(1, rows())
    }

    @Test
    fun aNewSearchClearsTheResultsOfTheLastOne() {
        searchStarted(2)

        assertEquals(0, rows())
    }
}
