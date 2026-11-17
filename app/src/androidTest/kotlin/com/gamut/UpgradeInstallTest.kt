package com.gamut

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** CI installs alpha 8, seeds its actual v10 library, then uses adb install -r for this update. */
@RunWith(AndroidJUnit4::class)
class UpgradeInstallTest {
    @Test fun seedPreviousInstall() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradePhase") == "seed")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(10008L, context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("schema-v10.sql").bufferedReader().use { it.readText() }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("gamut.db").apply { parentFile!!.mkdirs() }, null).use { db ->
            schema.split(';').map(String::trim).filter(String::isNotEmpty).forEach(db::execSQL)
            db.execSQL("INSERT OR REPLACE INTO tracks(isrc,title,artist,durationMs,source,albumUri) VALUES('upgrade-saved','Keep this saved song','Upgrade Artist',60000,'SPOTIFY','spotify:album:4m2880jivSbbyEGAKfITCa')")
            db.execSQL("INSERT OR REPLACE INTO favorites VALUES('upgrade-saved',100)")
            db.execSQL("INSERT OR REPLACE INTO playlists(id,name,createdAtEpochMs) VALUES(9876,'Upgrade playlist',100)")
            db.execSQL("INSERT OR REPLACE INTO PlaylistTrackCrossRef VALUES(9876,'upgrade-saved',0)")
            db.version = 10
        }
        assertTrue(context.getSharedPreferences("update-proof", 0).edit().putString("marker", "alpha-8-library").commit())
        File(context.filesDir, "update-proof.txt").writeText("alpha-8-file")
    }
    @Test fun updatePreservesInstalledLibrary() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradePhase") == "verify")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode > 10008)
        assertEquals("alpha-8-library", context.getSharedPreferences("update-proof", 0).getString("marker", null))
        assertEquals("alpha-8-file", File(context.filesDir, "update-proof.txt").readText())
        val library = EntryPointAccessors.fromApplication(context, UiTestData::class.java).testLibraryRepository()
        assertTrue(library.observeFavorites().first().any { it.isrc == "upgrade-saved" })
        assertTrue(library.observePlaylists().first().any { it.id == 9876L && it.name == "Upgrade playlist" })
        assertEquals("spotify:album:4m2880jivSbbyEGAKfITCa", library.observeTracks(9876).first().single().albumUri)
    }
}
