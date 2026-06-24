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
class ConversationListViewModelTest {

    private lateinit var chatDao: ChatDao
    private lateinit var settingsManager: SettingsManager
    private lateinit var viewModel: ConversationListViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val conversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())
    private val conversationsLimitFlow = MutableStateFlow(5)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatDao = mockk()
        settingsManager = mockk()

        every { chatDao.getAllConversations() } returns conversationsFlow
        every { settingsManager.conversationsLimit } returns conversationsLimitFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModelAndAdvance(): ConversationListViewModel {
        val vm = ConversationListViewModel(chatDao, settingsManager)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `conversations flow starts empty`() = runTest {
        viewModel = createViewModelAndAdvance()
        assertTrue(viewModel.conversations.first().isEmpty())
    }

    @Test
    fun `conversations flow starts with dao data`() = runTest {
        conversationsFlow.value = listOf(
            Conversation(id = 1, title = "Chat 1"),
            Conversation(id = 2, title = "Chat 2")
        )
        viewModel = createViewModelAndAdvance()
        val result = conversationsFlow.value
        assertEquals(2, result.size)
        assertEquals("Chat 1", result[0].title)
    }

    @Test
    fun `startNewConversation creates conversation and invokes callback`() = runTest {
        coEvery { chatDao.createNewConversation(any(), any()) } returns 42L
        coEvery { chatDao.deleteOldConversations(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        var callbackId: Long? = null

        viewModel.startNewConversation(isImageGeneration = false) { id ->
            callbackId = id
        }

        advanceUntilIdle()

        coVerify {
            chatDao.deleteOldConversations(4)
            chatDao.createNewConversation("New Chat", false)
        }
        assertEquals(42L, callbackId)
    }

    @Test
    fun `startNewConversation creates image generation conversation`() = runTest {
        coEvery { chatDao.createNewConversation(any(), any()) } returns 99L
        coEvery { chatDao.deleteOldConversations(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        var callbackId: Long? = null

        viewModel.startNewConversation(isImageGeneration = true) { id ->
            callbackId = id
        }

        advanceUntilIdle()

        coVerify {
            chatDao.createNewConversation("Image Generation", true)
        }
        assertEquals(99L, callbackId)
    }

    @Test
    fun `deleteConversation calls dao delete`() = runTest {
        coEvery { chatDao.deleteConversation(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        val conversation = Conversation(id = 7, title = "To Delete")

        viewModel.deleteConversation(conversation)
        advanceUntilIdle()

        coVerify { chatDao.deleteConversation(conversation) }
    }

    @Test
    fun `startNewConversation enforces conversations limit`() = runTest {
        conversationsLimitFlow.value = 3
        coEvery { chatDao.createNewConversation(any(), any()) } returns 1L
        coEvery { chatDao.deleteOldConversations(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.startNewConversation(isImageGeneration = false) {}
        advanceUntilIdle()

        coVerify { chatDao.deleteOldConversations(2) }
    }

    @Test
    fun `startNewConversation with zero limit does not crash`() = runTest {
        conversationsLimitFlow.value = 0
        coEvery { chatDao.createNewConversation(any(), any()) } returns 1L
        coEvery { chatDao.deleteOldConversations(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        viewModel.startNewConversation(isImageGeneration = false) {}
        advanceUntilIdle()

        coVerify { chatDao.deleteOldConversations(-1) }
    }

    @Test
    fun `startNewConversation with limit 1 creates conversation`() = runTest {
        conversationsLimitFlow.value = 1
        coEvery { chatDao.createNewConversation(any(), any()) } returns 1L
        coEvery { chatDao.deleteOldConversations(any()) } just Runs

        viewModel = createViewModelAndAdvance()
        var callbackId: Long? = null

        viewModel.startNewConversation(isImageGeneration = false) { id ->
            callbackId = id
        }

        advanceUntilIdle()

        coVerify {
            chatDao.deleteOldConversations(0)
            chatDao.createNewConversation("New Chat", false)
        }
        assertEquals(1L, callbackId)
    }
}
