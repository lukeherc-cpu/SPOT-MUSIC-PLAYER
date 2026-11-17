package com.gamut

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamut.core.database.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchemaUpgradeTest {
    @Test fun alphaSixLibrarySurvivesAlbumLinkUpgrade() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "album-upgrade-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("schema-v9.sql").bufferedReader().use { it.readText() }
            schema.split(';').map(String::trim).filter(String::isNotEmpty).forEach(old::execSQL)
            old.execSQL("INSERT INTO tracks(isrc,title,artist,album,durationMs,source) VALUES('saved','Saved song','Artist','Album',60000,'SPOTIFY')")
            old.execSQL("INSERT INTO favorites VALUES('saved',100)")
            old.execSQL("INSERT INTO playlists(id,name,createdAtEpochMs) VALUES(1,'Keep me',100)")
            old.execSQL("INSERT INTO PlaylistTrackCrossRef VALUES(1,'saved',0)")
            old.version = 9
        }
        try {
            val db = Room.databaseBuilder(context, GamutDatabase::class.java, name).addMigrations(MIGRATION_9_10).build()
            try {
                val oldSong = db.trackDao().getByIsrcs(listOf("saved")).single()
                assertEquals("Saved song", oldSong.title)
                assertNull(oldSong.albumUri)
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM favorites").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
                db.openHelper.readableDatabase.query("SELECT name FROM playlists WHERE id=1").use { it.moveToFirst(); assertEquals("Keep me", it.getString(0)) }
                val uri = "spotify:album:4m2880jivSbbyEGAKfITCa"
                db.openHelper.writableDatabase.execSQL("UPDATE tracks SET albumUri=? WHERE isrc='saved'", arrayOf(uri))
                assertEquals(uri, db.trackDao().getByIsrcs(listOf("saved")).single().toDomain().albumUri)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
