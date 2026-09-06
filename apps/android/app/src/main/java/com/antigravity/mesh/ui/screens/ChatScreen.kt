package com.antigravity.mesh.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.data.ReadFileResponse
import com.antigravity.mesh.ui.components.FileViewerDialog
import com.antigravity.mesh.ui.components.MarkdownText
import com.antigravity.mesh.ui.theme.*
import java.io.File

import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PushPin
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
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.antigravity.mesh.data.UploadFileResponse

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
    onUploadFile: ((targetDir: String, fileName: String, uri: Uri, onProgress: (Float) -> Unit, onDone: (Result<UploadFileResponse>) -> Unit) -> Unit)? = null
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showClearChatDialog by remember { mutableStateOf(false) }
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
    var viewingFileRequest by remember { mutableStateOf<Pair<String, Int?>?>(null) }

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
                    viewingFileRequest = Pair(cleanPath, targetLine)
                } else {
                    onOpenFiles(selectedNodeId, cleanPath)
                }
            }
        }
    }

    // Intercept system back button / gesture to return to device list
    BackHandler(onBack = onBack)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Bar with Back Button & Node Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wróć",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentNode?.displayName ?: "Rozmawiaj z Agentem",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentNode?.isPinned == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Przypięty",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        val statusText = buildString {
                            append(if (currentNode?.isOnline == true) "Online" else "Offline")
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // File Explorer Icon — Always visible and accessible
                    IconButton(onClick = { onOpenFiles(selectedNodeId, null) }) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Przeglądaj pliki",
                            tint = if (currentNode?.isOnline == true) AccentCyan else TextSecondary
                        )
                    }

                    if (messages.isNotEmpty()) {
                        IconButton(onClick = {
                            val exportText = buildString {
                                appendLine("# Czat z agentem: ${currentNode?.displayName ?: selectedNodeId}")
                                appendLine("Adres: ${currentNode?.host}:${currentNode?.port}")
                                appendLine("---")
                                appendLine()
                                messages.forEach { msg ->
                                    if (msg.isUser) {
                                        appendLine("### 👤 Ty:")
                                    } else {
                                        appendLine("### 🤖 ${msg.senderNode}:")
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
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Eksportuj czat",
                                tint = TextSecondary
                            )
                        }

                        IconButton(onClick = { showClearChatDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Wyczyść czat",
                                tint = AccentRed
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = BorderDark, thickness = 1.dp)
        }


        // Messages List
        if (messages.isEmpty() && !isLoading) {
            // Minimalist empty state for conversation
            val selectedNode = nodes.find { it.id == selectedNodeId }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg, onLinkClick = handleLinkClick)
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

        // Bottom Input Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Attachment Paperclip Button — Always visible in chat bar
                    IconButton(
                        onClick = {
                            if (!isUploadingFile && onUploadFile != null) {
                                filePickerLauncher.launch("*/*")
                            }
                        },
                        enabled = !isUploadingFile && onUploadFile != null,
                        modifier = Modifier
                            .size(42.dp)
                            .padding(bottom = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Wgraj i załącz plik z telefonu",
                            tint = if (onUploadFile != null) AccentCyan else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedTextField(
                        value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Zadaj pytanie agentowi...", color = TextMuted, fontSize = 13.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceVariantDark,
                        unfocusedContainerColor = SurfaceVariantDark,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AccentRed)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStopGenerating()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Zatrzymaj",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
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
                            contentDescription = "Wyślij",
                            tint = if (inputText.isNotBlank()) TextPrimary else TextMuted,
                            modifier = Modifier.size(20.dp)
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
                        Text("Wyczyść", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearChatDialog = false }) {
                        Text("Anuluj", color = TextSecondary)
                    }
                }
            )
        }

        // Modal file viewer triggered by clicking file links in chat
        viewingFileRequest?.let { req ->
            if (onReadFile != null) {
                FileViewerDialog(
                    filePath = req.first,
                    initialLine = req.second,
                    onDismiss = { viewingFileRequest = null },
                    onReadFile = onReadFile,
                    onAskAgentAboutFile = { filePath, fileName ->
                        viewingFileRequest = null
                        val prompt = "Przeanalizuj plik $fileName (ścieżka: $filePath) i wyjaśnij jego zawartość oraz działanie."
                        onSendMessage(selectedNodeId, prompt)
                    },
                    onOpenFolderInExplorer = { folderPath ->
                        viewingFileRequest = null
                        onOpenFiles(selectedNodeId, folderPath)
                    },
                    onDownloadRawFile = onDownloadRawFile,
                    rawFileStreamUrl = getRawFileStreamUrl?.invoke(req.first)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onLinkClick: ((String) -> Unit)? = null
) {
    val isUser = message.isUser
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AntigravityAvatarGradient)
                    .border(1.dp, AccentViolet.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(min = 40.dp, max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) SurfaceElevated
                    else if (message.isError) AccentRed.copy(alpha = 0.12f)
                    else SurfaceDark
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) AccentCyan.copy(alpha = 0.35f) else if (message.isError) AccentRed.copy(alpha = 0.5f) else BorderDark,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .clickable(enabled = isUser) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    clipboardManager.setText(AnnotatedString(message.content))
                    Toast.makeText(context, "Skopiowano do schowka", Toast.LENGTH_SHORT).show()
                }
                .padding(12.dp)
        ) {
            if (!isUser) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.senderNode,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (message.isError) AccentRed else AccentCyan
                    )

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            clipboardManager.setText(AnnotatedString(message.content))
                            Toast.makeText(context, "Skopiowano do schowka", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Kopiuj treść",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (isUser) {
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            } else {
                MarkdownText(
                    markdown = message.content,
                    textColor = TextPrimary,
                    onLinkClick = onLinkClick
                )
            }
        }
    }
}
