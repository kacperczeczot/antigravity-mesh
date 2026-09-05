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
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.network.MeshRepository
import com.antigravity.mesh.ui.screens.ChatScreen
import com.antigravity.mesh.ui.screens.DashboardScreen
import com.antigravity.mesh.ui.screens.QuickActionsScreen
import com.antigravity.mesh.ui.theme.AccentCyan
import com.antigravity.mesh.ui.theme.AntigravityMeshTheme
import com.antigravity.mesh.ui.theme.SurfaceDark
import com.antigravity.mesh.ui.theme.TextMuted
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
    val coroutineScope = rememberCoroutineScope()
    val nodes by repository.nodes.collectAsState()
    val chatMessages by repository.chatMessages.collectAsState()

    var selectedTab by remember { mutableStateOf(ScreenTab.DASHBOARD) }
    var selectedNodeId by remember { mutableStateOf("windows-pc") }
    var isScanning by remember { mutableStateOf(false) }
    var isChatLoading by remember { mutableStateOf(false) }
    var isActionExecuting by remember { mutableStateOf(false) }
    var lastActionOutput by remember { mutableStateOf<String?>(null) }

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
                    }
                )

                ScreenTab.CHAT -> ChatScreen(
                    nodes = nodes,
                    selectedNodeId = selectedNodeId,
                    onSelectNode = { selectedNodeId = it },
                    messages = chatMessages,
                    isLoading = isChatLoading,
                    onSendMessage = { nodeId, question ->
                        coroutineScope.launch {
                            repository.addChatMessage(
                                ChatMessage(
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
