package nl.codeinfinity.coreassistant

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetupViewModel(private val settingsManager: SettingsManager) : ViewModel() {
    val apiKey: StateFlow<String> = settingsManager.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.saveGeminiApiKey(key)
        }
    }
}

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    settingsManager: SettingsManager
) {
    val viewModel: SetupViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SetupViewModel(settingsManager) as T
        }
    })

    val apiKey by viewModel.apiKey.collectAsState()
    var apiKeyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Core Assistant",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "To get started, please enter your Gemini API Key. You can get one from the Google AI Studio.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = { viewModel.saveApiKey(it) },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (apiKeyVisible) "Hide API Key" else "Show API Key"
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (apiKey.isNotBlank()) {
                    onSetupComplete()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = apiKey.isNotBlank()
        ) {
            Text("Finish Setup")
        }
    }
}
