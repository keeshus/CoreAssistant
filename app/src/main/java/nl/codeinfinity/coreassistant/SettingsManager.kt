package nl.codeinfinity.coreassistant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "secure_settings",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _geminiApiKey = MutableStateFlow(encryptedPrefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: Flow<String> = _geminiApiKey.asStateFlow()

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GOOGLE_GROUNDING_ENABLED = booleanPreferencesKey("google_grounding_enabled")
        val GEMINI_THINKING_LEVEL = stringPreferencesKey("gemini_thinking_level")
        val CHAT_HISTORY = stringPreferencesKey("chat_history")
        val CONVERSATIONS_LIMIT = stringPreferencesKey("conversations_limit")
    }

    val geminiModel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_MODEL] ?: "gemini-1.5-flash"
    }

    val googleGroundingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[GOOGLE_GROUNDING_ENABLED] ?: false
    }

    val conversationsLimit: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CONVERSATIONS_LIMIT]?.toIntOrNull() ?: 5
    }

    val geminiThinkingLevel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_THINKING_LEVEL] ?: "OFF"
    }

    fun getHistory(): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CHAT_HISTORY]
    }

    suspend fun saveGeminiApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("gemini_api_key", apiKey).apply()
        _geminiApiKey.value = apiKey
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

    suspend fun saveGeminiThinkingLevel(level: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_THINKING_LEVEL] = level
        }
    }

    suspend fun saveConversationsLimit(limit: String) {
        context.dataStore.edit { preferences ->
            preferences[CONVERSATIONS_LIMIT] = limit
        }
    }

    suspend fun saveHistory(historyJson: String) {
        context.dataStore.edit { preferences ->
            preferences[CHAT_HISTORY] = historyJson
        }
    }
}
