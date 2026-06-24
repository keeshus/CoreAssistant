package nl.codeinfinity.coreassistant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsManagerTest {
    private lateinit var mockContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testFile: File
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mockk<Context>()
        testFile = File.createTempFile("test_prefs", ".preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create { testFile }

        mockkStatic("nl.codeinfinity.coreassistant.SettingsManagerKt")
        every { mockContext.dataStore } returns testDataStore
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testFile.delete()
    }

    @Test
    fun `companion object keys have correct names`() {
        assertEquals("gemini_api_key_secure", SettingsManager.GEMINI_API_KEY_SECURE.name)
        assertEquals("db_passphrase", SettingsManager.DB_PASSPHRASE.name)
        assertEquals("gemini_model", SettingsManager.GEMINI_MODEL.name)
        assertEquals("image_generation_model", SettingsManager.IMAGE_GENERATION_MODEL.name)
        assertEquals("google_grounding_enabled", SettingsManager.GOOGLE_GROUNDING_ENABLED.name)
        assertEquals("gemini_thinking_level", SettingsManager.GEMINI_THINKING_LEVEL.name)
        assertEquals("conversations_limit", SettingsManager.CONVERSATIONS_LIMIT.name)
        assertEquals("user_name", SettingsManager.USER_NAME.name)
        assertEquals("screenshot_protection", SettingsManager.SCREENSHOT_PROTECTION.name)
        assertEquals("clear_history_on_close", SettingsManager.CLEAR_HISTORY_ON_CLOSE.name)
        assertEquals("dark_mode", SettingsManager.DARK_MODE.name)
    }

    @Test
    fun `default geminiModel`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals("gemini-3.0-flash-preview", manager.geminiModel.first())
    }

    @Test
    fun `default imageGenerationModel`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals("imagen-3.0-generate-001", manager.imageGenerationModel.first())
    }

    @Test
    fun `default googleGroundingEnabled is false`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals(false, manager.googleGroundingEnabled.first())
    }

    @Test
    fun `default conversationsLimit is 5`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals(5, manager.conversationsLimit.first())
    }

    @Test
    fun `default geminiThinkingLevel is OFF`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals("OFF", manager.geminiThinkingLevel.first())
    }

    @Test
    fun `default screenshotProtection is true`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals(true, manager.screenshotProtection.first())
    }

    @Test
    fun `default clearHistoryOnClose is false`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals(false, manager.clearHistoryOnClose.first())
    }

    @Test
    fun `default userName is User`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals("User", manager.userName.first())
    }

    @Test
    fun `default darkMode is system`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals("system", manager.darkMode.first())
    }

    @Test
    fun `geminiApiKey returns empty string when no key stored`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        assertEquals("", manager.geminiApiKey.first())
    }

    @Test
    fun `geminiApiKey returns empty string when stored value is invalid`() = runTest(testDispatcher) {
        testDataStore.edit { prefs ->
            prefs[SettingsManager.GEMINI_API_KEY_SECURE] = "corrupted-data"
        }
        val manager = SettingsManager(mockContext)
        assertEquals("", manager.geminiApiKey.first())
    }

    @Test
    fun `save and read geminiModel`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveGeminiModel("models/gemini-2.0-flash")
        assertEquals("models/gemini-2.0-flash", manager.geminiModel.first())
    }

    @Test
    fun `save and read imageGenerationModel`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveImageGenerationModel("models/imagen-3.1")
        assertEquals("models/imagen-3.1", manager.imageGenerationModel.first())
    }

    @Test
    fun `save and read googleGroundingEnabled`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveGoogleGroundingEnabled(true)
        assertEquals(true, manager.googleGroundingEnabled.first())
        manager.saveGoogleGroundingEnabled(false)
        assertEquals(false, manager.googleGroundingEnabled.first())
    }

    @Test
    fun `save and read geminiThinkingLevel`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveGeminiThinkingLevel("MEDIUM")
        assertEquals("MEDIUM", manager.geminiThinkingLevel.first())
        manager.saveGeminiThinkingLevel("HIGH")
        assertEquals("HIGH", manager.geminiThinkingLevel.first())
    }

    @Test
    fun `save and read conversationsLimit`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveConversationsLimit("10")
        assertEquals(10, manager.conversationsLimit.first())
        manager.saveConversationsLimit("25")
        assertEquals(25, manager.conversationsLimit.first())
    }

    @Test
    fun `conversationsLimit defaults to 5 when stored value is not an integer`() = runTest(testDispatcher) {
        testDataStore.edit { prefs ->
            prefs[SettingsManager.CONVERSATIONS_LIMIT] = "not-a-number"
        }
        val manager = SettingsManager(mockContext)
        assertEquals(5, manager.conversationsLimit.first())
    }

    @Test
    fun `save and read userName`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveUserName("Alice")
        assertEquals("Alice", manager.userName.first())
    }

    @Test
    fun `save and read screenshotProtection`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveScreenshotProtection(false)
        assertEquals(false, manager.screenshotProtection.first())
        manager.saveScreenshotProtection(true)
        assertEquals(true, manager.screenshotProtection.first())
    }

    @Test
    fun `save and read clearHistoryOnClose`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveClearHistoryOnClose(true)
        assertEquals(true, manager.clearHistoryOnClose.first())
        manager.saveClearHistoryOnClose(false)
        assertEquals(false, manager.clearHistoryOnClose.first())
    }

    @Test
    fun `save and read darkMode`() = runTest(testDispatcher) {
        val manager = SettingsManager(mockContext)
        manager.saveDarkMode("dark")
        assertEquals("dark", manager.darkMode.first())
        manager.saveDarkMode("light")
        assertEquals("light", manager.darkMode.first())
    }
}
