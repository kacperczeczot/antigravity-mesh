package com.antigravity.mesh.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antigravity.mesh.data.FileItem
import com.antigravity.mesh.data.FileQueryResponse
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.data.ReadFileResponse
import com.antigravity.mesh.ui.components.FileViewerDialog
import com.antigravity.mesh.ui.components.getFileIcon
import com.antigravity.mesh.ui.components.getFileIconColor
import com.antigravity.mesh.ui.theme.*

@Composable
fun FileExplorerScreen(
    node: MeshNode,
    initialPath: String = ".",
    onBack: () -> Unit,
    onLoadFiles: (path: String?, onResult: (Result<FileQueryResponse>) -> Unit) -> Unit,
    onReadFile: (filePath: String, onResult: (Result<ReadFileResponse>) -> Unit) -> Unit,
    onAskAgentAboutFile: (filePath: String, fileName: String) -> Unit
) {
    var currentPath by rememberSaveable { mutableStateOf(initialPath.ifBlank { "." }) }
    var parentPath by rememberSaveable { mutableStateOf<String?>(null) }
    var itemsList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var selectedFileToView by remember { mutableStateOf<FileItem?>(null) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    val loadDirectory: (String?) -> Unit = { targetPath ->
        isLoading = true
        errorMessage = null
        onLoadFiles(targetPath) { res ->
            isLoading = false
            res.onSuccess { resp ->
                if (resp.error != null) {
                    errorMessage = resp.error
                } else {
                    currentPath = resp.currentPath.ifBlank { targetPath ?: "." }
                    parentPath = resp.parentPath
                    itemsList = resp.items
                }
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Nie udało się pobrać listy plików"
            }
        }
    }

    // Intercept back button: go up if parentPath exists, otherwise exit screen
    BackHandler {
        if (!parentPath.isNullOrBlank() && parentPath != currentPath) {
            loadDirectory(parentPath)
        } else {
            onBack()
        }
    }

    // Initial load
    LaunchedEffect(node.id, initialPath) {
        loadDirectory(initialPath.ifBlank { "." })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wróć",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Eksplorator Plików",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${node.displayName} (${node.host})",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentCyan
                    )
                }
            }

            IconButton(
                onClick = { loadDirectory(currentPath) },
                modifier = Modifier
                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                    .background(SurfaceDark, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Odśwież",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Breadcrumbs & Quick Nav Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Root / Project '.' Button
                IconButton(
                    onClick = { loadDirectory(".") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = "Katalog roboczy projektu",
                        tint = AccentCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Home '~' Button
                IconButton(
                    onClick = { loadDirectory("~") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Katalog domowy",
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Parent '..' Button
                IconButton(
                    onClick = {
                        if (!parentPath.isNullOrBlank()) {
                            loadDirectory(parentPath)
                        }
                    },
                    enabled = !parentPath.isNullOrBlank() && parentPath != currentPath,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Katalog wyżej",
                        tint = if (!parentPath.isNullOrBlank() && parentPath != currentPath) TextPrimary else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Current Path text (Horizontally Scrollable)
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState)
                        .padding(start = 4.dp)
                ) {
                    Text(
                        text = currentPath,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search within current directory
        if (itemsList.isNotEmpty() || searchQuery.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filtruj pliki w tym katalogu...", color = TextMuted, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Wyczyść",
                                tint = TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceVariantDark,
                    unfocusedContainerColor = SurfaceVariantDark,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // File List / Loading / Error
        val filteredItems = remember(itemsList, searchQuery) {
            if (searchQuery.isBlank()) itemsList
            else itemsList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = AccentCyan, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Ładowanie plików…", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                errorMessage != null -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Błąd odczytu katalogu", fontWeight = FontWeight.Bold, color = AccentRed)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(errorMessage!!, color = TextPrimary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { loadDirectory(currentPath) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                            ) {
                                Text("Spróbuj ponownie", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                filteredItems.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Brak plików pasujących do wyszukiwania" else "Ten katalog jest pusty",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredItems, key = { it.path }) { item ->
                            FileListItem(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        loadDirectory(item.path)
                                    } else {
                                        selectedFileToView = item
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Code / File Viewer Dialog
    selectedFileToView?.let { fileItem ->
        FileViewerDialog(
            filePath = fileItem.path,
            fileName = fileItem.name,
            fileSize = fileItem.formattedSize,
            onDismiss = { selectedFileToView = null },
            onReadFile = onReadFile,
            onAskAgentAboutFile = onAskAgentAboutFile
        )
    }
}

@Composable
private fun FileListItem(
    item: FileItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (item.isDirectory) AccentCyan.copy(alpha = 0.12f) else SurfaceVariantDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isDirectory) Icons.Default.Folder else getFileIcon(item.name),
                        contentDescription = null,
                        tint = if (item.isDirectory) AccentCyan else getFileIconColor(item.name),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 13.sp,
                        fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.isDirectory) {
                        Text(
                            text = item.formattedSize,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            if (item.isDirectory) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
