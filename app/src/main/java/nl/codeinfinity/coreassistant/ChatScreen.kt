package nl.codeinfinity.coreassistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import nl.codeinfinity.coreassistant.Part as GeminiPart

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val thought: String? = null,
    val groundingMetadata: GroundingMetadata? = null,
    val attachments: List<Attachment>? = null,
    val isLoading: Boolean = false
)

class ChatViewModel(
    private val conversationId: Long,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager,
    private val context: Context,
    private val systemInstruction: String? = null
) : ViewModel() {
    val messages: StateFlow<List<ChatMessage>> = chatDao.getMessagesForConversation(conversationId)
        .map { entities ->
            entities.map { entity ->
                ChatMessage(
                    text = entity.text,
                    isUser = entity.isUser,
                    thought = entity.thought,
                    groundingMetadata = entity.groundingMetadata,
                    attachments = entity.attachments
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _loadingMessage = mutableStateOf<ChatMessage?>(null)
    val loadingMessage: ChatMessage? get() = _loadingMessage.value

    private val apiService = GeminiApiService.create()

    private var currentJob: Job? = null
    var lastUserMessage: String = ""
        private set
    var lastAttachments: List<Attachment> = emptyList()
        private set
    private var lastUserMessageId: Long? = null

    private val _selectedAttachments = mutableStateListOf<Attachment>()
    val selectedAttachments: List<Attachment> get() = _selectedAttachments

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    init {
        viewModelScope.launch {
            val conv = withContext(Dispatchers.IO) {
                chatDao.getConversationById(conversationId)
            }
            conv?.draftText?.let {
                _draftText.value = it
            }
        }
    }

    fun updateDraft(text: String) {
        _draftText.value = text
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatDao.updateDraft(conversationId, text, System.currentTimeMillis())
            }
        }
    }

    fun addAttachments(attachments: List<Attachment>) {
        _selectedAttachments.addAll(attachments)
    }

    fun removeAttachment(attachment: Attachment) {
        _selectedAttachments.remove(attachment)
    }

    private suspend fun prepareAttachmentPart(attachment: Attachment, apiKey: String): GeminiPart? {
        val uri = attachment.uri.toUri()
        val fileSize = attachment.fileSize
        val mimeType = attachment.mimeType

        return if (fileSize < 20 * 1024 * 1024) {
            // Inline data
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            GeminiPart(inlineData = InlineData(mimeType = mimeType, data = base64Data))
        } else {
            // File API
            val remoteUri = attachment.remoteUri ?: uploadToFileApi(attachment, apiKey) ?: return null
            GeminiPart(fileData = FileData(mimeType = mimeType, fileUri = remoteUri))
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private suspend fun uploadToFileApi(attachment: Attachment, apiKey: String): String? {
        if (!isNetworkAvailable()) {
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                val uri = attachment.uri.toUri()
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                
                // Create a temporary file to use with asRequestBody
                val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_${attachment.fileName}")
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }

                val mediaType = attachment.mimeType.toMediaTypeOrNull()
                val requestFile = tempFile.asRequestBody(mediaType)
                
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(
                        MultipartBody.Part.createFormData(
                            "file",
                            attachment.fileName,
                            requestFile
                        )
                    )
                    .build()

                val remoteUri = try {
                    val response = apiService.uploadFile(apiKey = apiKey, body = multipartBody)
                    response.file.uri
                } finally {
                    tempFile.delete()
                }
                
                remoteUri
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Upload failed", e)
                null
            }
        }
    }

    fun sendMessage(userText: String, messageIdToUpdate: Long? = null) {
        if (userText.isBlank() && _selectedAttachments.isEmpty()) return

        // Clear draft when sending
        updateDraft("")

        val attachmentsToSend = _selectedAttachments.toList()
        lastUserMessage = userText
        lastAttachments = attachmentsToSend
        _selectedAttachments.clear()

        currentJob = viewModelScope.launch {
            // Save or update user message in DB
            val messageId = withContext(Dispatchers.IO) {
                chatDao.insertMessage(
                    MessageEntity(
                        id = messageIdToUpdate ?: 0,
                        conversationId = conversationId,
                        text = userText,
                        isUser = true,
                        attachments = if (attachmentsToSend.isNotEmpty()) attachmentsToSend else null
                    )
                )
            }
            lastUserMessageId = messageId
            
            // Update conversation timestamp and title if it was "New Chat"
            val currentMessages = messages.value
            val conv = withContext(Dispatchers.IO) {
                chatDao.getConversationById(conversationId)
            }
            conv?.let {
                if (currentMessages.size <= 1) {
                    val title = if (userText.length > 30) userText.take(27) + "..." else userText
                    withContext(Dispatchers.IO) {
                        chatDao.updateConversation(it.copy(title = title, lastModified = System.currentTimeMillis()))
                    }
                } else {
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
                
                // Convert history to Content objects, handling attachments for the last message
                val contents = historyEntities.mapIndexed { index, msg ->
                    val parts = mutableListOf<GeminiPart>()
                    
                    if (msg.text.isNotBlank()) {
                        parts.add(GeminiPart(text = msg.text))
                    }
                    
                    msg.attachments?.forEach { attachment ->
                        if (index == historyEntities.lastIndex && msg.isUser) {
                            val part = prepareAttachmentPart(attachment, apiKey)
                            if (part != null) {
                                parts.add(part)
                                if (part.fileData != null && attachment.remoteUri == null) {
                                    val updatedAttachment = attachment.copy(remoteUri = part.fileData.fileUri)
                                    val updatedAttachments = msg.attachments.map {
                                        if (it.uri == attachment.uri) updatedAttachment else it
                                    }
                                    withContext(Dispatchers.IO) {
                                        val history = chatDao.getMessagesForConversationSync(conversationId)
                                        val entityToUpdate = history.getOrNull(index)
                                        if (entityToUpdate != null) {
                                            chatDao.insertMessage(entityToUpdate.copy(attachments = updatedAttachments))
                                        }
                                    }
                                }
                            } else {
                                throw Exception("Failed to process attachment: ${attachment.fileName}")
                            }
                        } else if (attachment.remoteUri != null) {
                            parts.add(GeminiPart(fileData = FileData(mimeType = attachment.mimeType, fileUri = attachment.remoteUri)))
                        } else if (attachment.fileSize < 20 * 1024 * 1024) {
                            val part = prepareAttachmentPart(attachment, apiKey)
                            if (part != null) parts.add(part)
                        }
                    }

                    Content(role = if (msg.isUser) "user" else "model", parts = parts.toList())
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
                
                val request = GenerateContentRequest(
                    contents = contents,
                    tools = tools,
                    generationConfig = configuration,
                    systemInstruction = systemInstruction?.let { Content(parts = listOf(GeminiPart(text = it))) }
                )
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
            } catch (_: kotlinx.coroutines.CancellationException) {
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
            } catch (_: java.io.IOException) {
                _loadingMessage.value = ChatMessage("Network Error: Please check your connection.", isUser = false)
            } catch (e: Exception) {
                _loadingMessage.value = ChatMessage("Error: ${e.message}", isUser = false)
            } finally {
                currentJob = null
            }
        }
    }

    fun cancelMessage() {
        currentJob?.cancel()
        currentJob = null
        _loadingMessage.value = null
    }

}

class ChatViewModelFactory(
    private val conversationId: Long,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager,
    private val context: Context,
    private val systemInstruction: String? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(conversationId, chatDao, settingsManager, context, systemInstruction) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onNavigateBack: () -> Unit,
    database: ChatDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        key = conversationId.toString(),
        factory = remember(conversationId) {
            ChatViewModelFactory(
                conversationId = conversationId,
                chatDao = database.chatDao(),
                settingsManager = SettingsManager(context),
                context = context
            )
        }
    )
    
    val messages by viewModel.messages.collectAsState()
    val settingsManager = remember(context) { SettingsManager(context) }
    val userName by settingsManager.userName.collectAsState(initial = "User")
    val loadingMessage = viewModel.loadingMessage
    
    val draftText by viewModel.draftText.collectAsState()
    var inputText by remember { mutableStateOf("") }
    
    // Sync local inputText with draft from ViewModel
    LaunchedEffect(draftText) {
        if (inputText != draftText) {
            inputText = draftText
        }
    }

    var editingMessageId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    
    val sheetState = rememberModalBottomSheetState()
    var showGroundingSheet by remember { mutableStateOf(false) }
    var selectedGroundingMetadata by remember { mutableStateOf<GroundingMetadata?>(null) }
    
    var showThoughtSheet by remember { mutableStateOf(false) }
    var selectedThought by remember { mutableStateOf<String?>(null) }

    LocalSoftwareKeyboardController.current

    LaunchedEffect(messages.size, loadingMessage) {
        if (listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val attachments = uris.map { uri ->
            var fileName = "unknown"
            var fileSize = 0L
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
            Attachment(
                uri = uri.toString(),
                mimeType = mimeType,
                fileName = fileName,
                fileSize = fileSize
            )
        }
        viewModel.addAttachments(attachments)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .fillMaxHeight()
                    .imePadding()
                    .padding(16.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                loadingMessage?.let {
                    item { MessageBubble(it, userName) }
                }
                items(messages.asReversed()) { message ->
                    MessageBubble(
                        message = message,
                        userName = userName,
                        onShowGrounding = {
                            selectedGroundingMetadata = it
                            showGroundingSheet = true
                        },
                        onShowThought = {
                            selectedThought = it
                            showThoughtSheet = true
                        }
                    )
                }
            }
            
            if (viewModel.selectedAttachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.selectedAttachments) { attachment ->
                        AttachmentChip(
                            attachment = attachment,
                            onRemove = { viewModel.removeAttachment(attachment) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(Icons.Default.Add, contentDescription = "Attach Files")
                }
                
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { 
                        inputText = it
                        viewModel.updateDraft(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter && !keyEvent.isShiftPressed) {
                                viewModel.sendMessage(inputText, editingMessageId)
                                inputText = ""
                                editingMessageId = null
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Type a message...") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        viewModel.sendMessage(inputText, editingMessageId)
                        inputText = ""
                        editingMessageId = null
                    }),
                    trailingIcon = {
                        if (loadingMessage?.isLoading == true) {
                            IconButton(onClick = { viewModel.cancelMessage() }) {
                                Icon(Icons.Default.Stop, contentDescription = "Cancel")
                            }
                        } else if (inputText.isNotBlank() || viewModel.selectedAttachments.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.sendMessage(inputText, editingMessageId)
                                inputText = ""
                                editingMessageId = null
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    }
                )
            }
        }
    }
    }
    }

    if (showGroundingSheet && selectedGroundingMetadata != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showGroundingSheet = false
                selectedGroundingMetadata = null
            },
            sheetState = sheetState
        ) {
            GroundingContent(selectedGroundingMetadata!!)
        }
    }

    if (showThoughtSheet && selectedThought != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showThoughtSheet = false
                selectedThought = null
            },
            sheetState = sheetState
        ) {
            ThoughtContent(selectedThought!!)
        }
    }
}

