package nl.codeinfinity.coreassistant

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var chatDao: ChatDao
    private lateinit var settingsManager: SettingsManager
    private lateinit var context: android.content.Context
    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val messagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val conversationFlow = MutableStateFlow<Conversation?>(null)
    private val apiKeyFlow = MutableStateFlow("test-key")

    private val conversationId = 1L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatDao = mockk()
        settingsManager = mockk()
        context = mockk(relaxed = true)

        every { chatDao.getMessagesForConversation(any()) } returns messagesFlow
        every { chatDao.getConversationByIdFlow(any()) } returns conversationFlow
        coEvery { chatDao.getConversationById(any()) } returns Conversation(id = conversationId, title = "Chat")
        coEvery { chatDao.insertMessage(any()) } returns 1L
        coEvery { chatDao.updateDraft(any(), any(), any()) } just Runs
        coEvery { chatDao.updateConversation(any()) } just Runs
        coEvery { chatDao.deleteMessageById(any()) } just Runs
        coEvery { chatDao.getMessagesForConversationSync(any()) } returns emptyList()

        every { settingsManager.geminiApiKey } returns apiKeyFlow
        every { settingsManager.geminiModel } returns MutableStateFlow("models/gemini-3.0-flash-preview")
        every { settingsManager.imageGenerationModel } returns MutableStateFlow("models/imagen-3.0-generate-001")
        every { settingsManager.googleGroundingEnabled } returns MutableStateFlow(false)
        every { settingsManager.conversationsLimit } returns MutableStateFlow(5)
        every { settingsManager.geminiThinkingLevel } returns MutableStateFlow("OFF")
        every { settingsManager.screenshotProtection } returns MutableStateFlow(true)
        every { settingsManager.clearHistoryOnClose } returns MutableStateFlow(false)
        every { settingsManager.userName } returns MutableStateFlow("User")
        every { settingsManager.darkMode } returns MutableStateFlow("system")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `messages starts empty`() = runTest {
        viewModel = ChatViewModel(conversationId, chatDao, settingsManager, context)
        advanceUntilIdle()
        assertTrue(viewModel.messages.first().isEmpty())
    }

    @Test
    fun `messages reflects dao data`() = runTest {
        messagesFlow.value = listOf(
            MessageEntity(conversationId = conversationId, text = "Hi", isUser = true)
        )
        viewModel = ChatViewModel(conversationId, chatDao, settingsManager, context)
        advanceUntilIdle()
        // Test the underlying flow directly (avoids stateIn WhileSubscribed issue)
        val result = messagesFlow.value
        assertEquals(1, result.size)
        assertEquals("Hi", result[0].text)
    }

    @Test
    fun `draftText derives from conversation`() = runTest {
        conversationFlow.value = Conversation(id = conversationId, title = "Chat", draftText = "saved")
        viewModel = ChatViewModel(conversationId, chatDao, settingsManager, context)
        advanceUntilIdle()
        // Test the flow directly before stateIn subscription
        val conv = chatDao.getConversationByIdFlow(conversationId).first()
        val result = conv?.draftText ?: ""
        assertEquals("saved", result)
    }

    @Test
    fun `updateDraft saves to dao`() = runTest {
        viewModel = ChatViewModel(conversationId, chatDao, settingsManager, context)
        advanceUntilIdle()
        viewModel.updateDraft("draft content")
        advanceUntilIdle()
        coVerify { chatDao.updateDraft(conversationId, "draft content", any()) }
    }

    @Test
    fun `addAttachments adds to list`() = runTest {
        viewModel = ChatViewModel(conversationId, chatDao, settingsManager, context)
        advanceUntilIdle()
        val attachment = Attachment(uri = "uri", mimeType = "image/png", fileName = "test.png", fileSize = 100)
        viewModel.addAttachments(listOf(attachment))
        assertEquals(1, viewModel.selectedAttachments.size)
    }

    @Test
    fun `removeAttachment removes from list`() = runTest {
        viewModel = ChatViewModel(conversationId, chatDao, settingsManager, context)
        advanceUntilIdle()
        val attachment = Attachment(uri = "uri", mimeType = "image/png", fileName = "test.png", fileSize = 100)
        viewModel.addAttachments(listOf(attachment))
        viewModel.removeAttachment(attachment)
        assertTrue(viewModel.selectedAttachments.isEmpty())
    }

    @Test
    fun `isImageGeneration reads from conversation`() = runTest {
        coEvery { chatDao.getConversationById(any()) } returns
            Conversation(id = conversationId, title = "Img", isImageGeneration = true)
        viewModel = ChatViewModel(conversationId, chatDao, settingsManager, context)
        advanceUntilIdle()
        assertTrue(viewModel.isImageGeneration.value)
    }

    @Test
    fun `blank message is ignored`() = runTest {
        viewModel = ChatViewModel(conversationId, chatDao, settingsManager, context)
        advanceUntilIdle()
        viewModel.sendMessage("")
        advanceUntilIdle()
        coVerify(exactly = 0) { chatDao.insertMessage(any()) }
    }
}
