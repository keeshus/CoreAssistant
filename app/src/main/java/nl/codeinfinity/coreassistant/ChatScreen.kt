package nl.codeinfinity.coreassistant

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val thought: String? = null,
    val groundingMetadata: GroundingMetadata? = null,
    val isLoading: Boolean = false
)

class ChatViewModel(
    private val conversationId: Long,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager
) : ViewModel() {
    val messages: StateFlow<List<ChatMessage>> = chatDao.getMessagesForConversation(conversationId)
        .map { entities ->
            entities.map { entity ->
                ChatMessage(
                    text = entity.text,
                    isUser = entity.isUser,
                    thought = entity.thought,
                    groundingMetadata = entity.groundingMetadata
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _loadingMessage = mutableStateOf<ChatMessage?>(null)
    val loadingMessage: ChatMessage? get() = _loadingMessage.value

    private val apiService = GeminiApiService.create()

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        viewModelScope.launch {
            // Save user message to DB
            withContext(Dispatchers.IO) {
                chatDao.insertMessage(MessageEntity(conversationId = conversationId, text = userText, isUser = true))
            }
            
            // Update conversation timestamp and title if it was "New Chat"
            val currentMessages = messages.value
            if (currentMessages.size <= 1) { // 1 because we just inserted user message
                val title = if (userText.length > 30) userText.take(27) + "..." else userText
                withContext(Dispatchers.IO) {
                    chatDao.updateConversation(Conversation(id = conversationId, title = title, lastModified = System.currentTimeMillis()))
                }
            } else {
                val conv = withContext(Dispatchers.IO) {
                    chatDao.getAllConversationsSync().find { it.id == conversationId }
                }
                conv?.let {
                    withContext(Dispatchers.IO) {
                        chatDao.updateConversation(it.copy(lastModified = System.currentTimeMillis()))
                    }
                }
            }

            // Show loading
            _loadingMessage.value = ChatMessage("", isUser = false, isLoading = true)

            try {
                val apiKey = settingsManager.geminiApiKey.first()
                if (apiKey.isBlank()) {
                    _loadingMessage.value = ChatMessage("Error: API Key is not set.", isUser = false)
                    return@launch
                }

                val modelName = settingsManager.geminiModel.first().let {
                    if (it.startsWith("models/")) it.substringAfter("models/") else it
                }
                val isGroundingEnabled = settingsManager.googleGroundingEnabled.first()
                val tools = if (isGroundingEnabled) listOf(Tool(googleSearch = GoogleSearch())) else null

                // History from DB
                val historyEntities = withContext(Dispatchers.IO) {
                    chatDao.getMessagesForConversationSync(conversationId)
                }
                val contents = historyEntities.map { msg ->
                    Content(role = if (msg.isUser) "user" else "model", parts = listOf(Part(text = msg.text)))
                }

                val thinkingLevel = settingsManager.geminiThinkingLevel.first()
                val configuration = if (thinkingLevel != "OFF") {
                    GenerationConfig(
                        thinkingConfig = ThinkingConfig(
                            includeThoughts = true,
                            thinkingLevel = thinkingLevel
                        )
                    )
                } else {
                    null
                }
                
                val request = GenerateContentRequest(contents = contents, tools = tools, generationConfig = configuration)
                val response = apiService.generateContent(model = modelName, apiKey = apiKey, request = request)

                val responseText = response.text
                val responseThought = response.thought
                val groundingMetadata = response.groundingMetadata
                
                if (responseText == null) {
                    val blockReason = response.candidates?.firstOrNull()?.finishReason 
                        ?: response.promptFeedback?.blockReason
                    
                    val errorMessage = when (blockReason) {
                        "SAFETY" -> "Error: Response blocked by safety filters."
                        "RECITATION" -> "Error: Response blocked due to content recitation."
                        "OTHER" -> "Error: Response blocked for other reasons."
                        else -> "Error: Received an empty response from the API."
                    }
                    _loadingMessage.value = ChatMessage(errorMessage, isUser = false)
                    return@launch
                }
                
                android.util.Log.d("ChatViewModel", "Response text: $responseText")
                android.util.Log.d("ChatViewModel", "Response thought: ${if (responseThought != null) "Present" else "Null"}")
                android.util.Log.d("ChatViewModel", "Grounding metadata: ${if (groundingMetadata != null) "Present" else "Null"}")

                // Save assistant message to DB
                withContext(Dispatchers.IO) {
                    chatDao.insertMessage(
                        MessageEntity(
                            conversationId = conversationId,
                            text = responseText,
                            isUser = false,
                            thought = responseThought,
                            groundingMetadata = groundingMetadata
                        )
                    )
                }
                _loadingMessage.value = null
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val descriptiveMessage = try {
                    val errorResponse = com.google.gson.Gson().fromJson(errorBody, GenerateContentResponse::class.java)
                    errorResponse.error?.message ?: e.message()
                } catch (_: Exception) {
                    e.message()
                }

                val userFriendlyMessage = when (e.code()) {
                    400 -> "Bad Request: $descriptiveMessage"
                    401, 403 -> "Invalid API Key or Permission Denied."
                    429 -> "Quota Exceeded: Please try again later."
                    500, 503 -> "Gemini API is currently unavailable."
                    else -> "API Error (${e.code()}): $descriptiveMessage"
                }
                _loadingMessage.value = ChatMessage(userFriendlyMessage, isUser = false)
            } catch (e: java.io.IOException) {
                _loadingMessage.value = ChatMessage("Network Error: Please check your connection.", isUser = false)
            } catch (e: Exception) {
                _loadingMessage.value = ChatMessage("Error: ${e.message}", isUser = false)
            }
        }
    }
}

class ChatViewModelFactory(
    private val conversationId: Long,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(conversationId, chatDao, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val viewModel: ChatViewModel = viewModel(
        key = conversationId.toString(),
        factory = remember(conversationId) {
            ChatViewModelFactory(
                conversationId = conversationId,
                chatDao = ChatDatabase.getDatabase(context).chatDao(),
                settingsManager = SettingsManager(context)
            )
        }
    )
    
    val messages by viewModel.messages.collectAsState()
    val settingsManager = remember(context) { SettingsManager(context) }
    val userName by settingsManager.userName.collectAsState(initial = "User")
    val loadingMessage = viewModel.loadingMessage
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(messages.size, loadingMessage) {
        if (messages.isNotEmpty() || loadingMessage != null) {
            listState.animateScrollToItem((messages.size + (if (loadingMessage != null) 1 else 0)))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message, userName)
                }
                loadingMessage?.let {
                    item { MessageBubble(it, userName) }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Type a message...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, userName: String) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Text(
            text = if (message.isUser) userName else "Gemini",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    if (message.thought != null) {
                        ThoughtDrawer(thought = message.thought)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (message.isUser) {
                        Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Markdown(content = message.text)
                    }

                    if (message.groundingMetadata != null) {
                        val hasLinks = message.groundingMetadata.groundingChunks?.any { it.web != null } == true
                        if (hasLinks) {
                            Spacer(modifier = Modifier.height(8.dp))
                            GroundingDrawer(metadata = message.groundingMetadata)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThoughtDrawer(thought: String) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { expanded = !expanded }
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Model Thinking",
                style = MaterialTheme.typography.labelMedium,
                fontStyle = FontStyle.Italic
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = thought,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GroundingDrawer(metadata: GroundingMetadata) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
            .clickable { expanded = !expanded }
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Sources & Search",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                metadata.groundingChunks?.forEach { chunk ->
                    chunk.web?.let { web ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { web.uri?.let { uriHandler.openUri(it) } }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = web.title ?: "Source",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                            web.title?.let { title ->
                                Text(
                                    text = " - $title",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                metadata.searchEntryPoint?.html?.let { html ->
                    Text(
                        text = "Google Search results are available for this response.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
