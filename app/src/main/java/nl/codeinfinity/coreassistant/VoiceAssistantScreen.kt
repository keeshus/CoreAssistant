package nl.codeinfinity.coreassistant

import android.app.Activity
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.view.WindowManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import nl.codeinfinity.coreassistant.Part as GeminiPart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantScreen(
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val sherpaManager = remember { SherpaManager(context) }
    val apiService = remember { GeminiApiService.create() }
    
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var loadingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    var isListening by remember { mutableStateOf(false) }
    val shouldExitAfterTts = remember { mutableStateOf(false) }
    val initialGreetingDone = remember { mutableStateOf(false) }

    // Initial Greeting
    LaunchedEffect(Unit) {
        val userName = settingsManager.userName.first()
        val languagePref = settingsManager.sherpaLanguage.first()
        // Extract language code, e.g., "nl-NL" -> "nl", "en-US" -> "en"
        val whisperLang = languagePref.split("-").first().lowercase()
        val greeting = "Hallo $userName, waar kan ik je mee helpen?"
        messages.add(ChatMessage(greeting, isUser = false))
        initialGreetingDone.value = true
        
        // Initialize Sherpa if models exist
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        
        // STT paths (Whisper)
        val sttDir = File(modelsDir, "stt")
        val encoderPath = File(sttDir, "small-encoder.int8.onnx").absolutePath
        val decoderPath = File(sttDir, "small-decoder.int8.onnx").absolutePath
        val tokensPath = File(sttDir, "small-tokens.txt").absolutePath
        
        // VAD path
        val vadPath = File(modelsDir, "vad/silero_vad.onnx").absolutePath
        
        android.util.Log.d("VoiceAssistantScreen", "modelsDir exists. encoderPath exists: ${File(encoderPath).exists()}, vadPath exists: ${File(vadPath).exists()}")
        if (File(encoderPath).exists()) {
            withContext(Dispatchers.IO) {
                sherpaManager.initStt(encoderPath, decoderPath, tokensPath, whisperLang)
                if (File(vadPath).exists()) {
                    sherpaManager.initVad(vadPath)
                } else {
                    android.util.Log.e("VoiceAssistantScreen", "VAD model not found at $vadPath. Speech recognition will fail because it depends on VAD.")
                }
            }
        } else {
            android.util.Log.e("VoiceAssistantScreen", "Encoder model not found at $encoderPath")
        }
    }
    
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
            isListening = true
        }
    }

    // Sherpa-ONNX Listening Loop
    LaunchedEffect(isListening) {
        if (isListening) {
            withContext(Dispatchers.IO) {
                val sampleRate = 16000
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                
                sherpaManager.resetVad()
                audioRecord.startRecording()
                val buffer = ShortArray(bufferSize)
                
                while (isListening) {
                    val read = audioRecord.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        val floats = FloatArray(read) { buffer[it] / 32767.0f }
                        
                        val transcription = sherpaManager.processAudioAndTranscribe(floats)
                        if (!transcription.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) {
                                isListening = false
                                handleSpokenText(transcription, settingsManager, apiService, messages, scope, tts, shouldExitAfterTts, onExit, { loadingMessage = it })
                            }
                        }
                    }
                }
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }

    // Manual Mic Trigger for first time
    LaunchedEffect(initialGreetingDone.value) {
        if (initialGreetingDone.value && messages.size == 1) {
            val greeting = messages.first().text
            tts.speak(greeting)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val listState = rememberLazyListState()
        
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            reverseLayout = false
        ) {
            items(messages) { msg ->
                MessageBubble(msg, "User", tts)
            }
            loadingMessage?.let {
                item { MessageBubble(it, "User", tts) }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (isListening) "Listening..." else "Assistant is ready",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onExit) {
            Text("Close Assistant")
        }
    }
}

private suspend fun handleSpokenText(
    spokenText: String,
    settingsManager: SettingsManager,
    apiService: GeminiApiService,
    messages: MutableList<ChatMessage>,
    scope: kotlinx.coroutines.CoroutineScope,
    tts: TtsManager,
    shouldExitAfterTts: MutableState<Boolean>,
    onExit: () -> Unit,
    setLoading: (ChatMessage?) -> Unit
) {
    if (spokenText.contains("goodbye", ignoreCase = true) || 
        spokenText.contains("that is all", ignoreCase = true) || 
        spokenText.contains("exit", ignoreCase = true)) {
        onExit()
    } else {
        val userMsg = ChatMessage(spokenText, isUser = true)
        messages.add(userMsg)
        setLoading(ChatMessage("", isUser = false, isLoading = true))
        try {
            val apiKey = settingsManager.geminiApiKey.first()
            val modelName = settingsManager.geminiModel.first().let {
                if (it.startsWith("models/")) it.substringAfter("models/") else it
            }
            val contents = messages.filter { !it.isLoading }.map { msg ->
                Content(role = if (msg.isUser) "user" else "model", parts = listOf(GeminiPart(text = msg.text)))
            }
            val request = GenerateContentRequest(
                contents = contents,
                systemInstruction = Content(parts = listOf(GeminiPart(text = "You are a concise voice assistant. Give short, direct answers suitable for being read aloud. After answering a user's question, always ask if they have any further questions or if there is anything else you can help with. Only once the user confirms they are done or gives a closing command, end your final message with '[[FINISH]]'.")))
            )
            val response = apiService.generateContent(model = modelName, apiKey = apiKey, request = request)
            setLoading(null)
            val aiText = response.text ?: "I'm sorry, I couldn't process that."
            messages.add(ChatMessage(aiText, isUser = false))
            
            if (aiText.contains("[[FINISH]]")) {
                shouldExitAfterTts.value = true
            }
            tts.speak(aiText.replace("[[FINISH]]", ""))
        } catch (e: Exception) {
            setLoading(ChatMessage("Error: ${e.message}", isUser = false))
        }
    }
}
