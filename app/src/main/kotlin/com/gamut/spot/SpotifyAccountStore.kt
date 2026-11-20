package com.gamut.spot

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The listener's Spotify web session, encrypted on this device and never synced or backed up. */
@Singleton
class SpotifyAccountStore @Inject constructor(@ApplicationContext private val context: Context) {
    private fun open(): SharedPreferences = EncryptedSharedPreferences.create(
        context, FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val prefs: SharedPreferences by lazy {
        try { open() } catch (_: Exception) { context.deleteSharedPreferences(FILE); open() }
    }
    fun cookie(): String? = runCatching { prefs.getString(KEY, null) }.getOrNull()
    fun save(cookie: String) { prefs.edit().putString(KEY, cookie).commit() }
    fun clear() { prefs.edit().clear().commit() }

    private companion object {
        const val FILE = "cadence_spotify_account"
        const val KEY = "sp_dc"
    }
}
