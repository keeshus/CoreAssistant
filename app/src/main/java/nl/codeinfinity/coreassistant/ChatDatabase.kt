package nl.codeinfinity.coreassistant

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val text: String,
    val isUser: Boolean,
    val thought: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY lastModified DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY lastModified ASC")
    suspend fun getAllConversationsSync(): List<Conversation>

    @Insert
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversationSync(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Transaction
    suspend fun createNewConversation(title: String): Long {
        return insertConversation(Conversation(title = title))
    }
    
    @Query("DELETE FROM conversations WHERE id NOT IN (SELECT id FROM conversations ORDER BY lastModified DESC LIMIT :limit)")
    suspend fun deleteOldConversations(limit: Int)
}

@Database(entities = [Conversation::class, MessageEntity::class], version = 1)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: android.content.Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