@Composable
fun ThoughtContent(thought: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Psychology, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Thought Process",
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Markdown(
            content = thought,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun GroundingContent(metadata: GroundingMetadata) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Grounding Sources",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val entryPointHtml = metadata.searchEntryPoint?.html
        if (entryPointHtml != null) {
            Surface(
                onClick = {
                    val match = "href=\"(.*?)\"".toRegex().find(entryPointHtml)
                    match?.groupValues?.get(1)?.let { url ->
                        uriHandler.openUri(url)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search on Google",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        metadata.groundingChunks?.forEach { chunk ->
            chunk.web?.let { web ->
                if (web.uri != null && web.title != null) {
                    Surface(
                        onClick = { uriHandler.openUri(web.uri) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = web.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = web.uri,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    userName: String,
    onShowGrounding: (GroundingMetadata) -> Unit = {},
    onShowThought: (String) -> Unit = {}
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (message.isUser) userName else "Gemini",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                if (message.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                } else {
                    if (!message.thought.isNullOrBlank()) {
                        Text(
                            text = "View Thought Process",
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .clickable { onShowThought(message.thought) }
                        )
                    }

                    Markdown(content = message.text)
                    
                    message.attachments?.let { attachments ->
                        attachments.forEach { attachment ->
                            AttachmentPreview(attachment)
                        }
                    }

                    if (message.groundingMetadata != null) {
                        Text(
                            text = "View Sources",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { onShowGrounding(message.groundingMetadata) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentPreview(attachment: Attachment) {
    val isImage = attachment.mimeType.startsWith("image/")
    
    if (isImage) {
        AsyncImage(
            model = attachment.uri,
            contentDescription = attachment.fileName,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 100.dp)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
            }
        }
    }
}
