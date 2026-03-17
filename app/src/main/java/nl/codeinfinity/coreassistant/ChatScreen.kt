package nl.codeinfinity.coreassistant
import androidx.compose.ui.draw.scale

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.OpenableColumns
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
import androidx.core.net.toUri

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

                // Actually Gemini File API (multipart) expects a specific structure if using simple POST.
                // The documentation says:
                // POST https://upload.googleapis.com/v1beta/files?key=$API_KEY
                // X-Goog-Upload-Protocol: multipart
                
                // Retrofit @Body with RequestBody works.
                val remoteUri = try {
                    val response = apiService.uploadFile(apiKey = apiKey, body = multipartBody)
                    response.file.uri
                } finally {
                    tempFile.delete()
                }
                
                // Update attachment in DB if possible (though we don't have messageId here yet easily)
                // For now just return it to be used in current request.
                remoteUri
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Upload failed", e)
                null
            }
        }
    }

    fun sendMessage(userText: String, messageIdToUpdate: Long? = null) {
        if (userText.isBlank() && _selectedAttachments.isEmpty()) return

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
                chatDao.getAllConversationsSync().find { it.id == conversationId }
            }
            conv?.let {
                if (!it.isVoiceAssistant && currentMessages.size <= 1) {
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
                    
                    // Only process attachments for the current message being sent (which is the last one in history)
                    // or if they were already processed and stored with remoteUri.
                    // For simplicity and to avoid re-uploading, we check if attachments exist.
                    msg.attachments?.forEach { attachment ->
                        if (index == historyEntities.lastIndex && msg.isUser) {
                            // This is the message we just sent, we need to prepare the parts (upload if needed)
                            val part = prepareAttachmentPart(attachment, apiKey)
                            if (part != null) {
                                parts.add(part)
                                
                                // If it was a File API upload, we should update the DB with the remoteUri
                                if (part.fileData != null && attachment.remoteUri == null) {
                                    val updatedAttachment = attachment.copy(remoteUri = part.fileData.fileUri)
                                    val updatedAttachments = msg.attachments.map {
                                        if (it.uri == attachment.uri) updatedAttachment else it
                                    }
                                    withContext(Dispatchers.IO) {
                                        // Update the message in DB. We need to find the entity first to get its ID.
                                        val history = chatDao.getMessagesForConversationSync(conversationId)
                                        val entityToUpdate = history.getOrNull(index)
                                        if (entityToUpdate != null) {
                                            chatDao.insertMessage(entityToUpdate.copy(attachments = updatedAttachments))
                                        }
                                    }
                                }
                            } else {
                                // Failed to prepare/upload attachment
                                throw Exception("Failed to process attachment: ${attachment.fileName}")
                            }
                        } else if (attachment.remoteUri != null) {
                            // History message with already uploaded file
                            parts.add(GeminiPart(fileData = FileData(mimeType = attachment.mimeType, fileUri = attachment.remoteUri)))
                        }
                        // Note: Inline data for history is tricky because we don't store the base64 in DB.
                        // Gemini usually expects the full context. If it's inline, we'd have to re-read and re-encode.
                        else if (attachment.fileSize < 20 * 1024 * 1024) {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation
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

    fun getCancelledMessageId(): Long? = lastUserMessageId
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

class TtsManager(context: Context, private val onFinished: () -> Unit = {}) {
    private val sherpaManager = SherpaManager(context)
    private var audioTrack: AudioTrack? = null
    private var isReady = false
    private var initFailed = false

    init {
        // Models are now expected to be in the app's internal storage models/tts/
        val modelsDir = File(context.getExternalFilesDir(null), "models/tts")
        val rootModelsDir = File(context.getExternalFilesDir(null), "models")
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            val settingsManager = SettingsManager(context)
            val selectedVoice = settingsManager.sherpaVoice.first()
            
            // Preload STT (Whisper) model in the background so it's ready for Voice Input
            val sttDir = File(rootModelsDir, "stt")
            val encoderPath = File(sttDir, "small-encoder.int8.onnx").absolutePath
            val decoderPath = File(sttDir, "small-decoder.int8.onnx").absolutePath
            val tokensPath = File(sttDir, "small-tokens.txt").absolutePath
            
            if (File(encoderPath).exists()) {
                val languagePref = settingsManager.sherpaLanguage.first()
                val whisperLang = languagePref.split("-").first().lowercase()
                sherpaManager.initStt(encoderPath, decoderPath, tokensPath, whisperLang)
                
                // Also preload VAD
                val vadModelPath = File(rootModelsDir, "vad/silero_vad.onnx").absolutePath
                if (File(vadModelPath).exists()) {
                    sherpaManager.initVad(vadModelPath)
                }
            }

            if (selectedVoice.isNotEmpty()) {
                val voiceDir = File(modelsDir, selectedVoice)
                // Look for .onnx file in the voice directory
                val modelFile = voiceDir.listFiles { file -> file.extension == "onnx" }?.firstOrNull()
                val tokensFile = File(voiceDir, "tokens.txt")
                val espeakDataDir = File(modelsDir, "espeak-ng-data")
                
                if (modelFile != null && modelFile.exists() && tokensFile.exists()) {
                    sherpaManager.initTts(
                        modelPath = modelFile.absolutePath,
                        tokensPath = tokensFile.absolutePath,
                        dataDir = espeakDataDir.absolutePath
                    )
                    isReady = true
                    pendingText?.let {
                        val textToSpeak = it
                        pendingText = null
                        speak(textToSpeak)
                    }
                } else {
                    initFailed = true
                    onFinished()
                }
            } else {
                initFailed = true
                onFinished()
            }
        }
    }

    private var pendingText: String? = null
    
    private fun cleanTextForSpeech(text: String): String {
        return text
            // Remove markdown bold/italic asterisks and underscores
            .replace(Regex("(?<!\\\\)[*_]"), "")
            // Remove markdown headers
            .replace(Regex("(?m)^#+\\s*"), "")
            // Remove inline code ticks
            .replace("`", "")
            // Replace markdown links with just the text [Text](url) -> Text
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            // Remove URLs that are standing alone
            .replace(Regex("https?://\\S+"), "link")
            // Clean up extra spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun speak(text: String) {
        val cleanText = cleanTextForSpeech(text)
        
        if (initFailed) {
            onFinished()
            return
        }
        
        if (!isReady) {
            pendingText = cleanText
            return
        }

        val audio = sherpaManager.speak(cleanText)
        if (audio == null) {
            onFinished()
            return
        }
        
        stop()

        val sampleRate = audio.sampleRate 
        
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(audio.samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val samples = audio.samples
        val shortArray = ShortArray(samples.size) { (samples[it] * 32767).toInt().toShort() }
        
        audioTrack?.setNotificationMarkerPosition(samples.size)
        audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                onFinished()
            }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        })

        audioTrack?.write(shortArray, 0, samples.size)
        audioTrack?.play()
    }

    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun shutdown() {
        stop()
    }
}

@Composable
fun rememberTextToSpeech(onFinished: () -> Unit = {}): TtsManager {
    val context = LocalContext.current
    val ttsManager = remember { TtsManager(context, onFinished) }
    DisposableEffect(ttsManager) {
        onDispose {
            ttsManager.stop()
            ttsManager.shutdown()
        }
    }
    return ttsManager
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onNavigateBack: () -> Unit,
    database: ChatDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
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
    var inputText by remember { mutableStateOf("") }
    var editingMessageId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    
    val tts = rememberTextToSpeech()
    LocalSoftwareKeyboardController.current

    val sherpaManager = remember { SherpaManager(context) }
    var isListening by remember { mutableStateOf(false) }
    var currentVolume by remember { mutableStateOf(0f) }
    
    var hasMicPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            isListening = true
        } else {
            android.widget.Toast.makeText(context, "Microphone permission required", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isListening) {
        if (isListening) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val sampleRate = 16000
                    val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    val audioRecord = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, sampleRate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                    
                    sherpaManager.resetVad()
                    audioRecord.startRecording()
                    val buffer = ShortArray(bufferSize)
                    
                    while (isListening) {
                        val read = audioRecord.read(buffer, 0, bufferSize)
                        if (read > 0) {
                            var sumSq = 0f
                            val floats = FloatArray(read) {
                                val floatVal = buffer[it] / 32767.0f
                                sumSq += floatVal * floatVal
                                floatVal
                            }
                            
                            val rms = kotlin.math.sqrt(sumSq / read).toFloat()
                            currentVolume = rms
                            
                            val transcription = sherpaManager.processAudioAndTranscribe(floats)
                            if (!transcription.isNullOrEmpty()) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    isListening = false
                                    val messageToSend = if (inputText.isBlank()) transcription else "$inputText $transcription"
                                    inputText = ""
                                    viewModel.sendMessage(messageToSend)
                                }
                            }
                        }
                    }
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        isListening = false
                        android.widget.Toast.makeText(context, "Microphone error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

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
                    item { MessageBubble(it, userName, tts) }
                }
                items(messages.asReversed()) { message ->
                    MessageBubble(message, userName, tts)
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
                IconButton(onClick = {
                    if (isListening) {
                        isListening = false // Allow toggling off
                    } else {
                        if (hasMicPermission) {
                            isListening = true
                        } else {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (isListening) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText, editingMessageId)
                                    inputText = ""
                                    editingMessageId = null
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
                            viewModel.sendMessage(inputText, editingMessageId)
                            inputText = ""
                            editingMessageId = null
                        }
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (loadingMessage != null) {
                    Button(onClick = {
                        editingMessageId = viewModel.getCancelledMessageId()
                        viewModel.cancelMessage()
                        inputText = viewModel.lastUserMessage
                        viewModel.addAttachments(viewModel.lastAttachments)
                    }) {
                        Text("Cancel")
                    }
                } else {
                    Button(onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText, editingMessageId)
                            inputText = ""
                            editingMessageId = null
                        }
                    }) {
                        Text(if (editingMessageId != null) "Resend" else "Send")
                    }
                }
            }
        }
    } // End of Scaffold
    
    // Microphone overlay
    AnimatedMicOverlay(isListening = isListening, currentVolume = currentVolume)
} // End of Box
}
}

@Composable
fun MessageBubble(message: ChatMessage, userName: String, tts: TtsManager) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (message.isUser) userName else "Gemini",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (!message.isLoading && message.text.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { 
                        tts.speak(message.text)
                    },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Audio",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { 
                        clipboardManager.setText(AnnotatedString(message.text))
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            modifier = Modifier
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .widthIn(max = screenWidth * 0.85f)
            ) {
                if (message.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    message.attachments?.let { attachments ->
                        AttachmentPreviewGrid(attachments = attachments)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

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
fun AttachmentChip(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            if (attachment.mimeType.startsWith("image/")) {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AttachmentPreviewGrid(attachments: List<Attachment>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    if (attachment.mimeType.startsWith("image/")) {
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column {
                        Text(
                            text = attachment.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "${attachment.fileSize / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
                
                metadata.searchEntryPoint?.html?.let { _ ->
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
