package nl.codeinfinity.coreassistant

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
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

class SetupViewModel(private val settingsManager: SettingsManager, private val context: Context) : ViewModel() {
    val apiKey: StateFlow<String> = settingsManager.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userName: StateFlow<String> = settingsManager.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val sherpaLanguage: StateFlow<String> = settingsManager.sherpaLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en-US")

    val sherpaVoice: StateFlow<String> = settingsManager.sherpaVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _availableTtsModels = MutableStateFlow<List<String>>(emptyList())
    val availableTtsModels = _availableTtsModels.asStateFlow()

    init {
        loadAvailableTtsModels()
    }

    fun loadAvailableTtsModels() {
        viewModelScope.launch {
            val modelsDir = File(context.getExternalFilesDir(null), "downloaded_models/models/tts")
            val models = modelsDir.listFiles { file -> file.isDirectory && file.name != "espeak-ng-data" }
                ?.map { it.name } ?: emptyList()
            _availableTtsModels.value = models
        }
    }

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

    fun saveSherpaSettings(language: String, voice: String) {
        viewModelScope.launch {
            settingsManager.saveSherpaLanguage(language)
            settingsManager.saveSherpaVoice(voice)
        }
    }
}

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val viewModel: SetupViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SetupViewModel(settingsManager, context) as T
        }
    })

    val apiKeyPref by viewModel.apiKey.collectAsState()
    val userNamePref by viewModel.userName.collectAsState()
    val sherpaLanguagePref by viewModel.sherpaLanguage.collectAsState()
    val sherpaVoicePref by viewModel.sherpaVoice.collectAsState()
    val availableTtsModels by viewModel.availableTtsModels.collectAsState()

    var apiKey by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("en-US") }
    var selectedVoice by remember { mutableStateOf("") }

    LaunchedEffect(apiKeyPref) {
        if (apiKey != apiKeyPref) apiKey = apiKeyPref
    }
    LaunchedEffect(userNamePref) {
        if (userNamePref != "User" && userName != userNamePref) {
            userName = userNamePref
        }
    }
    LaunchedEffect(sherpaLanguagePref) {
        selectedLanguage = sherpaLanguagePref
    }
    LaunchedEffect(sherpaVoicePref) {
        if (sherpaVoicePref.isNotEmpty()) selectedVoice = sherpaVoicePref
    }
    LaunchedEffect(availableTtsModels) {
        if (selectedVoice.isEmpty() && availableTtsModels.isNotEmpty()) {
            selectedVoice = availableTtsModels.first()
        }
    }

    var isDownloading by remember { mutableStateOf(false) }
    var isDownloadComplete by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    var apiKeyVisible by remember { mutableStateOf(false) }
    var langMenuExpanded by remember { mutableStateOf(false) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))
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

        if (isDownloading) {
            Text(text = downloadStatus, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Spacer(modifier = Modifier.height(16.dp))
        } else if (isDownloadComplete) {
            Text(text = "Models Downloaded Successfully!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isDownloading = true
                        downloadProgress = 0f
                        downloadStatus = "Downloading Models..."
                        val modelsUrl = "https://bibocraftseu.blob.core.windows.net/settings/Models/models.tar.gz?sv=2025-07-05&spr=https&st=2026-03-17T17%3A12%3A24Z&se=2030-12-31T17%3A12%3A00Z&sr=b&sp=r&sig=u1w%2BoQV%2FqWBkR0UhbHl5GcKbw0p5%2BxvkbJYPWyEIYZc%3D"
                        val checksumUrl = "https://bibocraftseu.blob.core.windows.net/settings/Models/models.tar.gz.sha256?sv=2025-07-05&spr=https&st=2026-03-17T20%3A15%3A46Z&se=2030-12-31T20%3A15%3A00Z&sr=b&sp=r&sig=BfbWfmntk6ZV7xOgoP8TSu7UMNSAGFtnfbgeHNFUV98%3D"
                        val modelsSuccess = DownloadManager.downloadAndExtractModels(context, modelsUrl, checksumUrl) { progress ->
                            downloadProgress = progress
                        }

                        if (modelsSuccess) {
                            downloadStatus = "Download Complete!"
                            viewModel.loadAvailableTtsModels()
                            isDownloadComplete = true
                        } else {
                            downloadStatus = "Failed to download Models."
                        }
                        isDownloading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download Required Models")
            }
            if (downloadStatus.isNotEmpty() && !isDownloadComplete) {
                Text(text = downloadStatus, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

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
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Voice Assistant Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedLanguage,
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
                            selectedLanguage = lang
                            langMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedVoice,
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
                            selectedVoice = model
                            voiceMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (apiKey.isNotBlank()) {
                    val finalUserName = if (userName.isBlank()) "User" else userName
                    viewModel.saveUserName(finalUserName)
                    viewModel.saveApiKey(apiKey)
                    viewModel.saveSherpaSettings(selectedLanguage, selectedVoice)
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
