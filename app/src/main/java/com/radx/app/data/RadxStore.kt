package com.radx.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.radxDataStore by preferencesDataStore(name = "radx_store")

/**
 * Persists everything Radx needs to survive a restart: profiles (with their parsed
 * nodes), the active profile id and the app settings. Stored as JSON blobs inside
 * DataStore preferences, which keeps the project free of a database layer.
 */
class RadxStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private object Keys {
        val profiles = stringPreferencesKey("profiles")
        val selectedProfile = stringPreferencesKey("selected_profile")
        val settings = stringPreferencesKey("settings")
    }

    val profiles: Flow<List<Profile>> = context.radxDataStore.data.map { prefs ->
        decodeProfiles(prefs[Keys.profiles])
    }

    val selectedProfileId: Flow<String?> = context.radxDataStore.data.map { prefs ->
        prefs[Keys.selectedProfile]
    }

    val settings: Flow<AppSettings> = context.radxDataStore.data.map { prefs ->
        decodeSettings(prefs[Keys.settings])
    }

    suspend fun upsertProfile(profile: Profile) {
        context.radxDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[Keys.profiles])
            val updated = if (current.any { it.id == profile.id }) {
                current.map { if (it.id == profile.id) profile else it }
            } else {
                current + profile
            }
            prefs[Keys.profiles] = json.encodeToString(ProfileList.serializer(), ProfileList(updated))
            if (prefs[Keys.selectedProfile] == null) {
                prefs[Keys.selectedProfile] = profile.id
            }
        }
    }

    suspend fun deleteProfile(id: String) {
        context.radxDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[Keys.profiles])
            prefs[Keys.profiles] =
                json.encodeToString(ProfileList.serializer(), ProfileList(current.filterNot { it.id == id }))
            if (prefs[Keys.selectedProfile] == id) {
                val next = current.firstOrNull { it.id != id }?.id
                if (next == null) {
                    prefs.remove(Keys.selectedProfile)
                } else {
                    prefs[Keys.selectedProfile] = next
                }
            }
        }
    }

    suspend fun selectProfile(id: String?) {
        context.radxDataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(Keys.selectedProfile)
            } else {
                prefs[Keys.selectedProfile] = id
            }
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.radxDataStore.edit { prefs ->
            prefs[Keys.settings] = json.encodeToString(AppSettings.serializer(), settings)
        }
    }

    suspend fun readSelectedProfile(): Profile? {
        val prefs: Preferences = context.radxDataStore.data.first()
        val id = prefs[Keys.selectedProfile] ?: return null
        return decodeProfiles(prefs[Keys.profiles]).firstOrNull { it.id == id }
    }

    suspend fun readSettings(): AppSettings {
        val prefs: Preferences = context.radxDataStore.data.first()
        return decodeSettings(prefs[Keys.settings])
    }

    private fun decodeProfiles(raw: String?): List<Profile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(ProfileList.serializer(), raw).items }
            .getOrDefault(emptyList())
    }

    private fun decodeSettings(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
            .getOrDefault(AppSettings())
    }
}

@kotlinx.serialization.Serializable
private data class ProfileList(val items: List<Profile>)
