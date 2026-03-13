package nl.codeinfinity.coreassistant

import android.app.Activity
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.codeinfinity.coreassistant.Part as GeminiPart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantScreen(
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val apiService = remember { GeminiApiService.create() }
    
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var loadingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    val triggerMic = remember { mutableStateOf(false) }
    val shouldExitAfterTts = remember { mutableStateOf(false) }

    // Keep screen on
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val tts = rememberTextToSpeech {
        if (shouldExitAfterTts.value) {
            onExit()
        } else {
            triggerMic.value = true
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                if (spokenText.contains("goodbye", ignoreCase = true) || 
                    spokenText.contains("that is all", ignoreCase = true) || 
                    spokenText.contains("exit", ignoreCase = true)) {
                    onExit()
                } else {
                    val userMsg = ChatMessage(spokenText, isUser = true)
                    messages.add(userMsg)
                    loadingMessage = ChatMessage("", isUser = false, isLoading = true)
                    scope.launch {
                        try {
                            val apiKey = settingsManager.geminiApiKey.first()
                            val modelName = settingsManager.geminiModel.first().let {
                                if (it.startsWith("models/")) it.substringAfter("models/") else it
                            }
                            val contents = messages.filter { it.text.isNotBlank() }.map { msg ->
                                Content(role = if (msg.isUser) "user" else "model", parts = listOf(GeminiPart(text = msg.text)))
                            }
                            val request = GenerateContentRequest(
                                contents = contents,
                                systemInstruction = Content(parts = listOf(GeminiPart(text = "You are a concise voice assistant. Give short, direct answers suitable for being read aloud. After answering a user's question, always ask if they have any further questions or if there is anything else you can help with. Only once the user confirms they are done or gives a closing command, end your final message with '[[FINISH]]'.")))
                            )
                            val response = apiService.generateContent(model = modelName, apiKey = apiKey, request = request)
                            loadingMessage = null
                            val aiText = response.text ?: "I'm sorry, I couldn't process that."
                            messages.add(ChatMessage(aiText, isUser = false))
                        } catch (e: Exception) {
                            loadingMessage = ChatMessage("Error: ${e.message}", isUser = false)
                        }
                    }
                }
            } else {
                onExit()
            }
        } else {
            onExit()
        }
    }

    LaunchedEffect(Unit, triggerMic.value) {
        if ((triggerMic.value || messages.isEmpty()) && !shouldExitAfterTts.value) {
            triggerMic.value = false
            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "How can I help?")
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (e: Exception) {
                onExit()
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages.last()
            if (!lastMessage.isUser && !lastMessage.isLoading) {
                if (lastMessage.text.contains("[[FINISH]]", ignoreCase = true)) {
                    shouldExitAfterTts.value = true
                    val cleanText = lastMessage.text.replace("[[FINISH]]", "", ignoreCase = true)
                        .replace(Regex("[*#_>`]"), "")
                        .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
                    tts.speak(cleanText)
                } else {
                    shouldExitAfterTts.value = false
                    val cleanText = lastMessage.text
                        .replace(Regex("[*#_>`]"), "")
                        .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
                    tts.speak(cleanText)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Voice Assistant") })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .fillMaxHeight()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                val listState = rememberLazyListState()
                val userName by settingsManager.userName.collectAsState(initial = "User")

                LaunchedEffect(messages.size, loadingMessage) {
                    listState.animateScrollToItem(0)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    loadingMessage?.let { item { MessageBubble(it, userName, tts) } }
                    items(messages.asReversed()) { message ->
                        val displayMessage = message.copy(text = message.text.replace("[[FINISH]]", "", ignoreCase = true).trim())
                        MessageBubble(displayMessage, userName, tts)
                    }
                }
            }
        }
    }
}
