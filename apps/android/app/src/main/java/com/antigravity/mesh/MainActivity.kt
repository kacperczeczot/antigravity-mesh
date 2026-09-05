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
import com.antigravity.mesh.ui.theme.AccentCyan
import com.antigravity.mesh.ui.theme.AntigravityMeshTheme
import com.antigravity.mesh.ui.theme.SurfaceDark
import com.antigravity.mesh.ui.theme.TextMuted
import com.antigravity.mesh.updater.ApkInstaller
import com.antigravity.mesh.updater.ReleaseUpdateChecker
import kotlinx.coroutines.launch

import androidx.activity.viewModels
import androidx.compose.runtime.saveable.rememberSaveable
import com.antigravity.mesh.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AntigravityMeshTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("antigravity_mesh_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val nodes by viewModel.nodes.collectAsState()
    val chatHistories by viewModel.chatHistories.collectAsState()

    var activeChatNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var isChatLoading by remember { mutableStateOf(false) }

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val currentChatNodeId = activeChatNodeId

        if (currentChatNodeId == null) {
            // Main View: List of Devices and Conversations
            DashboardScreen(
                nodes = nodes,
                chatHistories = chatHistories,
                isScanning = isScanning,
                onRefreshAll = {
                    viewModel.refreshAllNodes()
                },
                onScanAndPair = {
                    isScanning = true
                    viewModel.scanAndPair {
                        isScanning = false
                    }
                },
                onNodeChat = { node ->
                    activeChatNodeId = node.id
                },
                onNodeRefresh = {
                    viewModel.refreshAllNodes()
                },
                hasUpdateAvailable = updateOffer != null,
                updateVersion = updateOffer?.latestVersion,
                onCheckUpdates = { checkUpdates(true) },
                onOpenUpdateDialog = { showUpdateDialog = true },
                onAddManualNode = { host, port, onComplete ->
                    viewModel.pairWithHost(host, port, onComplete)
                },
                onDeleteNode = { node ->
                    viewModel.removeNode(node.id)
                }
            )
        } else {
            // Chat View for selected Node
            val nodeMessages = chatHistories[currentChatNodeId] ?: emptyList()

            ChatScreen(
                nodes = nodes,
                selectedNodeId = currentChatNodeId,
                onBack = { activeChatNodeId = null },
                onSelectNode = { activeChatNodeId = it },
                messages = nodeMessages,
                isLoading = isChatLoading,
                onSendMessage = { nodeId, question ->
                    viewModel.sendChatMessage(nodeId, question) { loading ->
                        isChatLoading = loading
                    }
                },
                onClearChat = { nodeId ->
                    viewModel.clearChatHistory(nodeId)
                }
            )
        }
    }
}
