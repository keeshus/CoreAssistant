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

    val userName: StateFlow<String> = settingsManager.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.saveGeminiApiKey(key)
        }
    }

    fun saveUserName(name: String) {
        viewModelScope.launch {
            settingsManager.saveUserName(name)
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

    val apiKeyPref by viewModel.apiKey.collectAsState()
    val userNamePref by viewModel.userName.collectAsState()

    var apiKey by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }

    LaunchedEffect(apiKeyPref) {
        if (apiKey != apiKeyPref) apiKey = apiKeyPref
    }
    LaunchedEffect(userNamePref) {
        if (userName != userNamePref) userName = userNamePref
    }

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
            text = "To get started, please enter your name and Gemini API Key.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = {
                userName = it
            },
            label = { Text("Your Name") },
            placeholder = { Text("User") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
            },
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
                    val finalUserName = if (userName.isBlank()) "User" else userName
                    viewModel.saveUserName(finalUserName)
                    viewModel.saveApiKey(apiKey)
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
