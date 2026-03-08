package nl.codeinfinity.coreassistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    val apiKey: StateFlow<String> = settingsManager.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val selectedModel: StateFlow<String> = settingsManager.geminiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "models/gemini-1.5-flash")

    val groundingEnabled: StateFlow<Boolean> = settingsManager.googleGroundingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val conversationsLimit: StateFlow<Int> = settingsManager.conversationsLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    private val _availableModels = mutableStateListOf<GeminiModel>()
    val availableModels: List<GeminiModel> = _availableModels

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    fun fetchModels() {
        viewModelScope.launch {
            try {
                val key = apiKey.value
                if (key.isNotBlank()) {
                    val response = apiService.getModels(key)
                    _availableModels.clear()
                    // Filter for generateContent compatible models (simplified check)
                    _availableModels.addAll(response.models.filter { it.name.contains("gemini") })
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.saveGeminiApiKey(key)
        }
    }

    fun saveModel(model: String) {
        viewModelScope.launch {
            settingsManager.saveGeminiModel(model)
        }
    }

    fun saveGrounding(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveGoogleGroundingEnabled(enabled)
        }
    }

    fun saveConversationsLimit(limit: String) {
        viewModelScope.launch {
            settingsManager.saveConversationsLimit(limit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    settingsManager: SettingsManager = SettingsManager(LocalContext.current)
) {
    val viewModel: SettingsViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsManager) as T
        }
    })

    val apiKey by viewModel.apiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val groundingEnabled by viewModel.groundingEnabled.collectAsState()
    val conversationsLimit by viewModel.conversationsLimit.collectAsState()
    val availableModels = viewModel.availableModels

    var expanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

            Button(
                onClick = { viewModel.fetchModels() },
                enabled = apiKey.isNotBlank()
            ) {
                Text("Refresh Models")
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = availableModels.find { it.name == selectedModel }?.displayName ?: selectedModel,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Selected Model") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                viewModel.saveModel(model.name)
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = conversationsLimit.toString(),
                onValueChange = { viewModel.saveConversationsLimit(it) },
                label = { Text("Max Conversations to Keep") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Google Grounding")
                Switch(
                    checked = groundingEnabled,
                    onCheckedChange = { viewModel.saveGrounding(it) }
                )
            }
            
            Text(
                text = "Note: Grounding requires a compatible model and configuration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
