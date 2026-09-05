package com.antigravity.mesh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.network.MeshRepository
import com.antigravity.mesh.ui.components.UpdateDialog
import com.antigravity.mesh.ui.screens.ChatScreen
import com.antigravity.mesh.ui.screens.DashboardScreen
import com.antigravity.mesh.ui.screens.QuickActionsScreen
import com.antigravity.mesh.ui.theme.AccentCyan
import com.antigravity.mesh.ui.theme.AntigravityMeshTheme
import com.antigravity.mesh.ui.theme.SurfaceDark
import com.antigravity.mesh.ui.theme.TextMuted
import com.antigravity.mesh.updater.ApkInstaller
import com.antigravity.mesh.updater.ReleaseUpdateChecker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: MeshRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MeshRepository(this)

        // Initial refresh
        lifecycleScope.launch {
            repository.refreshAllNodes()
        }

        setContent {
            AntigravityMeshTheme {
                MainApp(repository = repository)
            }
        }
    }
}

enum class ScreenTab(val title: String) {
    DASHBOARD("Węzły"),
    CHAT("Czat"),
    ACTIONS("Akcje")
}

@Composable
fun MainApp(repository: MeshRepository) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("antigravity_mesh_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val nodes by repository.nodes.collectAsState()

    var selectedTab by remember { mutableStateOf(ScreenTab.DASHBOARD) }
    var selectedNodeId by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var isChatLoading by remember { mutableStateOf(false) }
    var isActionExecuting by remember { mutableStateOf(false) }
    var lastActionOutput by remember { mutableStateOf<String?>(null) }

    // Auto-update states
    var updateOffer by remember { mutableStateOf<ReleaseUpdateChecker.UpdateOffer?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgressFraction by remember { mutableStateOf(0f) }
    var downloadProgressText by remember { mutableStateOf("") }
    var updateError by remember { mutableStateOf<String?>(null) }

    val checkUpdates: (Boolean) -> Unit = { isManual ->
        coroutineScope.launch {
            val offer = ReleaseUpdateChecker.checkAsync(BuildConfig.VERSION_NAME)
            if (offer != null) {
                updateOffer = offer
                val snoozed = prefs.getString("snooze_update_version", null)
                if (isManual || snoozed != offer.latestVersion) {
                    showUpdateDialog = true
                }
            } else if (isManual) {
                Toast.makeText(context, "Aplikacja jest aktualna (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto check updates on cold start
    LaunchedEffect(Unit) {
        checkUpdates(false)
    }

    val startUpdate: (ReleaseUpdateChecker.UpdateOffer) -> Unit = { offer ->
        if (!ApkInstaller.canInstallPackages(context)) {
            Toast.makeText(context, "Wymagane zezwolenie na instalowanie aplikacji", Toast.LENGTH_LONG).show()
            context.startActivity(ApkInstaller.unknownSourcesSettingsIntent(context))
        } else {
            isDownloadingUpdate = true
            updateError = null
            downloadProgressFraction = 0f
            downloadProgressText = "Inicjalizacja pobierania…"
            ApkInstaller.downloadThenInstall(
                context = context,
                apkUrl = offer.apkUrl,
                onProgress = { text, frac ->
                    downloadProgressText = text
                    downloadProgressFraction = frac
                },
                onError = { err ->
                    isDownloadingUpdate = false
                    updateError = err
                },
                onReadyToInstall = { apkFile ->
                    isDownloadingUpdate = false
                    showUpdateDialog = false
                    ApkInstaller.install(context, apkFile)
                }
            )
        }
    }

    // Auto-select first node if none selected yet
    LaunchedEffect(nodes) {
        if (selectedNodeId.isEmpty() && nodes.isNotEmpty()) {
            selectedNodeId = nodes.first().id
        }
    }

    if (showUpdateDialog && updateOffer != null) {
        UpdateDialog(
            offer = updateOffer!!,
            isDownloading = isDownloadingUpdate,
            progressFraction = downloadProgressFraction,
            progressStatus = downloadProgressText,
            errorMessage = updateError,
            onDismiss = {
                prefs.edit().putString("snooze_update_version", updateOffer!!.latestVersion).apply()
                showUpdateDialog = false
            },
            onStartUpdate = {
                startUpdate(updateOffer!!)
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.DASHBOARD,
                    onClick = { selectedTab = ScreenTab.DASHBOARD },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    label = { Text(ScreenTab.DASHBOARD.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.CHAT,
                    onClick = { selectedTab = ScreenTab.CHAT },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    label = { Text(ScreenTab.CHAT.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.ACTIONS,
                    onClick = { selectedTab = ScreenTab.ACTIONS },
                    icon = { Icon(Icons.Default.FlashOn, contentDescription = null) },
                    label = { Text(ScreenTab.ACTIONS.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                ScreenTab.DASHBOARD -> DashboardScreen(
                    nodes = nodes,
                    isScanning = isScanning,
                    onRefreshAll = {
                        coroutineScope.launch { repository.refreshAllNodes() }
                    },
                    onScanAndPair = {
                        coroutineScope.launch {
                            isScanning = true
                            repository.scanAndPair()
                            repository.refreshAllNodes()
                            isScanning = false
                        }
                    },
                    onNodeChat = { node ->
                        selectedNodeId = node.id
                        selectedTab = ScreenTab.CHAT
                    },
                    onNodeRefresh = {
                        coroutineScope.launch { repository.refreshAllNodes() }
                    },
                    hasUpdateAvailable = updateOffer != null,
                    updateVersion = updateOffer?.latestVersion,
                    onCheckUpdates = { checkUpdates(true) },
                    onOpenUpdateDialog = { showUpdateDialog = true }
                )

                ScreenTab.CHAT -> {
                    val chatHistories by repository.chatHistories.collectAsState()
                    val nodeMessages = chatHistories[selectedNodeId] ?: emptyList()

                    ChatScreen(
                        nodes = nodes,
                        selectedNodeId = selectedNodeId,
                        onSelectNode = { selectedNodeId = it },
                        messages = nodeMessages,
                        isLoading = isChatLoading,
                        onSendMessage = { nodeId, question ->
                            coroutineScope.launch {
                                repository.addChatMessage(
                                    ChatMessage(
                                        nodeId = nodeId,
                                        senderNode = "Ty",
                                        isUser = true,
                                        content = question
                                    )
                                )
                                isChatLoading = true
                                val reply = repository.askAgent(nodeId, question)
                                repository.addChatMessage(reply)
                                isChatLoading = false
                            }
                        }
                    )
                }

                ScreenTab.ACTIONS -> QuickActionsScreen(
                    nodes = nodes,
                    selectedNodeId = selectedNodeId,
                    onSelectNode = { selectedNodeId = it },
                    onRunCommand = { nodeId, cmd ->
                        coroutineScope.launch {
                            isActionExecuting = true
                            lastActionOutput = null
                            val out = repository.executeCommand(nodeId, cmd)
                            lastActionOutput = out
                            isActionExecuting = false
                        }
                    },
                    lastCommandOutput = lastActionOutput,
                    isExecuting = isActionExecuting
                )
            }
        }
    }
}
