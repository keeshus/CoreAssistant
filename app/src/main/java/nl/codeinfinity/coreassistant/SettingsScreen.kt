package nl.codeinfinity.coreassistant

import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(private val settingsManager: SettingsManager, private val context: Context) : ViewModel() {

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

    val sherpaLanguage: StateFlow<String> = settingsManager.sherpaLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en-US")

    val sherpaVoice: StateFlow<String> = settingsManager.sherpaVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _availableTtsModels = MutableStateFlow<List<String>>(emptyList())
    val availableTtsModels = _availableTtsModels.asStateFlow()

    init {
        loadAvailableTtsModels()
    }

    private fun loadAvailableTtsModels() {
        viewModelScope.launch {
            val modelsDir = File(context.getExternalFilesDir(null), "models/tts")
            val models = modelsDir.listFiles { file -> file.isDirectory && file.name != "espeak-ng-data" }
                ?.map { it.name } ?: emptyList()
            _availableTtsModels.value = models
        }
    }

    fun saveSherpaSettings(language: String, voice: String) {
        viewModelScope.launch {
            settingsManager.saveSherpaLanguage(language)
            settingsManager.saveSherpaVoice(voice)
        }
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
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsManager, context) as T
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
    val sherpaLanguagePref by viewModel.sherpaLanguage.collectAsState()
    val sherpaVoicePref by viewModel.sherpaVoice.collectAsState()
    val availableTtsModels by viewModel.availableTtsModels.collectAsState()
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
    var langMenuExpanded by remember { mutableStateOf(false) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }

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
            Text("Voice Assistant (Sherpa-ONNX)", style = MaterialTheme.typography.titleMedium)

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = sherpaLanguagePref,
                    onValueChange = {},
                    label = { Text("Language") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { langMenuExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Language")
                        }
                    }
                )
                DropdownMenu(
                    expanded = langMenuExpanded,
                    onDismissRequest = { langMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("en-US", "nl-NL", "de-DE", "fr-FR", "es-ES", "it-IT").forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                viewModel.saveSherpaSettings(lang, sherpaVoicePref)
                                langMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = if (sherpaVoicePref.isEmpty()) "Select Model" else sherpaVoicePref,
                    onValueChange = {},
                    label = { Text("Voice Model") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { voiceMenuExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Voice Model")
                        }
                    }
                )
                DropdownMenu(
                    expanded = voiceMenuExpanded,
                    onDismissRequest = { voiceMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableTtsModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                viewModel.saveSherpaSettings(sherpaLanguagePref, model)
                                voiceMenuExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()
            Text("System Integration", style = MaterialTheme.typography.titleMedium)

            val systemContext = LocalContext.current
            Button(
                onClick = {
                    try {
                        systemContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))
                    } catch (e: Exception) {
                        try {
                            systemContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        } catch (e2: Exception) {
                            android.widget.Toast.makeText(systemContext, "Cannot open settings", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set as Default Voice Assistant")
            }
            
            Button(
                onClick = {
                    try {
                        // STT engines are typically configured in Input Method Settings on modern Android devices
                        val intent = android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
                        systemContext.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                            systemContext.startActivity(intent)
                        } catch (e2: Exception) {
                            android.widget.Toast.makeText(systemContext, "Cannot open settings", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set as Default Speech-to-Text Engine")
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
