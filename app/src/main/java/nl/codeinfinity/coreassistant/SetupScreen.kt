package nl.codeinfinity.coreassistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    settingsManager: SettingsManager,
    database: ChatDatabase
) {
    var step by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()
    
    // Step 1: User Name
    var userName by remember { mutableStateOf("") }
    
    // Step 2: API Key
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    
    // Step 3: Model Selection
    var availableModels by remember { mutableStateOf<List<GeminiModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<GeminiModel?>(null) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelFetchError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Initial Setup") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                1 -> {
                    Text(
                        "Welcome to Core Assistant",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Let's get started. What should I call you?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("Your Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { 
                            scope.launch {
                                settingsManager.saveUserName(userName)
                                step = 2
                            }
                        },
                        enabled = userName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next")
                    }
                }
                2 -> {
                    Text(
                        "Gemini API Configuration",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This app uses Google's Gemini API. Please enter your API key to continue.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "You can get an API key from Google AI Studio (aistudio.google.com)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isLoadingModels = true
                                modelFetchError = null
                                try {
                                    val apiService = GeminiApiService.create()
                                    val response = withContext(Dispatchers.IO) {
                                        apiService.getModels(apiKey)
                                    }
                                    val filteredModels = response.models.filter {
                                        it.name.startsWith("models/") &&
                                        (it.displayName.contains("Gemini", ignoreCase = true))
                                    }
                                    
                                    if (filteredModels.isNotEmpty()) {
                                        withContext(Dispatchers.IO) {
                                            val entities = filteredModels.map {
                                                GeminiModelEntity(it.name, it.displayName, it.description)
                                            }
                                            database.geminiModelDao().deleteAllModels()
                                            database.geminiModelDao().insertModels(entities)
                                        }
                                        
                                        availableModels = filteredModels
                                        selectedModel = availableModels.firstOrNull { it.name.contains("gemini-1.5-flash") }
                                            ?: availableModels.first()
                                        step = 3
                                    } else {
                                        modelFetchError = "No compatible Gemini models found."
                                    }
                                } catch (e: Exception) {
                                    modelFetchError = "Error fetching models: ${e.message}"
                                } finally {
                                    isLoadingModels = false
                                }
                            }
                        },
                        enabled = apiKey.isNotBlank() && !isLoadingModels,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Next")
                        }
                    }
                    if (modelFetchError != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(modelFetchError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { step = 1 }, enabled = !isLoadingModels) {
                        Text("Back")
                    }
                }
                3 -> {
                    Text(
                        "Select a Model",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Choose which Gemini model you'd like to use by default.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        val fillMaxWidth = Modifier.fillMaxWidth()
                        OutlinedTextField(
                            value = selectedModel?.displayName ?: "Select Model",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName)
                                            Text(model.description, style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    onClick = {
                                        selectedModel = model
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                settingsManager.saveGeminiApiKey(apiKey)
                                selectedModel?.let { settingsManager.saveGeminiModel(it.name) }
                                onSetupComplete()
                            }
                        },
                        enabled = selectedModel != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Finish Setup")
                    }
                    TextButton(onClick = { step = 2 }) {
                        Text("Back")
                    }
                }
            }
        }
    }
}
