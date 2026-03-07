package nl.codeinfinity.coreassistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatViewModel(private val settingsManager: SettingsManager) : ViewModel() {
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        _messages.add(ChatMessage(userText, isUser = true))
        
        viewModelScope.launch {
            try {
                val apiKey = settingsManager.geminiApiKey.first()
                val modelName = settingsManager.geminiModel.first()
                val isGroundingEnabled = settingsManager.googleGroundingEnabled.first()

                if (apiKey.isBlank()) {
                    _messages.add(ChatMessage("Error: API Key not set in settings", isUser = false))
                    return@launch
                }

                // Note: v0.9.0 does not support Google Grounding via the Tool class in the same way as later versions.
                // We will leave the structure but remove the unresolved reference.
                val tools: List<Tool>? = null

                val generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey,
                    tools = tools
                )

                val response = generativeModel.generateContent(userText)
                _messages.add(ChatMessage(response.text ?: "No response", isUser = false))
            } catch (e: Exception) {
                _messages.add(ChatMessage("Error: ${e.message}", isUser = false))
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
                placeholder = { Text("Ask Gemini...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                viewModel.sendMessage(inputText)
                inputText = ""
            }) {
                Text("Send")
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
