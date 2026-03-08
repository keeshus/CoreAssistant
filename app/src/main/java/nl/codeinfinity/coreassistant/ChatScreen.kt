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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatViewModel(private val settingsManager: SettingsManager) : ViewModel() {
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        _messages.add(ChatMessage(userText, isUser = true))
        
        viewModelScope.launch {
            try {
                val modelName = settingsManager.geminiModel.first()
                val isGroundingEnabled = settingsManager.googleGroundingEnabled.first()

                val tools = if (isGroundingEnabled) {
                    listOf(Tool.googleSearch())
                } else {
                    null
                }

                val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = modelName,
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
                placeholder = { Text("Type a message...") }
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
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
