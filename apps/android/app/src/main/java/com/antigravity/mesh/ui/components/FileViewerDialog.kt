package com.antigravity.mesh.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antigravity.mesh.data.ReadFileResponse
import com.antigravity.mesh.ui.theme.*

fun getFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts", "rs", "py", "js", "ts", "jsx", "tsx", "java", "c", "cpp", "h", "go", "sh", "swift" -> Icons.Default.Code
        "json", "toml", "yaml", "yml", "xml", "gradle", "properties", "env" -> Icons.Default.Settings
        "md", "txt", "log", "rst", "pdf" -> Icons.Default.Description
        "png", "jpg", "jpeg", "svg", "gif", "ico", "webp" -> Icons.Default.Image
        "zip", "tar", "gz", "rar", "7z" -> Icons.Default.Archive
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

fun getFileIconColor(fileName: String): Color {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts", "rs", "go", "sh" -> AccentCyan
        "py", "js", "ts", "jsx", "tsx" -> AccentIndigo
        "json", "toml", "yaml", "yml", "xml" -> AccentGreen
        "md", "txt", "log" -> TextSecondary
        "png", "jpg", "jpeg", "svg", "gif" -> AccentViolet
        else -> TextMuted
    }
}

@Composable
fun FileViewerDialog(
    filePath: String,
    fileName: String? = null,
    fileSize: String? = null,
    initialLine: Int? = null,
    onDismiss: () -> Unit,
    onReadFile: (filePath: String, onResult: (Result<ReadFileResponse>) -> Unit) -> Unit,
    onAskAgentAboutFile: ((filePath: String, fileName: String) -> Unit)? = null,
    onOpenFolderInExplorer: ((folderPath: String) -> Unit)? = null
) {
    val effectiveName = fileName?.ifBlank { null }
        ?: filePath.substringAfterLast('/').substringAfterLast('\\').ifBlank { "plik" }

    var fileContentLoading by remember { mutableStateOf(true) }
    var fileContentData by remember { mutableStateOf<ReadFileResponse?>(null) }
    var fileContentError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(filePath) {
        fileContentLoading = true
        fileContentError = null
        fileContentData = null
        onReadFile(filePath) { res ->
            fileContentLoading = false
            res.onSuccess { data ->
                if (data.error != null && !data.isDir) {
                    fileContentError = data.error
                } else {
                    fileContentData = data
                }
            }.onFailure { err ->
                fileContentError = err.localizedMessage ?: "Błąd odczytu pliku"
            }
        }
    }

    // Scroll to initialLine if specified
    LaunchedEffect(fileContentData, initialLine) {
        if (fileContentData != null && initialLine != null && initialLine > 0) {
            val linesCount = fileContentData!!.content.lines().size
            val targetIdx = (initialLine - 1).coerceIn(0, (linesCount - 1).coerceAtLeast(0))
            try {
                listState.scrollToItem(targetIdx)
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
            color = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isDir = fileContentData?.isDir == true
                        Icon(
                            imageVector = if (isDir) Icons.Default.Folder else getFileIcon(effectiveName),
                            contentDescription = null,
                            tint = if (isDir) AccentCyan else getFileIconColor(effectiveName),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = fileContentData?.name?.ifBlank { null } ?: effectiveName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (initialLine != null && initialLine > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ":$initialLine",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan
                                    )
                                }
                            }
                            val displaySize = fileContentData?.let {
                                if (it.size > 0) {
                                    when {
                                        it.size < 1024 -> "${it.size} B"
                                        it.size < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", it.size / 1024.0)
                                        else -> String.format(java.util.Locale.US, "%.1f MB", it.size / (1024.0 * 1024.0))
                                    }
                                } else null
                            } ?: fileSize

                            val infoText = if (displaySize != null) "$displaySize • $filePath" else filePath
                            Text(
                                text = infoText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zamknij",
                            tint = TextSecondary
                        )
                    }
                }

                // Content Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(BgDark)
                        .padding(10.dp)
                ) {
                    when {
                        fileContentLoading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Pobieranie zawartości pliku…", color = TextMuted, fontSize = 12.sp)
                            }
                        }

                        fileContentData?.isDir == true -> {
                            // The path clicked was actually a directory!
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "To jest katalog",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = filePath,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (onOpenFolderInExplorer != null) {
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenFolderInExplorer(filePath)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Otwórz w Eksploratorze Plików", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        fileContentError != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = fileContentError!!,
                                    color = AccentRed,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            fileContentLoading = true
                                            fileContentError = null
                                            onReadFile(filePath) { res ->
                                                fileContentLoading = false
                                                res.onSuccess { data ->
                                                    if (data.error != null && !data.isDir) {
                                                        fileContentError = data.error
                                                    } else {
                                                        fileContentData = data
                                                    }
                                                }.onFailure { err ->
                                                    fileContentError = err.localizedMessage ?: "Błąd odczytu pliku"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Spróbuj ponownie", color = TextPrimary)
                                    }

                                    if (onAskAgentAboutFile != null) {
                                        OutlinedButton(
                                            onClick = {
                                                onDismiss()
                                                onAskAgentAboutFile(filePath, effectiveName)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan)
                                        ) {
                                            Icon(imageVector = Icons.Default.SmartToy, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Zapytaj agenta", color = AccentCyan)
                                        }
                                    }
                                }
                            }
                        }

                        fileContentData?.isBinary == true -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Plik binarny",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Podgląd tekstowy niedostępny dla tego typu pliku (${fileSize ?: ""})",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                if (onAskAgentAboutFile != null) {
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onAskAgentAboutFile(filePath, effectiveName)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Zapytaj agenta o ten plik", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        fileContentData != null -> {
                            val content = fileContentData!!.content
                            val lines = remember(content) { content.lines() }
                            val horizScroll = rememberScrollState()

                            if (content.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Plik jest pusty (0 B)",
                                        fontSize = 13.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(horizScroll)
                                ) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxHeight()
                                    ) {
                                        items(lines.size) { idx ->
                                            val lineNum = idx + 1
                                            val isHighlighted = initialLine != null && lineNum == initialLine

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (isHighlighted) {
                                                            Modifier
                                                                .background(AccentCyan.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                                                .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp)
                                                        } else {
                                                            Modifier.padding(horizontal = 4.dp)
                                                        }
                                                    )
                                            ) {
                                                Text(
                                                    text = "$lineNum".padStart(4, ' '),
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isHighlighted) AccentCyan else TextMuted.copy(alpha = 0.5f),
                                                    modifier = Modifier.padding(end = 12.dp)
                                                )
                                                Text(
                                                    text = lines[idx],
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (isHighlighted) Color.White else TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dialog Bottom Actions Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Copy Button
                        OutlinedButton(
                            onClick = {
                                fileContentData?.content?.let { txt ->
                                    clipboardManager.setText(AnnotatedString(txt))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(context, "Skopiowano zawartość pliku", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = fileContentData != null && !fileContentLoading && fileContentData?.isDir != true,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kopiuj", fontSize = 12.sp, color = TextSecondary)
                        }

                        // Open Folder in Explorer Button
                        if (onOpenFolderInExplorer != null) {
                            val folderTarget = if (fileContentData?.isDir == true) {
                                filePath
                            } else {
                                filePath.substringBeforeLast('/', "").ifBlank {
                                    filePath.substringBeforeLast('\\', "").ifBlank { "." }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenFolderInExplorer(folderTarget)
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Eksplorator", fontSize = 12.sp, color = AccentCyan)
                            }
                        }
                    }

                    // Ask AI Agent about file Button
                    if (onAskAgentAboutFile != null && fileContentData?.isDir != true) {
                        Button(
                            onClick = {
                                val actualName = fileContentData?.name?.ifBlank { null } ?: effectiveName
                                onDismiss()
                                onAskAgentAboutFile(filePath, actualName)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = BgDark,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Zapytaj agenta",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BgDark
                            )
                        }
                    }
                }
            }
        }
    }
}
