package com.antigravity.mesh.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.data.ReadFileResponse
import com.antigravity.mesh.ui.components.FileViewerDialog
import com.antigravity.mesh.ui.components.MarkdownText
import com.antigravity.mesh.ui.components.LocalMermaidFullscreenHandler
import com.antigravity.mesh.ui.components.MermaidFullscreenDialog
import com.antigravity.mesh.ui.components.getNodeDeviceIcon
import com.antigravity.mesh.ui.theme.*
import java.io.File

import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.antigravity.mesh.data.UploadFileResponse

import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.antigravity.mesh.data.ChatSession
import com.antigravity.mesh.data.QueuedMessage
import com.antigravity.mesh.ui.components.QueueDeck

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    nodes: List<MeshNode>,
    selectedNodeId: String,
    onBack: () -> Unit = {},
    onSelectNode: (String) -> Unit,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    agentStatus: String? = null,
    onSendMessage: (String, String) -> Unit,
    onStopGenerating: () -> Unit = {},
    onOpenFiles: (nodeId: String, path: String?) -> Unit = { _, _ -> },
    onReadFile: ((filePath: String, onResult: (Result<ReadFileResponse>) -> Unit) -> Unit)? = null,
    onDownloadRawFile: ((filePath: String, destFile: File, onProgress: (Float) -> Unit, onDone: (Result<File>) -> Unit) -> Unit)? = null,
    getRawFileStreamUrl: ((filePath: String) -> String?)? = null,
    onClearChat: (String) -> Unit = {},
    onUploadFile: ((targetDir: String, fileName: String, uri: Uri, onProgress: (Float) -> Unit, onDone: (Result<UploadFileResponse>) -> Unit) -> Unit)? = null,
    onPermissionsClick: ((String) -> Unit)? = null,
    sessions: List<ChatSession> = emptyList(),
    activeSessionId: String? = null,
    onSelectSession: ((String) -> Unit)? = null,
    onCreateSession: (() -> Unit)? = null,
    onRenameSession: ((sessionId: String, newTitle: String) -> Unit)? = null,
    onDeleteSession: ((sessionId: String) -> Unit)? = null,
    generatingSessionId: String? = null,
    onRecoverTask: ((String) -> Unit)? = null,
    onFastTrackMessage: ((messageId: String) -> Unit)? = null,
    onCancelQueuedMessage: ((messageId: String) -> Unit)? = null,
    onSendImmediate: ((nodeId: String, question: String) -> Unit)? = null,
    queuedMessages: List<QueuedMessage> = emptyList(),
    onEditQueuedMessage: ((QueuedMessage) -> Unit)? = null
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val initialItemIndex = remember(selectedNodeId, activeSessionId) {
        if (messages.isNotEmpty()) messages.size - 1 else 0
    }
    val listState = key(selectedNodeId, activeSessionId) {
        rememberLazyListState(initialFirstVisibleItemIndex = initialItemIndex)
    }
    var hasInitialScrolled by remember(selectedNodeId, activeSessionId) { mutableStateOf(false) }
    var showClearChatDialog by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var renameSessionText by remember { mutableStateOf("") }
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    var sessionMenuExpandedId by remember { mutableStateOf<String?>(null) }
    val currentNode = nodes.find { it.id == selectedNodeId }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Keep screen on during AI query generation to prevent Android Doze/network drop
    val activity = context as? android.app.Activity
    DisposableEffect(isLoading) {
        if (isLoading) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var isUploadingFile by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var uploadingFileName by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && onUploadFile != null) {
            val resolvedName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            } ?: "upload_${System.currentTimeMillis()}"

            uploadingFileName = resolvedName
            isUploadingFile = true
            uploadProgress = 0f

            onUploadFile(".", resolvedName, uri, { progress ->
                uploadProgress = progress
            }) { res ->
                isUploadingFile = false
                res.onSuccess { uploadResp ->
                    val uploadedPath = uploadResp.path ?: resolvedName
                    Toast.makeText(context, "Załączono: $resolvedName", Toast.LENGTH_SHORT).show()
                    val tag = "[Załącznik: $resolvedName](file://$uploadedPath)"
                    inputText = if (inputText.isBlank()) {
                        "Przeanalizuj plik $resolvedName ($tag)"
                    } else {
                        "$inputText\n$tag"
                    }
                }.onFailure { err ->
                    Toast.makeText(context, "Błąd wgrywania pliku: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // State for viewing file modal triggered by markdown links
    var viewingFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingFileLine by rememberSaveable { mutableStateOf<Int?>(null) }
    var viewingMermaidCode by rememberSaveable { mutableStateOf<String?>(null) }

    val handleLinkClick: (String) -> Unit = { rawTarget ->
        val target = rawTarget.trim()
        if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Nie można otworzyć linku: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // File or directory link!
            var cleanPath = target
            var targetLine: Int? = null

            val hashIdx = cleanPath.indexOf('#')
            if (hashIdx != -1) {
                val fragment = cleanPath.substring(hashIdx + 1)
                cleanPath = cleanPath.substring(0, hashIdx)
                val lineMatch = Regex("""(?:L|line)?(\d+)""", RegexOption.IGNORE_CASE).find(fragment)
                targetLine = lineMatch?.groupValues?.get(1)?.toIntOrNull()
            }

            cleanPath = cleanPath.trim()
            val lower = cleanPath.lowercase()
            if (lower.startsWith("file://localhost/")) {
                cleanPath = cleanPath.substring(16)
            } else if (lower.startsWith("file:///")) {
                val rest = cleanPath.substring(8)
                cleanPath = if (rest.length >= 2 && rest[1] == ':') {
                    rest
                } else {
                    cleanPath.substring(7)
                }
            } else if (lower.startsWith("file://")) {
                cleanPath = cleanPath.substring(7)
            } else if (lower.startsWith("file:")) {
                cleanPath = cleanPath.substring(5)
            }

            cleanPath = try {
                java.net.URLDecoder.decode(cleanPath, "UTF-8")
            } catch (_: Exception) {
                cleanPath
            }

            if (cleanPath.isNotBlank()) {
                if (onReadFile != null) {
                    viewingFilePath = cleanPath
                    viewingFileLine = targetLine
                } else {
                    onOpenFiles(selectedNodeId, cleanPath)
                }
            }
        }
    }

    // Intercept system back button / gesture to close modal or return to device list
    BackHandler {
        if (viewingMermaidCode != null) {
            viewingMermaidCode = null
        } else if (viewingFilePath != null) {
            viewingFilePath = null
            viewingFileLine = null
        } else {
            onBack()
        }
    }

    LaunchedEffect(selectedNodeId, activeSessionId, messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            val targetIndex = if (isLoading) messages.size else (messages.size - 1)
            if (!hasInitialScrolled) {
                listState.scrollToItem(targetIndex)
                hasInitialScrolled = true
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isImeVisible = WindowInsets.isImeVisible
    val isCompactLandscape = isLandscape && isImeVisible

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalMermaidFullscreenHandler provides { code -> viewingMermaidCode = code }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .windowInsetsPadding(
                        if (isImeVisible) WindowInsets.ime else WindowInsets.navigationBars
                    )
            ) {
            // Top Bar with Back Button & Node Selector
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceDark
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = if (isCompactLandscape) 2.dp else 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = if (isCompactLandscape) Modifier.size(32.dp) else Modifier
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Wróć",
                                    tint = TextPrimary,
                                    modifier = if (isCompactLandscape) Modifier.size(18.dp) else Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (currentNode != null) {
                                        Icon(
                                            imageVector = getNodeDeviceIcon(currentNode),
                                            contentDescription = null,
                                            tint = if (currentNode.isOnline) AccentCyan else TextMuted,
                                            modifier = Modifier.size(if (isCompactLandscape) 14.dp else 16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = currentNode?.displayName ?: "Rozmawiaj z Agentem",
                                        style = if (isCompactLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (currentNode?.isPinned == true && !isCompactLandscape) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Przypięty",
                                            tint = AccentCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                if (!isCompactLandscape) {
                                    val statusText = buildString {
                                        append(if (currentNode?.isOnline == true) "Aktywny w sieci" else "Nieosiągalny")
                                        append(" • ${currentNode?.host}")
                                        if (currentNode?.customName != null) {
                                            append(" (${currentNode.name})")
                                        }
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (currentNode?.isOnline == true) AccentGreen else AccentRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!isCompactLandscape) {
                                // File Explorer Icon — Always visible and accessible with Tooltip
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text("Przeglądaj pliki węzła") } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(onClick = { onOpenFiles(selectedNodeId, null) }) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = "Przeglądaj pliki",
                                            tint = if (currentNode?.isOnline == true) AccentCyan else TextSecondary
                                        )
                                    }
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = if (isCompactLandscape) Modifier.size(32.dp) else Modifier
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Więcej opcji",
                                        tint = TextSecondary,
                                        modifier = if (isCompactLandscape) Modifier.size(18.dp) else Modifier.size(24.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier
                                        .background(SurfaceDark)
                                        .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                ) {
                                if (onPermissionsClick != null) {
                                    val isOnline = currentNode?.isOnline == true
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Audyt uprawnień i diagnostyka",
                                                color = if (isOnline) TextPrimary else TextMuted,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Security,
                                                null,
                                                tint = if (isOnline) AccentViolet else TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        enabled = isOnline,
                                        onClick = {
                                            showMoreMenu = false
                                            onPermissionsClick(selectedNodeId)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Eksportuj rozmowę",
                                            color = if (messages.isNotEmpty()) TextPrimary else TextMuted,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Share,
                                            null,
                                            tint = if (messages.isNotEmpty()) AccentCyan else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    enabled = messages.isNotEmpty(),
                                    onClick = {
                                        showMoreMenu = false
                                        val exportText = buildString {
                                            appendLine("# Czat z agentem: ${currentNode?.displayName ?: selectedNodeId}")
                                            appendLine("Adres: ${currentNode?.host}:${currentNode?.port}")
                                            appendLine("---")
                                            appendLine()
                                            messages.forEach { msg ->
                                                if (msg.isUser) {
                                                    appendLine("### 👤 Ty:")
                                                } else {
                                                    appendLine("### 🤖 ${currentNode?.displayName ?: "Agent"}:")
                                                }
                                                appendLine(msg.content)
                                                appendLine()
                                            }
                                        }
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, exportText)
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Eksportuj rozmowę")
                                        context.startActivity(shareIntent)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Wyczyść historię",
                                            color = if (messages.isNotEmpty()) AccentRed else TextMuted,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            null,
                                            tint = if (messages.isNotEmpty()) AccentRed else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    enabled = messages.isNotEmpty(),
                                    onClick = {
                                        showMoreMenu = false
                                        showClearChatDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = BorderDark, thickness = 1.dp)

        // Session Threads Bar (v2.7) - hide while typing in landscape to maximize vertical typing space
        if (!isCompactLandscape && (sessions.isNotEmpty() || onCreateSession != null)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceDark
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onCreateSession != null) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text("Nowy wątek") } },
                                state = rememberTooltipState()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceVariantDark,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onCreateSession()
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Nowy wątek",
                                            tint = AccentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        sessions.forEach { session ->
                            val isSelected = session.id == activeSessionId
                            val isThisSessionGenerating = session.id == generatingSessionId
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else SurfaceVariantDark,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) AccentCyan else BorderDark
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (!isSelected && onSelectSession != null) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onSelectSession(session.id)
                                                } else if (isSelected) {
                                                    sessionMenuExpandedId = session.id
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                sessionMenuExpandedId = session.id
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isThisSessionGenerating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(10.dp),
                                                strokeWidth = 1.5.dp,
                                                color = AccentCyan
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = session.title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) AccentCyan else TextPrimary,
                                            maxLines = 1
                                        )
                                        if (isSelected && (onRenameSession != null || onDeleteSession != null)) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Opcje wątku",
                                                tint = AccentCyan.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = sessionMenuExpandedId == session.id,
                                    onDismissRequest = { sessionMenuExpandedId = null },
                                    modifier = Modifier.background(SurfaceDark)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Zmień nazwę", color = TextPrimary, fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Edit, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                        },
                                        onClick = {
                                            sessionMenuExpandedId = null
                                            renameSessionText = session.title
                                            sessionToRename = session
                                        }
                                    )
                                    if (sessions.size > 1 && onDeleteSession != null) {
                                        DropdownMenuItem(
                                            text = { Text("Usuń wątek", color = AccentRed, fontSize = 13.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                                            },
                                            onClick = {
                                                sessionMenuExpandedId = null
                                                sessionToDelete = session
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = BorderDark, thickness = 1.dp)
        }

        // Messages List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (messages.isEmpty() && !isLoading) {
                // Minimalist empty state for conversation
                val selectedNode = nodes.find { it.id == selectedNodeId }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 960.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = selectedNode?.displayName ?: "Agent AI",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Wpisz polecenie lub pytanie poniżej, aby rozpocząć rozmowę z agentem.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            SuggestionChip(
                                onClick = { onOpenFiles(selectedNodeId, null) },
                                label = { Text("Przeglądaj pliki", fontSize = 12.sp) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = SurfaceVariantDark,
                                    labelColor = TextPrimary
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = BorderDark
                                )
                            )

                            if (onPermissionsClick != null && currentNode?.isOnline == true) {
                                SuggestionChip(
                                    onClick = { onPermissionsClick(selectedNodeId) },
                                    label = { Text("Audyt uprawnień", fontSize = 12.sp) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = AccentViolet,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = SurfaceVariantDark,
                                        labelColor = TextPrimary
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = BorderDark
                                    )
                                )
                            }

                            SuggestionChip(
                                onClick = { inputText = "Sprawdź stan procesów i zasobów maszyny" },
                                label = { Text("Stan systemu", fontSize = 12.sp) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = SurfaceVariantDark,
                                    labelColor = TextPrimary
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = BorderDark
                                )
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 960.dp),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(
                            message = msg,
                            onLinkClick = handleLinkClick,
                            onRecoverTask = onRecoverTask,
                            onFastTrackMessage = onFastTrackMessage,
                            onCancelQueuedMessage = onCancelQueuedMessage
                        )
                    }

                    if (isLoading) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceVariantDark.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = AccentCyan,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = agentStatus ?: "Agent myśli...",
                                        fontSize = 12.sp,
                                        color = if (agentStatus != null) AccentCyan else TextSecondary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Queue Deck docked above composer - hide while typing in landscape
        val isDeckVisible = queuedMessages.isNotEmpty() && !isCompactLandscape
        if (isDeckVisible) {
            QueueDeck(
                queuedMessages = queuedMessages,
                onEditMessage = { item ->
                    inputText = item.text
                    onEditQueuedMessage?.invoke(item)
                },
                onFastTrackMessage = { id -> onFastTrackMessage?.invoke(id) },
                onCancelMessage = { id -> onCancelQueuedMessage?.invoke(id) }
            )
        }

        // Bottom Input Area - seamlessly fuse with QueueDeck when queued messages exist
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            shape = if (isDeckVisible) RectangleShape else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp)
                ) {
                    if (isUploadingFile) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceVariantDark)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Wgrywanie $uploadingFileName...",
                                fontSize = 12.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (uploadProgress > 0f) {
                                Text(
                                    text = "${(uploadProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AccentCyan
                                )
                            }
                        }
                        HorizontalDivider(color = BorderDark, thickness = 1.dp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 10.dp,
                                vertical = if (isCompactLandscape) 4.dp else 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attachment Paperclip Button — Always visible in chat bar
                        IconButton(
                            onClick = {
                                if (!isUploadingFile && onUploadFile != null) {
                                    filePickerLauncher.launch("*/*")
                                }
                            },
                            enabled = !isUploadingFile && onUploadFile != null,
                            modifier = Modifier.size(if (isCompactLandscape) 36.dp else 44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Wgraj i załącz plik z telefonu",
                                tint = if (onUploadFile != null) AccentCyan else TextSecondary,
                                modifier = Modifier.size(if (isCompactLandscape) 20.dp else 24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Zadaj pytanie agentowi...", color = TextMuted, fontSize = 13.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            shape = RoundedCornerShape(if (isCompactLandscape) 14.dp else 20.dp),
                            modifier = Modifier.weight(1f),
                            maxLines = if (isCompactLandscape) 2 else 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceVariantDark,
                                unfocusedContainerColor = SurfaceVariantDark,
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(if (isCompactLandscape) 4.dp else 6.dp))
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .size(if (isCompactLandscape) 36.dp else 40.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed.copy(alpha = 0.2f))
                                    .border(1.dp, AccentRed.copy(alpha = 0.6f), CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onStopGenerating()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Zatrzymaj generowanie",
                                    tint = AccentRed,
                                    modifier = Modifier.size(if (isCompactLandscape) 18.dp else 20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(if (isCompactLandscape) 4.dp else 6.dp))
                        }

                        if (isLoading && inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (onSendImmediate != null) {
                                        onSendImmediate(selectedNodeId, inputText.trim())
                                    } else {
                                        onSendMessage(selectedNodeId, inputText.trim())
                                    }
                                    inputText = ""
                                },
                                modifier = Modifier
                                    .size(if (isCompactLandscape) 36.dp else 40.dp)
                                    .background(AccentAmber.copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, AccentAmber.copy(alpha = 0.7f), CircleShape)
                                    .semantics { contentDescription = "Wyślij natychmiast" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(if (isCompactLandscape) 18.dp else 20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(if (isCompactLandscape) 4.dp else 6.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(if (isCompactLandscape) 36.dp else 40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank()) AntigravityButtonGradient
                                    else androidx.compose.ui.graphics.SolidColor(SurfaceVariantDark)
                                )
                                .clickable(enabled = inputText.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSendMessage(selectedNodeId, inputText.trim())
                                    inputText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (isLoading) "Dodaj do kolejki" else "Wyślij",
                                tint = if (inputText.isNotBlank()) TextPrimary else TextMuted,
                                modifier = Modifier.size(if (isCompactLandscape) 18.dp else 20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showClearChatDialog) {
            AlertDialog(
                onDismissRequest = { showClearChatDialog = false },
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = "Wyczyścić czat?",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Czy na pewno chcesz usunąć całą historię rozmowy z urządzeniem „${currentNode?.displayName ?: "tej maszyny"}”?\n\nTej operacji nie można cofnąć.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearChat(selectedNodeId)
                            showClearChatDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("Wyczyść", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearChatDialog = false }) {
                        Text("Anuluj", color = TextSecondary, maxLines = 1, softWrap = false)
                    }
                }
            )
        }

        // Rename Session Dialog
        sessionToRename?.let { session ->
            AlertDialog(
                onDismissRequest = { sessionToRename = null },
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = "Zmień nazwę wątku",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    OutlinedTextField(
                        value = renameSessionText,
                        onValueChange = { renameSessionText = it },
                        label = { Text("Nazwa wątku") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark,
                            cursorColor = AccentCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newTitle = renameSessionText.trim()
                            if (newTitle.isNotEmpty()) {
                                onRenameSession?.invoke(session.id, newTitle)
                            }
                            sessionToRename = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = BgDark
                        )
                    ) {
                        Text("Zapisz", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sessionToRename = null }) {
                        Text("Anuluj", color = TextSecondary, maxLines = 1, softWrap = false)
                    }
                }
            )
        }

        // Delete Session Dialog
        sessionToDelete?.let { session ->
            AlertDialog(
                onDismissRequest = { sessionToDelete = null },
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = "Usuń wątek",
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
                    )
                },
                text = {
                    Text(
                        text = "Czy na pewno chcesz usunąć wątek „${session.title}”?\n\nCała historia rozmowy w tym wątku zostanie bezpowrotnie skasowana.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteSession?.invoke(session.id)
                            sessionToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("Usuń", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sessionToDelete = null }) {
                        Text("Anuluj", color = TextSecondary, maxLines = 1, softWrap = false)
                    }
                }
            )
        }
        }
    }

        // Modal file viewer triggered by clicking file links in chat
        viewingFilePath?.let { filePath ->
            if (onReadFile != null) {
                FileViewerDialog(
                    filePath = filePath,
                    initialLine = viewingFileLine,
                    onDismiss = {
                        viewingFilePath = null
                        viewingFileLine = null
                    },
                    onReadFile = onReadFile,
                    onAskAgentAboutFile = { fPath, fileName ->
                        viewingFilePath = null
                        viewingFileLine = null
                        val prompt = "Przeanalizuj plik $fileName (ścieżka: $fPath) i wyjaśnij jego zawartość oraz działanie."
                        onSendMessage(selectedNodeId, prompt)
                    },
                    onOpenFolderInExplorer = { folderPath ->
                        viewingFilePath = null
                        viewingFileLine = null
                        onOpenFiles(selectedNodeId, folderPath)
                    },
                    onDownloadRawFile = onDownloadRawFile,
                    rawFileStreamUrl = getRawFileStreamUrl?.invoke(filePath)
                )
            }
        }

        // Fullscreen Mermaid diagram overlay with uniform margins
        viewingMermaidCode?.let { code ->
            MermaidFullscreenDialog(
                code = code,
                onDismiss = { viewingMermaidCode = null }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    onLinkClick: ((String) -> Unit)? = null,
    onRecoverTask: ((String) -> Unit)? = null,
    onFastTrackMessage: ((String) -> Unit)? = null,
    onCancelQueuedMessage: ((String) -> Unit)? = null
) {
    val isUser = message.isUser
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    if (isUser) {
        // User message: compact, right-aligned bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 40.dp, max = 560.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(SurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = AccentCyan.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboardManager.setText(AnnotatedString(message.content))
                        Toast.makeText(context, "Skopiowano do schowka", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val isLongMessage = remember(message.content) {
                    message.content.lines().size > 6 || message.content.length > 280
                }
                var isExpanded by rememberSaveable(message.id) { mutableStateOf(false) }

                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = if (isLongMessage && !isExpanded) 5 else Int.MAX_VALUE,
                    overflow = if (isLongMessage && !isExpanded) TextOverflow.Ellipsis else TextOverflow.Clip
                )

                if (isLongMessage) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isExpanded) "Zwiń ▲" else "Pokaż więcej ▼",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = !isExpanded
                            }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    } else {
        // Agent AI response: full width card utilizing maximum screen width for code, diagrams and tables
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (message.isError) AccentRed.copy(alpha = 0.12f)
                    else SurfaceDark
                )
                .border(
                    width = 1.dp,
                    color = if (message.isError) AccentRed.copy(alpha = 0.5f) else BorderDark,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Header with Node Avatar, Node Name and Copy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AntigravityAvatarGradient)
                            .border(1.dp, AccentViolet.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = message.senderNode,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (message.isError) AccentRed else AccentCyan
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboardManager.setText(AnnotatedString(message.content))
                        Toast.makeText(context, "Skopiowano do schowka", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Kopiuj treść",
                        tint = TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Full-width Markdown text supporting code blocks, Mermaid diagrams, tables, latex, etc.
            MarkdownText(
                markdown = message.content,
                textColor = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                onLinkClick = onLinkClick
            )

            // Smart Recovery Button when task can be recovered from node
            if (message.isError && message.canRecover && onRecoverTask != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRecoverTask(message.nodeId)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AccentCyan.copy(alpha = 0.1f),
                        contentColor = AccentCyan
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sprawdź status na węźle (Wznów)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                }
            }
        }
    }
}
