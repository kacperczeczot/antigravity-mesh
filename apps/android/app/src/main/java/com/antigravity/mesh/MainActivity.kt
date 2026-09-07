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
import com.antigravity.mesh.ui.components.PermissionsAuditDialog
import com.antigravity.mesh.ui.components.UpdateDialog
import com.antigravity.mesh.ui.screens.ChatScreen
import com.antigravity.mesh.ui.screens.DashboardScreen
import com.antigravity.mesh.ui.screens.FileExplorerScreen
import com.antigravity.mesh.ui.theme.AccentCyan
import com.antigravity.mesh.ui.theme.AntigravityMeshTheme
import com.antigravity.mesh.updater.ApkInstaller
import com.antigravity.mesh.updater.ReleaseUpdateChecker
import java.io.File
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
    val agentWorkingStatus by viewModel.agentWorkingStatus.collectAsState()

    var activeChatNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFilesNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFilesPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var isChatLoading by remember { mutableStateOf(false) }

    // Permissions Audit state
    var nodeForPermissions by remember { mutableStateOf<MeshNode?>(null) }
    val permissionsAuditReport by viewModel.permissionsAuditReport.collectAsState()
    val isAuditLoading by viewModel.isAuditLoading.collectAsState()
    val auditError by viewModel.auditError.collectAsState()

    // Auto-update states
    var updateOffer by remember { mutableStateOf<ReleaseUpdateChecker.UpdateOffer?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgressFraction by remember { mutableStateOf(0f) }
    var downloadProgressText by remember { mutableStateOf("") }
    var updateError by remember { mutableStateOf<String?>(null) }

    var pendingApkToInstall by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = pendingApkToInstall
        if (apk != null && apk.exists()) {
            if (ApkInstaller.canInstallPackages(context)) {
                pendingApkToInstall = null
                ApkInstaller.install(context, apk)
            } else {
                Toast.makeText(context, "Brak uprawnienia do instalowania aktualizacji", Toast.LENGTH_LONG).show()
            }
        }
    }

    val checkUpdates: (Boolean) -> Unit = { isManual ->
        if (isManual) {
            Toast.makeText(context, "Sprawdzanie dostępności aktualizacji…", Toast.LENGTH_SHORT).show()
        }
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

    // Auto check updates on cold start & check if app was just updated
    LaunchedEffect(Unit) {
        val lastSeenVersion = prefs.getString("last_seen_version", null)
        val wasJustUpdated = prefs.getBoolean("just_updated", false)
        if (wasJustUpdated || (lastSeenVersion != null && lastSeenVersion != BuildConfig.VERSION_NAME)) {
            Toast.makeText(
                context,
                "✅ Antigravity Mesh zaktualizowano do v${BuildConfig.VERSION_NAME}!",
                Toast.LENGTH_LONG
            ).show()
            prefs.edit()
                .putString("last_seen_version", BuildConfig.VERSION_NAME)
                .putBoolean("just_updated", false)
                .apply()
        } else if (lastSeenVersion == null) {
            prefs.edit().putString("last_seen_version", BuildConfig.VERSION_NAME).apply()
        }

        checkUpdates(false)
    }

    val startUpdate: (ReleaseUpdateChecker.UpdateOffer) -> Unit = { offer ->
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
                downloadProgressFraction = 1f
                downloadProgressText = "Uruchamianie instalatora…"
                showUpdateDialog = false
                isDownloadingUpdate = false
                if (ApkInstaller.canInstallPackages(context)) {
                    ApkInstaller.install(context, apkFile)
                } else {
                    pendingApkToInstall = apkFile
                    Toast.makeText(context, "Zezwól na instalację aktualizacji", Toast.LENGTH_LONG).show()
                    permissionLauncher.launch(ApkInstaller.unknownSourcesSettingsIntent(context))
                }
            }
        )
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
                isDownloadingUpdate = false
            },
            onCancelDownload = {
                isDownloadingUpdate = false
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
        val currentFilesNodeId = activeFilesNodeId
        val currentChatNodeId = activeChatNodeId

        if (currentFilesNodeId != null) {
            val filesNode = nodes.find { it.id == currentFilesNodeId }
            if (filesNode != null) {
                FileExplorerScreen(
                    node = filesNode,
                    initialPath = activeFilesPath ?: ".",
                    onBack = {
                        activeFilesNodeId = null
                        activeFilesPath = null
                    },
                    onLoadFiles = { path, onResult ->
                        viewModel.loadFiles(filesNode.id, path, onResult)
                    },
                    onReadFile = { filePath, onResult ->
                        viewModel.readFile(filesNode.id, filePath, onResult)
                    },
                    onAskAgentAboutFile = { filePath, fileName ->
                        activeFilesNodeId = null
                        activeFilesPath = null
                        activeChatNodeId = filesNode.id
                        val prompt = "Przeanalizuj plik $fileName (ścieżka: $filePath) i wyjaśnij jego zawartość oraz działanie."
                        viewModel.sendChatMessage(filesNode.id, prompt) { loading ->
                            isChatLoading = loading
                        }
                    },
                    onDownloadRawFile = { filePath, destFile, onProgress, onDone ->
                        viewModel.downloadRawFile(filesNode.id, filePath, destFile, onProgress, onDone)
                    },
                    getRawFileStreamUrl = { filePath ->
                        viewModel.getRawFileStreamUrl(filesNode.id, filePath)
                    },
                    onUploadFile = { targetDir, fileName, uri, onProgress, onDone ->
                        viewModel.uploadFile(filesNode.id, targetDir, fileName, uri, context.contentResolver, onProgress, onDone)
                    }
                )
            } else {
                activeFilesNodeId = null
                activeFilesPath = null
            }
        } else if (currentChatNodeId == null) {
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
                onAddManualNode = { host, port, pinOrToken, onComplete ->
                    viewModel.pairWithHost(host, port, pinOrToken, onComplete)
                },
                onDeleteNode = { node ->
                    viewModel.removeNode(node.id)
                },
                onRenameNode = { nodeId, newName ->
                    viewModel.renameNode(nodeId, newName)
                },
                onUpdateNodeDetails = { nodeId, newName, newHost, newPort ->
                    viewModel.updateNodeDetails(nodeId, newName, newHost, newPort)
                },
                onTogglePinNode = { node ->
                    viewModel.togglePinNode(node.id)
                },
                onNodeFilesClick = { node ->
                    activeFilesNodeId = node.id
                    activeFilesPath = null
                },
                onPermissionsClick = { node ->
                    nodeForPermissions = node
                    viewModel.runPermissionsAudit(node.id)
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
                agentStatus = agentWorkingStatus,
                onSendMessage = { nodeId, question ->
                    viewModel.sendChatMessage(nodeId, question) { loading ->
                        isChatLoading = loading
                    }
                },
                onStopGenerating = {
                    viewModel.stopGenerating()
                },
                onOpenFiles = { nodeId, path ->
                    activeFilesNodeId = nodeId
                    activeFilesPath = path
                },
                onReadFile = { filePath, onResult ->
                    viewModel.readFile(currentChatNodeId, filePath, onResult)
                },
                onDownloadRawFile = { filePath, destFile, onProgress, onDone ->
                    viewModel.downloadRawFile(currentChatNodeId, filePath, destFile, onProgress, onDone)
                },
                getRawFileStreamUrl = { filePath ->
                    viewModel.getRawFileStreamUrl(currentChatNodeId, filePath)
                },
                onClearChat = { nodeId ->
                    viewModel.clearChatHistory(nodeId)
                },
                onUploadFile = { targetDir, fileName, uri, onProgress, onDone ->
                    viewModel.uploadFile(currentChatNodeId, targetDir, fileName, uri, context.contentResolver, onProgress, onDone)
                },
                onPermissionsClick = { nodeId ->
                    val node = nodes.find { it.id == nodeId }
                    if (node != null) {
                        nodeForPermissions = node
                        viewModel.runPermissionsAudit(node.id)
                    }
                }
            )
        }

        if (nodeForPermissions != null) {
            PermissionsAuditDialog(
                node = nodeForPermissions!!,
                report = permissionsAuditReport,
                isLoading = isAuditLoading,
                errorMessage = auditError,
                onRefresh = { viewModel.runPermissionsAudit(nodeForPermissions!!.id) },
                onFixAction = { action ->
                    val targetNode = nodeForPermissions
                    if (targetNode != null) {
                        viewModel.fixPermission(targetNode.id, action) { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDismiss = {
                    nodeForPermissions = null
                    viewModel.clearPermissionsAudit()
                }
            )
        }
    }
}
