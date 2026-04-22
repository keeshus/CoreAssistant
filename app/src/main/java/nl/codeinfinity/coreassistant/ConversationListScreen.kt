package nl.codeinfinity.coreassistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationListViewModel(private val chatDao: ChatDao, private val settingsManager: SettingsManager) : ViewModel() {
    val conversations: StateFlow<List<Conversation>> = chatDao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startNewConversation(isImageGeneration: Boolean = false, onConversationCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val limit = settingsManager.conversationsLimit.first()
            chatDao.deleteOldConversations(limit - 1) // Make room for new one if needed
            val title = if (isImageGeneration) "Image Generation" else "New Chat"
            val id = chatDao.createNewConversation(title, isImageGeneration)
            onConversationCreated(id)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            chatDao.deleteConversation(conversation)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    database: ChatDatabase
) {
    val context = LocalContext.current
    val settingsManager = SettingsManager(context)
    val viewModel: ConversationListViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationListViewModel(database.chatDao(), settingsManager) as T
        }
    })
    val conversations by viewModel.conversations.collectAsState()
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }

    if (conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to delete this conversation? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    conversationToDelete?.let { viewModel.deleteConversation(it) }
                    conversationToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Core Assistant") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = {
                        viewModel.startNewConversation(isImageGeneration = true) { id ->
                            onConversationClick(id)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Image Generation")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FloatingActionButton(onClick = {
                    viewModel.startNewConversation(isImageGeneration = false) { id ->
                        onConversationClick(id)
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                }
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No conversations yet. Start a new one!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(conversations) { conversation ->
                    ListItem(
                        headlineContent = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("Last active: ${java.text.SimpleDateFormat("MMM dd, HH:mm").format(java.util.Date(conversation.lastModified))}") },
                        trailingContent = {
                            IconButton(onClick = { conversationToDelete = conversation }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Conversation", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.clickable { onConversationClick(conversation.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
