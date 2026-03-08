package nl.codeinfinity.coreassistant

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mikepenz.markdown.m3.Markdown
import retrofit2.HttpException
import java.io.IOException

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val thought: String? = null,
    val groundingMetadata: GroundingMetadata? = null,
    val isLoading: Boolean = false
)

class ChatViewModel(private val settingsManager: SettingsManager) : ViewModel() {
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    private val apiService = GeminiApiService.create()
    private val gson = Gson()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            settingsManager.getHistory().first()?.let { historyJson ->
                val type = object : TypeToken<List<ChatMessage>>() {}.type
                val history: List<ChatMessage> = gson.fromJson(historyJson, type)
                _messages.addAll(history)
            }
        }
    }

    private fun saveHistory() {
        viewModelScope.launch {
            val historyToSave = _messages.takeLast(10) // 5 conversations = 10 messages (user + assistant)
            val historyJson = gson.toJson(historyToSave)
            settingsManager.saveHistory(historyJson)
        }
    }

    fun sendMessage(context: android.content.Context, userText: String) {
        if (userText.isBlank()) return

        val userMessage = ChatMessage(userText, isUser = true)
        _messages.add(userMessage)
        
        // Add a loading message
        val loadingMessage = ChatMessage("", isUser = false, isLoading = true)
        _messages.add(loadingMessage)
        val loadingIndex = _messages.indexOf(loadingMessage)

        viewModelScope.launch {
            try {
                val apiKey = settingsManager.geminiApiKey.first()
                if (apiKey.isBlank()) {
                    _messages[loadingIndex] = ChatMessage("Error: API Key is not set. Please go to settings.", isUser = false)
                    return@launch
                }

                val rawModelName = settingsManager.geminiModel.first()
                val modelName = if (rawModelName.startsWith("models/")) {
                    rawModelName.substringAfter("models/")
                } else {
                    rawModelName
                }
                val isGroundingEnabled = settingsManager.googleGroundingEnabled.first()

                val tools = if (isGroundingEnabled) {
                    listOf(Tool(googleSearch = GoogleSearch()))
                } else {
                    null
                }

                // Create history for the request
                val historyContext = _messages.filter { !it.isLoading }.takeLast(11).dropLast(1) // Previous 5 conversations (10 messages) + current user message is already added
                
                val contents = historyContext.map { msg ->
                    Content(role = if (msg.isUser) "user" else "model", parts = listOf(Part(text = msg.text)))
                } + Content(role = "user", parts = listOf(Part(text = userText)))

                val request = GenerateContentRequest(
                    contents = contents,
                    tools = tools
                )

                val response = apiService.generateContent(
                    model = modelName,
                    apiKey = apiKey,
                    request = request
                )

                val assistantMessage = ChatMessage(
                    text = response.text ?: "No response",
                    isUser = false,
                    thought = response.thought,
                    groundingMetadata = response.groundingMetadata
                )
                _messages[loadingIndex] = assistantMessage
                saveHistory()
            } catch (e: IOException) {
                android.util.Log.e("ChatViewModel", "Network Error: ${e.message}", e)
                _messages[loadingIndex] = ChatMessage("Network Error: ${e.message ?: "Please check your internet connection."}", isUser = false)
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                android.util.Log.e("ChatViewModel", "HTTP Error ${e.code()}: $errorBody", e)
                val errorMessage = when (e.code()) {
                    401 -> "Error: Invalid API Key."
                    429 -> "Error: Too many requests. Please try again later."
                    else -> "API Error (${e.code()}): $errorBody"
                }
                _messages[loadingIndex] = ChatMessage(errorMessage, isUser = false)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Unexpected Error", e)
                _messages[loadingIndex] = ChatMessage("Unexpected Error: ${e.message}", isUser = false)
            }
        }
    }
}

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit,
    settingsManager: SettingsManager = SettingsManager(LocalContext.current)
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(settingsManager) as T
        }
    })
    var inputText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onNavigateToSettings) {
                Text("Settings")
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.messages) { message ->
                MessageBubble(message)
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                viewModel.sendMessage(context, inputText)
                inputText = ""
            }) {
                Text("Send")
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            modifier = Modifier.padding(vertical = 4.dp)
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
                    
                    Markdown(
                        content = message.text
                    )

                    if (message.groundingMetadata != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        GroundingDrawer(metadata = message.groundingMetadata)
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
                        Text(
                            text = "• ${web.title ?: "Source"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Text(
                            text = web.uri ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
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
