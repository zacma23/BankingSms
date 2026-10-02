package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.AiAdvisorScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProvidersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmsSimulatorScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.FinEthioTheme

enum class Screen(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    TRANSACTIONS("Transactions", Icons.Default.ReceiptLong),
    SIMULATOR("SMS Simulator", Icons.Default.Build),
    AI_ADVISOR("AI Advisor", Icons.Default.AutoAwesome),
    PROVIDERS("Providers", Icons.Default.AccountBalance),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FinEthioTheme {
                val viewModel: MainViewModel = viewModel()
                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

                // Ask for SMS permissions on initial start
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* Handled in settings UI */ }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                }

                // Handle back press to return to Dashboard
                if (currentScreen != Screen.DASHBOARD) {
                    BackHandler {
                        currentScreen = Screen.DASHBOARD
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = "Logo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (currentScreen == Screen.DASHBOARD) "FinEthio SMS Tracker" else currentScreen.title,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            },
                            navigationIcon = {
                                if (currentScreen != Screen.DASHBOARD) {
                                    IconButton(onClick = { currentScreen = Screen.DASHBOARD }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                if (currentScreen != Screen.SETTINGS) {
                                    IconButton(
                                        onClick = { currentScreen = Screen.SETTINGS },
                                        modifier = Modifier.testTag("topbar_settings_button")
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        if (currentScreen != Screen.SETTINGS) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp,
                                modifier = Modifier.testTag("bottom_navigation_bar")
                            ) {
                                val navScreens = listOf(
                                    Screen.DASHBOARD,
                                    Screen.TRANSACTIONS,
                                    Screen.SIMULATOR,
                                    Screen.AI_ADVISOR,
                                    Screen.PROVIDERS
                                )

                                navScreens.forEach { screen ->
                                    NavigationBarItem(
                                        selected = currentScreen == screen,
                                        onClick = { currentScreen = screen },
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title, fontSize = 10.sp, maxLines = 1) },
                                        modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            Screen.DASHBOARD -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToTransactions = { currentScreen = Screen.TRANSACTIONS },
                                onNavigateToSimulator = { currentScreen = Screen.SIMULATOR },
                                onNavigateToAi = { currentScreen = Screen.AI_ADVISOR }
                            )
                            Screen.TRANSACTIONS -> TransactionsScreen(
                                viewModel = viewModel
                            )
                            Screen.SIMULATOR -> SmsSimulatorScreen(
                                viewModel = viewModel
                            )
                            Screen.AI_ADVISOR -> AiAdvisorScreen(
                                viewModel = viewModel
                            )
                            Screen.PROVIDERS -> ProvidersScreen(
                                viewModel = viewModel
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
