package nl.codeinfinity.coreassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatDaoRobolectricTest {

    private lateinit var database: ChatDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var geminiModelDao: GeminiModelDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chatDao = database.chatDao()
        geminiModelDao = database.geminiModelDao()
    }

    @After
    fun tearDown() {
        database.clearAllTables()
        database.close()
    }

    // ── ChatDao tests ──────────────────────────────────────────────────────

    @Test
    fun insertAndGetConversation() = runTest {
        val id = chatDao.insertConversation(
            Conversation(title = "Test Title", isImageGeneration = true)
        )
        val conversation = chatDao.getConversationById(id)
        assertNotNull(conversation)
        assertEquals("Test Title", conversation!!.title)
        assertTrue(conversation.isImageGeneration)
    }

    @Test
    fun getAllConversations_sortedByLastModified() = runTest {
        chatDao.insertConversation(Conversation(title = "Old", lastModified = 1000))
        chatDao.insertConversation(Conversation(title = "New", lastModified = 2000))

        val conversations = chatDao.getAllConversations().first()
        assertEquals(2, conversations.size)
        assertEquals("New", conversations[0].title)
        assertEquals("Old", conversations[1].title)
    }

    @Test
    fun createNewConversation() = runTest {
        val id = chatDao.createNewConversation("New Conversation", isImageGeneration = false)
        assertTrue(id > 0)

        val conversation = chatDao.getConversationById(id)
        assertNotNull(conversation)
        assertEquals("New Conversation", conversation!!.title)
        assertFalse(conversation.isImageGeneration)
    }

    @Test
    fun deleteConversation() = runTest {
        val id = chatDao.insertConversation(Conversation(title = "To Delete"))
        assertNotNull(chatDao.getConversationById(id))

        chatDao.deleteConversation(chatDao.getConversationById(id)!!)
        assertNull(chatDao.getConversationById(id))
    }

    @Test
    fun insertAndGetMessage() = runTest {
        val convId = chatDao.insertConversation(Conversation(title = "Conv"))

        val msgId = chatDao.insertMessage(
            MessageEntity(
                conversationId = convId,
                text = "Hello",
                isUser = true,
                thought = "thinking...",
                thoughtSignature = "sig123"
            )
        )

        val messages = chatDao.getMessagesForConversationSync(convId)
        assertEquals(1, messages.size)

        val msg = messages[0]
        assertEquals("Hello", msg.text)
        assertTrue(msg.isUser)
        assertEquals("thinking...", msg.thought)
        assertEquals("sig123", msg.thoughtSignature)
    }

    @Test
    fun deleteMessageById() = runTest {
        val convId = chatDao.insertConversation(Conversation(title = "Conv"))
        val msgId = chatDao.insertMessage(
            MessageEntity(conversationId = convId, text = "Delete me", isUser = true)
        )
        assertEquals(1, chatDao.getMessagesForConversationSync(convId).size)

        chatDao.deleteMessageById(msgId)
        assertTrue(chatDao.getMessagesForConversationSync(convId).isEmpty())
    }

    @Test
    fun updateDraft() = runTest {
        val id = chatDao.insertConversation(Conversation(title = "Draft Test"))

        chatDao.updateDraft(id, "draft content", 5000)

        val conversation = chatDao.getConversationById(id)
        assertEquals("draft content", conversation!!.draftText)
        assertEquals(5000, conversation.lastDraftUpdate)
    }

    @Test
    fun deleteOldConversations() = runTest {
        chatDao.insertConversation(Conversation(title = "A", lastModified = 1000))
        chatDao.insertConversation(Conversation(title = "B", lastModified = 2000))
        chatDao.insertConversation(Conversation(title = "C", lastModified = 3000))

        chatDao.deleteOldConversations(2)

        val remaining = chatDao.getAllConversations().first()
        assertEquals(2, remaining.size)
        assertEquals("C", remaining[0].title)
        assertEquals("B", remaining[1].title)
    }

    @Test
    fun updateConversation() = runTest {
        val id = chatDao.insertConversation(Conversation(title = "Original"))

        val conversation = chatDao.getConversationById(id)!!.copy(title = "Updated")
        chatDao.updateConversation(conversation)

        val updated = chatDao.getConversationById(id)
        assertEquals("Updated", updated!!.title)
    }

    @Test
    fun getConversationByIdFlow() = runTest {
        val id = chatDao.insertConversation(Conversation(title = "Flow Test"))

        val result = chatDao.getConversationByIdFlow(id).first()
        assertNotNull(result)
        assertEquals("Flow Test", result!!.title)
    }

    // ── GeminiModelDao tests ───────────────────────────────────────────────

    @Test
    fun insertModelsAndGetAll() = runTest {
        val models = listOf(
            GeminiModelEntity(name = "model-1", displayName = "Model One", description = "First model"),
            GeminiModelEntity(name = "model-2", displayName = "Model Two", description = "Second model")
        )
        geminiModelDao.insertModels(models)

        val all = geminiModelDao.getAllModels().first()
        assertEquals(2, all.size)
    }

    @Test
    fun deleteAllModels() = runTest {
        geminiModelDao.insertModels(
            listOf(GeminiModelEntity(name = "to-delete", displayName = "Delete Me", description = "gone"))
        )
        assertFalse(geminiModelDao.getAllModels().first().isEmpty())

        geminiModelDao.deleteAllModels()
        assertTrue(geminiModelDao.getAllModels().first().isEmpty())
    }

    @Test
    fun insertModels_replacesOnConflict() = runTest {
        geminiModelDao.insertModels(
            listOf(GeminiModelEntity(name = "gemini-pro", displayName = "Original", description = "desc"))
        )
        geminiModelDao.insertModels(
            listOf(GeminiModelEntity(name = "gemini-pro", displayName = "Replaced", description = "desc"))
        )

        val all = geminiModelDao.getAllModels().first()
        assertEquals(1, all.size)
        assertEquals("Replaced", all[0].displayName)
    }
}
