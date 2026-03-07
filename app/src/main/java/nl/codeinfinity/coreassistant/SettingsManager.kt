package nl.codeinfinity.coreassistant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GOOGLE_GROUNDING_ENABLED = booleanPreferencesKey("google_grounding_enabled")
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: ""
    }

    val geminiModel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_MODEL] ?: "gemini-1.5-flash"
    }

    val googleGroundingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[GOOGLE_GROUNDING_ENABLED] ?: false
    }

    suspend fun saveGeminiApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = apiKey
        }
    }

    suspend fun saveGeminiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_MODEL] = model
        }
    }

    suspend fun saveGoogleGroundingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[GOOGLE_GROUNDING_ENABLED] = enabled
        }
    }
}
