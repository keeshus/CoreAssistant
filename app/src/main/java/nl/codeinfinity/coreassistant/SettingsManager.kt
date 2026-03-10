package nl.codeinfinity.coreassistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    private val keyAlias = "gemini_api_key_alias"
    private val dbKeyAlias = "chat_db_key_alias"
    private val androidKeyStore = "AndroidKeyStore"

    private fun getOrCreateKey(alias: String = keyAlias): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(data: String, alias: String = keyAlias): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val combined = iv + encryptedData
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun decrypt(encryptedDataWithIv: String, alias: String = keyAlias): String? {
        return try {
            val combined = Base64.decode(encryptedDataWithIv, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val ivSize = 12 // Standard GCM IV size
            val iv = combined.copyOfRange(0, ivSize)
            val encryptedData = combined.copyOfRange(ivSize, combined.size)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), spec)
            String(cipher.doFinal(encryptedData), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getDatabasePassphrase(): String {
        val preferences = context.dataStore.data.first()
        val encryptedKey = preferences[DB_PASSPHRASE]
        
        return if (encryptedKey != null) {
            decrypt(encryptedKey, dbKeyAlias) ?: generateAndSaveNewDbPassphrase()
        } else {
            generateAndSaveNewDbPassphrase()
        }
    }

    private suspend fun generateAndSaveNewDbPassphrase(): String {
        val newPassphrase = java.util.UUID.randomUUID().toString()
        val encrypted = encrypt(newPassphrase, dbKeyAlias)
        context.dataStore.edit { preferences ->
            preferences[DB_PASSPHRASE] = encrypted
        }
        return newPassphrase
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY_SECURE]?.let { decrypt(it) } ?: ""
    }

    companion object {
        val GEMINI_API_KEY_SECURE = stringPreferencesKey("gemini_api_key_secure")
        val DB_PASSPHRASE = stringPreferencesKey("db_passphrase")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GOOGLE_GROUNDING_ENABLED = booleanPreferencesKey("google_grounding_enabled")
        val GEMINI_THINKING_LEVEL = stringPreferencesKey("gemini_thinking_level")
        val CHAT_HISTORY = stringPreferencesKey("chat_history")
        val CONVERSATIONS_LIMIT = stringPreferencesKey("conversations_limit")
        val USER_NAME = stringPreferencesKey("user_name")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
        val CLEAR_HISTORY_ON_CLOSE = booleanPreferencesKey("clear_history_on_close")
    }

    val screenshotProtection: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREENSHOT_PROTECTION] ?: true // Default to true for privacy
    }

    val clearHistoryOnClose: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CLEAR_HISTORY_ON_CLOSE] ?: false
    }

    suspend fun saveScreenshotProtection(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCREENSHOT_PROTECTION] = enabled
        }
    }

    suspend fun saveClearHistoryOnClose(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CLEAR_HISTORY_ON_CLOSE] = enabled
        }
    }

    val userName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME] ?: "User"
    }

    val geminiModel: Flow<String> = context.dataStore.data.map {
preferences ->
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

    suspend fun saveGeminiApiKey(apiKey: String) {
        val encrypted = encrypt(apiKey)
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY_SECURE] = encrypted
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

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }

}
