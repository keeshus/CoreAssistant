package nl.codeinfinity.coreassistant

import android.util.Log
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isVoiceAssistant: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

data class Attachment(
    val uri: String, // Local Content URI
    val mimeType: String,
    val fileName: String,
    val fileSize: Long,
    val remoteUri: String? = null // Gemini File API URI if uploaded
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
    val groundingMetadata: GroundingMetadata? = null,
    val attachments: List<Attachment>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter
    fun fromGroundingMetadata(value: GroundingMetadata?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toGroundingMetadata(value: String?): GroundingMetadata? {
        return Gson().fromJson(value, GroundingMetadata::class.java)
    }

    @TypeConverter
    fun fromAttachmentList(value: List<Attachment>?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toAttachmentList(value: String?): List<Attachment>? {
        val listType = object : TypeToken<List<Attachment>>() {}.type
        return Gson().fromJson(value, listType)
    }
}

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
    
    @Query("DELETE FROM conversations WHERE id NOT IN (SELECT id FROM conversations WHERE title != 'Voice Assistant' ORDER BY lastModified DESC LIMIT :limit) AND title != 'Voice Assistant'")
    suspend fun deleteOldConversations(limit: Int)

    @Query("SELECT * FROM conversations WHERE title = 'Voice Assistant' LIMIT 1")
    suspend fun getVoiceAssistantConversation(): Conversation?

    @Transaction
    suspend fun getOrCreateVoiceAssistantConversation(): Long {
        val existing = getVoiceAssistantConversation()
        if (existing != null) {
            return existing.id
        }
        return insertConversation(Conversation(title = "Voice Assistant"))
    }
}

@Database(entities = [Conversation::class, MessageEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null
        private val mutex = Mutex()

        suspend fun getDatabase(context: android.content.Context): ChatDatabase {
            val dbName = "chat_database"
            INSTANCE?.let { return it }
            
            return mutex.withLock {
                INSTANCE?.let { return it }
                
                // Initialize SQLCipher libraries
                SQLiteDatabase.loadLibs(context.applicationContext)
                
                val settingsManager = SettingsManager(context.applicationContext)
                val passphrase = settingsManager.getDatabasePassphrase()
                val factory = SupportFactory(passphrase.toByteArray())

                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    dbName
                )
                .openHelperFactory(factory)
                // TODO: Replace with proper migrations before production release
                // Current version is 3. Fallback to destructive migration is used for simplicity during development.
                .fallbackToDestructiveMigration(true)

                var instance = builder.build()
                
                // If we are not on the main thread, we can check for the passphrase error immediately.
                // If we ARE on the main thread, we'll let the error happen when the database is actually used,
                // or we can just hope for the best.
                // However, the best practice is to always initialize on a background thread.
                if (android.os.Looper.getMainLooper().thread != Thread.currentThread()) {
                    try {
                        // Force open the database to check if the passphrase is correct.
                        instance.openHelper.writableDatabase
                    } catch (e: Exception) {
                        if (e.message?.contains("file is not a database", ignoreCase = true) == true) {
                            Log.e("CoreAssistant", "Database encryption mismatch. Resetting database.")
                            context.deleteDatabase(dbName)
                            instance = builder.build()
                        } else {
                            throw e
                        }
                    }
                }

                INSTANCE = instance
                instance
            }
        }
    }
}
