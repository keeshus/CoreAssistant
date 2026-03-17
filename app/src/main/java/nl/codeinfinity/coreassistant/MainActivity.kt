package nl.codeinfinity.coreassistant

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent?.getBooleanExtra("open_voice_assistant", false) == true ||
            intent?.action == android.content.Intent.ACTION_ASSIST) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

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
                    var database by remember { mutableStateOf<ChatDatabase?>(null) }
                    var isVoiceAssistantSession by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(Unit) {
                        val db = withContext(Dispatchers.IO) {
                            ChatDatabase.getDatabase(this@MainActivity)
                        }
                        
                        // We no longer copy models from assets, they are downloaded in the setup screen.
                        
                        val apiKey = settingsManager.geminiApiKey.first()
                        initialNeedsSetup = apiKey.isBlank()
                        
                        if (intent?.getBooleanExtra("open_voice_assistant", false) == true ||
                            intent?.action == android.content.Intent.ACTION_ASSIST) {
                            isVoiceAssistantSession = true
                        }
                        
                        database = db
                        setupDetermined = true
                    }

                    if (setupDetermined && database != null) {
                        AppNavigation(needsSetup = initialNeedsSetup, settingsManager = settingsManager, database = database!!, isVoiceAssistant = isVoiceAssistantSession)
                    } else {
                        // Show a loading screen while initializing
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val settingsManager = SettingsManager(this)
            CoroutineScope(Dispatchers.IO).launch {
                if (settingsManager.clearHistoryOnClose.first()) {
                    ChatDatabase.getDatabase(this@MainActivity).clearAllTables()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(needsSetup: Boolean, settingsManager: SettingsManager, database: ChatDatabase, isVoiceAssistant: Boolean = false) {
    val navController = rememberNavController()
    
    val startDest = if (needsSetup) "setup"
                    else if (isVoiceAssistant) "voice_assistant"
                    else "conversations"
                    
    NavHost(
        navController = navController,
        startDestination = startDest
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
                onNavigateToSettings = { navController.navigate("settings") },
                database = database
            )
        }
        composable(
            "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("conversationId") ?: -1L
            ChatScreen(
                conversationId = id,
                onNavigateBack = { navController.popBackStack() },
                database = database
            )
        }
        composable("voice_assistant") {
            val context = LocalContext.current
            VoiceAssistantScreen(
                onExit = {
                    (context as? android.app.Activity)?.finish()
                }
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
