package de.qspool.clementineremote.integration

import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.os.Looper
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementineLibraryDownloader
import de.qspool.clementineremote.backend.elements.DownloaderResult
import de.qspool.clementineremote.backend.library.LibraryDatabaseHelper
import de.qspool.clementineremote.backend.listener.OnLibraryDownloadListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowStatFs

/**
 * The app's library sync against a real Clementine: the library arrives in a file of its own and
 * replaces the one on the phone only once it's complete and indexed.
 */
@RunWith(RobolectricTestRunner::class)
class LibrarySyncIntegrationTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun requireClementine() {
            assumeTrue("clementine.host not set", ClementineSession.HOST != null)
        }
    }

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.getPreferences().edit()
            .putString(SharedPreferencesKeys.SP_KEY_IP, ClementineSession.HOST)
            .putString(SharedPreferencesKeys.SP_KEY_PORT, ClementineSession.PORT.toString())
            .putInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, ClementineSession.AUTH_CODE)
            .commit()
        // Room for the library: Robolectric reports no free space otherwise.
        ShadowStatFs.registerStats(Environment.getExternalStorageDirectory(), 1_000_000, 1_000_000, 1_000_000)
    }

    @Test
    fun replacesTheLibraryOnThePhone() {
        val helper = LibraryDatabaseHelper()
        // An old library, which the new one replaces.
        helper.libraryDb.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(helper.libraryDb, null).use {
            it.execSQL("CREATE TABLE songs (artist TEXT)")
            it.execSQL("INSERT INTO songs VALUES ('Old artist')")
        }

        var result: DownloaderResult? = null
        ClementineLibraryDownloader(App.getApp()).apply {
            addOnLibraryDownloadListener(object : OnLibraryDownloadListener {
                override fun OnProgressUpdate(progress: Long, total: Int) {}
                override fun OnOptimizeLibrary() {}
                override fun OnLibraryDownloadFinished(r: DownloaderResult) {
                    result = r
                }
            })
            startDownload(ClementineMessage.getMessage(MsgType.GET_LIBRARY))
        }
        // The download runs in the background and reports on the main thread.
        val deadline = System.currentTimeMillis() + 30_000
        while (result == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }

        assertNotNull("The sync didn't finish", result)
        assertEquals(DownloaderResult.DownloadResult.SUCCESSFUL, result!!.result)
        assertFalse("The partial library was left behind", helper.partialLibraryDb.exists())
        SQLiteDatabase.openDatabase(helper.libraryDb.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT count(*), count(DISTINCT artist) FROM songs", null).use {
                it.moveToFirst()
                assertEquals(10, it.getInt(0))
                assertEquals(2, it.getInt(1))
            }
            // Indexed for searching.
            db.rawQuery("SELECT count(*) FROM songs_fts", null).use {
                it.moveToFirst()
                assertEquals(10, it.getInt(0))
            }
        }
    }
}
