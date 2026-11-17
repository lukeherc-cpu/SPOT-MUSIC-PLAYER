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
            val db = Room.databaseBuilder(context, GamutDatabase::class.java, name).addMigrations(MIGRATION_9_10, MIGRATION_10_11).build()
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
    @Test fun alphaEightLibraryKeepsMetadataAfterUpgrade() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "metadata-upgrade-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { old ->
            val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("schema-v10.sql").bufferedReader().use { it.readText() }
            schema.split(';').map(String::trim).filter(String::isNotEmpty).forEach(old::execSQL)
            old.execSQL("INSERT INTO tracks(isrc,title,artist,durationMs,source,albumUri) VALUES('kept','Saved song','Artist',60000,'SPOTIFY','spotify:album:4m2880jivSbbyEGAKfITCa')")
            old.execSQL("INSERT INTO favorites VALUES('kept',100)")
            old.execSQL("INSERT INTO playlists(id,name,createdAtEpochMs) VALUES(1,'Keep this playlist',100)")
            old.execSQL("INSERT INTO PlaylistTrackCrossRef VALUES(1,'kept',0)")
            old.version = 10
        }
        try {
            val db = Room.databaseBuilder(context, GamutDatabase::class.java, name).addMigrations(MIGRATION_10_11).build()
            try {
                val track = db.trackDao().getByIsrcs(listOf("kept")).single().toDomain()
                assertEquals("spotify:album:4m2880jivSbbyEGAKfITCa", track.albumUri)
                assertNull(track.isExplicit)
                val updated = track.copy(isExplicit = true, artists = listOf(com.gamut.core.model.TrackArtist("Artist", "spotify:artist:4tZwfgrHOc3mvqYlEYSvVi")), transcripts = listOf(com.gamut.core.model.TranscriptSource("https://example.org/transcript.vtt", "text/vtt", "en")))
                db.trackDao().upsert(updated.toEntity())
                assertEquals(updated, db.trackDao().getByIsrcs(listOf("kept")).single().toDomain())
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM favorites").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM PlaylistTrackCrossRef").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
