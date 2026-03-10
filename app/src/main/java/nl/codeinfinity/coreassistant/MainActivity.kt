package nl.codeinfinity.coreassistant

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsManager = SettingsManager(this)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val screenshotProtection by settingsManager.screenshotProtection.collectAsState(initial = true)
                    
                    LaunchedEffect(screenshotProtection) {
                        if (screenshotProtection) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    // Use remember to only check once when the app starts
                    var setupDetermined by remember { mutableStateOf(false) }
                    var initialNeedsSetup by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (settingsManager.clearHistoryOnClose.first()) {
                            withContext(Dispatchers.IO) {
                                val db = ChatDatabase.getDatabase(this@MainActivity)
                                db.clearAllTables()
                            }
                        }
                        
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
                onNavigateToLicenses = { navController.navigate("licenses") },
                settingsManager = settingsManager
            )
        }
        composable("licenses") {
            LicensesScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
