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
import retrofit2.HttpException
import java.io.IOException

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatViewModel(private val settingsManager: SettingsManager) : ViewModel() {
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    private val apiService = GeminiApiService.create()

    fun sendMessage(context: android.content.Context, userText: String) {
        if (userText.isBlank()) return

        _messages.add(ChatMessage(userText, isUser = true))
        
        viewModelScope.launch {
            try {
                val apiKey = settingsManager.geminiApiKey.first()
                if (apiKey.isBlank()) {
                    _messages.add(ChatMessage("Error: API Key is not set. Please go to settings.", isUser = false))
                    return@launch
                }

                val modelName = settingsManager.geminiModel.first()
                val isGroundingEnabled = settingsManager.googleGroundingEnabled.first()

                val tools = if (isGroundingEnabled) {
                    listOf(Tool(googleSearchRetrieval = GoogleSearchRetrieval()))
                } else {
                    null
                }

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = userText)))),
                    tools = tools
                )

                val response = apiService.generateContent(
                    model = modelName,
                    apiKey = apiKey,
                    request = request
                )

                _messages.add(ChatMessage(response.text ?: "No response", isUser = false))
            } catch (e: IOException) {
                android.util.Log.e("ChatViewModel", "Network Error", e)
                _messages.add(ChatMessage("Network Error: Please check your internet connection.", isUser = false))
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                android.util.Log.e("ChatViewModel", "HTTP Error ${e.code()}: $errorBody", e)
                val errorMessage = when (e.code()) {
                    401 -> "Error: Invalid API Key."
                    429 -> "Error: Too many requests. Please try again later."
                    else -> "API Error (${e.code()}): $errorBody"
                }
                _messages.add(ChatMessage(errorMessage, isUser = false))
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Unexpected Error", e)
                _messages.add(ChatMessage("Unexpected Error: ${e.message}", isUser = false))
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
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
