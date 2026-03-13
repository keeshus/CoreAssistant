package nl.codeinfinity.coreassistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    val apiKey: StateFlow<String> = settingsManager.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val selectedModel: StateFlow<String> = settingsManager.geminiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "models/gemini-3.0-flash-preview")

    val groundingEnabled: StateFlow<Boolean> = settingsManager.googleGroundingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val conversationsLimit: StateFlow<Int> = settingsManager.conversationsLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val thinkingLevel: StateFlow<String> = settingsManager.geminiThinkingLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OFF")

    val screenshotProtection: StateFlow<Boolean> = settingsManager.screenshotProtection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val clearHistoryOnClose: StateFlow<Boolean> = settingsManager.clearHistoryOnClose
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _availableModels = mutableStateListOf<GeminiModel>()
    val availableModels: List<GeminiModel> = _availableModels

    private val apiService: GeminiApiService by lazy {
        GeminiApiService.create()
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
            } catch (_: Exception) {
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

    fun saveThinkingLevel(level: String) {
        viewModelScope.launch {
            settingsManager.saveGeminiThinkingLevel(level)
        }
    }

    fun saveScreenshotProtection(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveScreenshotProtection(enabled)
        }
    }

    fun saveClearHistoryOnClose(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveClearHistoryOnClose(enabled)
        }
    }

    fun saveUserName(name: String) {
        viewModelScope.launch {
            settingsManager.saveUserName(name)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    settingsManager: SettingsManager = SettingsManager(LocalContext.current)
) {
    val viewModel: SettingsViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsManager) as T
        }
    })

    val apiKeyPref by viewModel.apiKey.collectAsState()
    val userNamePref by settingsManager.userName.collectAsState(initial = "User")
    val selectedModel by viewModel.selectedModel.collectAsState()
    val groundingEnabled by viewModel.groundingEnabled.collectAsState()
    val conversationsLimitPref by viewModel.conversationsLimit.collectAsState()
    val thinkingLevel by viewModel.thinkingLevel.collectAsState()
    val screenshotProtection by viewModel.screenshotProtection.collectAsState()
    val clearHistoryOnClose by viewModel.clearHistoryOnClose.collectAsState()
    val availableModels = viewModel.availableModels

    var apiKey by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var conversationsLimit by remember { mutableStateOf("") }

    LaunchedEffect(apiKeyPref) {
        if (apiKey != apiKeyPref) apiKey = apiKeyPref
    }
    LaunchedEffect(userNamePref) {
        if (userName != userNamePref) userName = userNamePref
    }
    LaunchedEffect(conversationsLimitPref) {
        if (conversationsLimit != conversationsLimitPref.toString()) {
            conversationsLimit = conversationsLimitPref.toString()
        }
    }

    var expanded by remember { mutableStateOf(false) }
    var thinkingExpanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = userName,
                onValueChange = {
                    userName = it
                    viewModel.saveUserName(it)
                },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    viewModel.saveApiKey(it)
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

            Button(
                onClick = { viewModel.fetchModels() },
                enabled = apiKey.isNotBlank()
            ) {
                Text("Refresh Models")
            }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = availableModels.find { it.name == selectedModel }?.displayName
                        ?: selectedModel.removePrefix("models/").split("-").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
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

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = thinkingLevel,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Thinking Level") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { thinkingExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                )
                DropdownMenu(
                    expanded = thinkingExpanded,
                    onDismissRequest = { thinkingExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("OFF", "LOW", "MEDIUM", "HIGH").forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level) },
                            onClick = {
                                viewModel.saveThinkingLevel(level)
                                thinkingExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = conversationsLimit,
                onValueChange = { 
                    conversationsLimit = it
                    viewModel.saveConversationsLimit(it) 
                },
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

            HorizontalDivider()
            Text("System Integration", style = MaterialTheme.typography.titleMedium)

            val context = LocalContext.current
            Button(
                onClick = {
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))
                    } catch (e: Exception) {
                        try {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        } catch (e2: Exception) {
                            android.widget.Toast.makeText(context, "Cannot open settings", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set as Default Voice Assistant")
            }

            HorizontalDivider()
            Text("Privacy Settings", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Screenshot Protection")
                    Text("Block screenshots and hide app in recent apps", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = screenshotProtection,
                    onCheckedChange = { viewModel.saveScreenshotProtection(it) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clear History on Close")
                    Text("Automatically delete all chats when app is closed", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = clearHistoryOnClose,
                    onCheckedChange = { viewModel.saveClearHistoryOnClose(it) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNavigateToLicenses,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Licenses")
            }
        }
    }
}
