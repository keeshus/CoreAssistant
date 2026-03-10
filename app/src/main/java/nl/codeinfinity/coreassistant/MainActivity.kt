package nl.codeinfinity.coreassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settingsManager = SettingsManager(LocalContext.current)
                    
                    // Use remember to only check once when the app starts
                    val needsSetup by remember {
                        mutableStateOf<Boolean?>(null)
                    }
                    var setupDetermined by remember { mutableStateOf(false) }
                    var initialNeedsSetup by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        val apiKey = settingsManager.geminiApiKey.first()
                        initialNeedsSetup = apiKey.isBlank()
                        setupDetermined = true
                    }

                    if (setupDetermined) {
                        AppNavigation(needsSetup = initialNeedsSetup, settingsManager = settingsManager)
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(needsSetup: Boolean, settingsManager: SettingsManager) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (needsSetup) "setup" else "conversations"
    ) {
        composable("setup") {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate("conversations") {
                        popUpTo("setup") { inclusive = true }
                    }
                },
                settingsManager = settingsManager
            )
        }
        composable("conversations") {
            ConversationListScreen(
                onConversationClick = { id -> navController.navigate("chat/$id") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("chat/{conversationId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("conversationId")?.toLongOrNull() ?: -1L
            ChatScreen(
                conversationId = id,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                settingsManager = settingsManager
            )
        }
    }
}
