package nl.codeinfinity.coreassistant

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var settingsManager: SettingsManager
    private lateinit var database: ChatDatabase
    private lateinit var geminiModelDao: GeminiModelDao
    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val apiKeyFlow = MutableStateFlow("")
    private val modelFlow = MutableStateFlow("models/gemini-3.0-flash-preview")
    private val imageModelFlow = MutableStateFlow("models/imagen-3.0-generate-001")
    private val groundingFlow = MutableStateFlow(false)
    private val limitFlow = MutableStateFlow(5)
    private val thinkingFlow = MutableStateFlow("OFF")
    private val screenshotFlow = MutableStateFlow(true)
    private val historyFlow = MutableStateFlow(false)
    private val userNameFlow = MutableStateFlow("User")
    private val darkModeFlow = MutableStateFlow("system")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsManager = mockk()
        database = mockk()
        geminiModelDao = mockk()

        every { database.geminiModelDao() } returns geminiModelDao
        every { geminiModelDao.getAllModels() } returns flowOf(emptyList())

        every { settingsManager.geminiApiKey } returns apiKeyFlow
        every { settingsManager.geminiModel } returns modelFlow
        every { settingsManager.imageGenerationModel } returns imageModelFlow
        every { settingsManager.googleGroundingEnabled } returns groundingFlow
        every { settingsManager.conversationsLimit } returns limitFlow
        every { settingsManager.geminiThinkingLevel } returns thinkingFlow
        every { settingsManager.screenshotProtection } returns screenshotFlow
        every { settingsManager.clearHistoryOnClose } returns historyFlow
        every { settingsManager.userName } returns userNameFlow
        every { settingsManager.darkMode } returns darkModeFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModelAndAdvance(): SettingsViewModel {
        val vm = SettingsViewModel(settingsManager, database)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `saveApiKey delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveGeminiApiKey(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveApiKey("new-key")
        advanceUntilIdle()

        coVerify { settingsManager.saveGeminiApiKey("new-key") }
    }

    @Test
    fun `saveModel delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveGeminiModel(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveModel("models/gemini-3.0-pro")
        advanceUntilIdle()

        coVerify { settingsManager.saveGeminiModel("models/gemini-3.0-pro") }
    }

    @Test
    fun `saveGrounding delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveGoogleGroundingEnabled(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveGrounding(true)
        advanceUntilIdle()

        coVerify { settingsManager.saveGoogleGroundingEnabled(true) }
    }

    @Test
    fun `saveThinkingLevel delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveGeminiThinkingLevel(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveThinkingLevel("HIGH")
        advanceUntilIdle()

        coVerify { settingsManager.saveGeminiThinkingLevel("HIGH") }
    }

    @Test
    fun `saveScreenshotProtection delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveScreenshotProtection(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveScreenshotProtection(false)
        advanceUntilIdle()

        coVerify { settingsManager.saveScreenshotProtection(false) }
    }

    @Test
    fun `saveConversationsLimit delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveConversationsLimit(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveConversationsLimit("10")
        advanceUntilIdle()

        coVerify { settingsManager.saveConversationsLimit("10") }
    }

    @Test
    fun `saveClearHistoryOnClose delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveClearHistoryOnClose(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveClearHistoryOnClose(true)
        advanceUntilIdle()

        coVerify { settingsManager.saveClearHistoryOnClose(true) }
    }

    @Test
    fun `saveUserName delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveUserName(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveUserName("Alice")
        advanceUntilIdle()

        coVerify { settingsManager.saveUserName("Alice") }
    }

    @Test
    fun `saveImageModel delegates to settings manager`() = runTest {
        coEvery { settingsManager.saveImageGenerationModel(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.saveImageModel("models/imagen-3.0-generate-001")
        advanceUntilIdle()

        coVerify { settingsManager.saveImageGenerationModel("models/imagen-3.0-generate-001") }
    }

    @Test
    fun `availableModels maps entities to domain models`() = runTest {
        val entities = listOf(
            GeminiModelEntity("models/gemini-3.0-flash", "Gemini 3.0 Flash", "Fast model"),
            GeminiModelEntity("models/gemini-3.0-pro", "Gemini 3.0 Pro", "Capable model")
        )
        every { geminiModelDao.getAllModels() } returns flowOf(entities)

        val result = database.geminiModelDao().getAllModels().first()
        val mapped = result.map { entity ->
            GeminiModel(entity.name, entity.displayName, entity.description)
        }

        assertEquals(2, mapped.size)
        assertEquals("models/gemini-3.0-flash", mapped[0].name)
        assertEquals("Gemini 3.0 Flash", mapped[0].displayName)
        assertEquals("Fast model", mapped[0].description)
        assertEquals("models/gemini-3.0-pro", mapped[1].name)
    }

    @Test
    fun `availableModels handles empty dao response`() = runTest {
        every { geminiModelDao.getAllModels() } returns flowOf(emptyList())

        val result = database.geminiModelDao().getAllModels().first()

        assertTrue(result.isEmpty())
    }
}
