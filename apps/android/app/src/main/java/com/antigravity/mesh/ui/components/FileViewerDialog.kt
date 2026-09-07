package com.antigravity.mesh.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.antigravity.mesh.data.ReadFileResponse
import com.antigravity.mesh.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

fun getFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts", "rs", "py", "js", "ts", "jsx", "tsx", "java", "c", "cpp", "h", "go", "sh", "swift" -> Icons.Default.Code
        "json", "toml", "yaml", "yml", "xml", "gradle", "properties", "env" -> Icons.Default.Settings
        "md", "txt", "log", "rst" -> Icons.Default.Description
        "pdf" -> Icons.Default.PictureAsPdf
        "mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "opus" -> Icons.Default.MusicNote
        "mp4", "mkv", "mov", "avi", "webm" -> Icons.Default.VideoLibrary
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
        "pdf" -> AccentRed
        "mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "opus" -> AccentGreen
        "mp4", "mkv", "mov", "avi", "webm" -> AccentCyan
        "png", "jpg", "jpeg", "svg", "gif", "webp" -> AccentViolet
        else -> TextMuted
    }
}

enum class PreviewCategory {
    TEXT,
    MARKDOWN,
    AUDIO,
    PDF,
    IMAGE,
    GENERIC_BINARY
}

val KNOWN_BINARY_EXTENSIONS = setOf(
    "zip", "rar", "tar", "gz", "bz2", "xz", "7z", "zst", "iso", "dmg", "pkg", "deb", "rpm",
    "apk", "aab", "exe", "dll", "so", "dylib", "bin", "dat", "db", "sqlite", "sqlite3",
    "class", "jar", "pyc", "pyo", "wasm", "o", "a", "lib", "ds_store", "plist", "ipa", "app",
    "doc", "docx", "xls", "xlsx", "ppt", "pptx"
)

fun detectPreviewCategory(fileName: String, isBinary: Boolean, mimeType: String?): PreviewCategory {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val mime = mimeType?.lowercase() ?: ""

    if (ext in listOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "opus") || mime.startsWith("audio/")) {
        return PreviewCategory.AUDIO
    }
    if (ext == "pdf" || mime == "application/pdf") {
        return PreviewCategory.PDF
    }
    if (ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico") || mime.startsWith("image/")) {
        return PreviewCategory.IMAGE
    }
    if (isBinary || ext in KNOWN_BINARY_EXTENSIONS || mime.startsWith("application/octet-stream") || mime.startsWith("application/zip") || mime.startsWith("application/x-")) {
        return PreviewCategory.GENERIC_BINARY
    }
    if (ext in listOf("md", "markdown", "mdown", "mkd")) {
        return PreviewCategory.MARKDOWN
    }
    return PreviewCategory.TEXT
}

fun formatDurationMs(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun openFileWithExternalApp(context: Context, file: File, mimeType: String?) {
    try {
        val effectiveMime = mimeType ?: run {
            val ext = file.name.substringAfterLast('.', "").lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, effectiveMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Otwórz za pomocą..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Brak aplikacji do otwarcia pliku: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun saveFileToDownloads(context: Context, sourceFile: File, displayName: String, mimeType: String?): Boolean {
    return try {
        val effectiveMime = mimeType ?: run {
            val ext = displayName.substringAfterLast('.', "").lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, effectiveMime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { inp ->
                    inp.copyTo(out)
                }
            }
            true
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val dest = File(downloadsDir, displayName)
            sourceFile.copyTo(dest, overwrite = true)
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * Resolves a relative or absolute link target path against the current file's directory.
 */
internal fun resolveRelativeFilePath(currentPath: String, target: String): String {
    val cleanTarget = target.trim()
    if (cleanTarget.startsWith("/") ||
        (cleanTarget.length >= 3 && cleanTarget[1] == ':' && (cleanTarget[2] == '\\' || cleanTarget[2] == '/'))) {
        return cleanTarget
    }

    val isWindows = currentPath.contains('\\') && (!currentPath.contains('/') || currentPath.indexOf('\\') < currentPath.indexOf('/'))
    val separator = if (isWindows) '\\' else '/'

    val baseDir = if (currentPath.contains('/') || currentPath.contains('\\')) {
        currentPath.substringBeforeLast('/').substringBeforeLast('\\')
    } else {
        ""
    }

    if (baseDir.isEmpty()) return cleanTarget

    val baseParts = baseDir.split('/', '\\').filter { it.isNotEmpty() && it != "." }.toMutableList()
    val targetParts = cleanTarget.split('/', '\\').filter { it.isNotEmpty() && it != "." }

    for (part in targetParts) {
        if (part == "..") {
            if (baseParts.isNotEmpty()) {
                baseParts.removeAt(baseParts.size - 1)
            }
        } else {
            baseParts.add(part)
        }
    }

    val prefix = if (currentPath.startsWith("/")) "/" else ""
    return prefix + baseParts.joinToString(separator.toString())
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
    onOpenFolderInExplorer: ((folderPath: String) -> Unit)? = null,
    onDownloadRawFile: ((filePath: String, destFile: File, onProgress: (Float) -> Unit, onDone: (Result<File>) -> Unit) -> Unit)? = null,
    rawFileStreamUrl: String? = null
) {
    var currentFilePath by remember(filePath) { mutableStateOf(filePath) }
    val pathHistory = remember { mutableStateListOf<String>() }

    val effectiveName = remember(currentFilePath, fileName) {
        if (currentFilePath == filePath && !fileName.isNullOrBlank()) {
            fileName
        } else {
            currentFilePath.substringAfterLast('/').substringAfterLast('\\').ifBlank { "plik" }
        }
    }

    var fileContentLoading by remember { mutableStateOf(true) }
    var fileContentData by remember { mutableStateOf<ReadFileResponse?>(null) }
    var fileContentError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // Cache destination for raw downloads
    val cacheDir = remember { File(context.cacheDir, "preview_cache").apply { mkdirs() } }
    val safeFileName = remember(effectiveName) { effectiveName.replace(Regex("[^a-zA-Z0-9._-]"), "_") }
    val fileKey = remember(currentFilePath, safeFileName) {
        val hash = (currentFilePath.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        "${hash}_$safeFileName"
    }
    val cachedFile = remember(fileKey) { File(cacheDir, fileKey) }

    var isDownloaded by remember(currentFilePath) { mutableStateOf(cachedFile.exists() && cachedFile.length() > 0L) }
    var isDownloading by remember(currentFilePath) { mutableStateOf(false) }
    var downloadProgress by remember(currentFilePath) { mutableFloatStateOf(0f) }
    var downloadError by remember(currentFilePath) { mutableStateOf<String?>(null) }
    var isRenderedMarkdownView by remember(currentFilePath) { mutableStateOf(true) }

    val previewCategory = remember(effectiveName, fileContentData) {
        detectPreviewCategory(
            fileName = effectiveName,
            isBinary = fileContentData?.isBinary ?: false,
            mimeType = fileContentData?.mimeType
        )
    }

    LaunchedEffect(currentFilePath) {
        fileContentLoading = true
        fileContentError = null
        fileContentData = null
        onReadFile(currentFilePath) { res ->
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

    // Function to download raw file
    fun startRawDownload() {
        if (onDownloadRawFile == null) return
        isDownloading = true
        downloadError = null
        onDownloadRawFile(currentFilePath, cachedFile, { progress ->
            downloadProgress = progress
        }) { res ->
            isDownloading = false
            res.onSuccess {
                isDownloaded = true
            }.onFailure { err ->
                downloadError = err.localizedMessage ?: "Błąd pobierania pliku"
            }
        }
    }

    // Automatically trigger raw download for media files if not yet downloaded
    LaunchedEffect(previewCategory, onDownloadRawFile, isDownloaded) {
        if (!isDownloaded && onDownloadRawFile != null &&
            (previewCategory == PreviewCategory.AUDIO ||
             previewCategory == PreviewCategory.PDF ||
             previewCategory == PreviewCategory.IMAGE)
        ) {
            startRawDownload()
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

    val handleMarkdownLinkClick: (String) -> Unit = { rawTarget ->
        val target = rawTarget.trim()
        if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Nie można otworzyć linku: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            var cleanTarget = target
            if (cleanTarget.lowercase().startsWith("file://")) {
                cleanTarget = cleanTarget.substring(7)
                if (cleanTarget.startsWith("localhost/")) {
                    cleanTarget = cleanTarget.substring(10)
                }
            }
            val hashIdx = cleanTarget.indexOf('#')
            if (hashIdx != -1) {
                cleanTarget = cleanTarget.substring(0, hashIdx)
            }
            cleanTarget = cleanTarget.trim()

            if (cleanTarget.isNotEmpty()) {
                val resolvedPath = resolveRelativeFilePath(currentFilePath, cleanTarget)
                pathHistory.add(currentFilePath)
                currentFilePath = resolvedPath
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val view = LocalView.current
    val density = LocalDensity.current

    val rootInsets = remember(view, configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) { ViewCompat.getRootWindowInsets(view) }
    val navBarsInsets = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())
    val navBarLeftDp = with(density) { (navBarsInsets?.left ?: 0).toDp() }
    val navBarRightDp = with(density) { (navBarsInsets?.right ?: 0).toDp() }
    val navBarBottomDp = with(density) { (navBarsInsets?.bottom ?: 0).toDp() }

    val statusBarsInsets = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())
    val statusBarTopDp = with(density) { (statusBarsInsets?.top ?: 0).toDp() }

    val navBarHeightResId = remember { context.resources.getIdentifier("navigation_bar_height", "dimen", "android") }
    val resNavBarHeightDp = if (navBarHeightResId > 0) {
        with(density) { context.resources.getDimensionPixelSize(navBarHeightResId).toDp() }
    } else 0.dp

    val navBarWidthResId = remember { context.resources.getIdentifier("navigation_bar_width", "dimen", "android") }
    val resNavBarWidthDp = if (navBarWidthResId > 0) {
        with(density) { context.resources.getDimensionPixelSize(navBarWidthResId).toDp() }
    } else 0.dp

    val statusBarResId = remember { context.resources.getIdentifier("status_bar_height", "dimen", "android") }
    val resStatusBarDp = if (statusBarResId > 0) {
        with(density) { context.resources.getDimensionPixelSize(statusBarResId).toDp() }
    } else 0.dp

    val parentNavBars = WindowInsets.navigationBars.asPaddingValues()
    val parentStatusBars = WindowInsets.statusBars.asPaddingValues()
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()

    // 1. VERTICAL INSETS: 8dp in landscape (leaves max height for content), 14dp top / 40dp above nav bar in portrait
    val topInset = if (isLandscape) 8.dp else 14.dp
    val effectiveNavBarBottom = if (isLandscape) {
        maxOf(navBarBottomDp, parentNavBars.calculateBottomPadding())
    } else {
        maxOf(navBarBottomDp, resNavBarHeightDp, parentNavBars.calculateBottomPadding(), 48.dp)
    }
    val bottomInset = if (isLandscape) {
        8.dp
    } else {
        effectiveNavBarBottom + 40.dp
    }

    // 2. HORIZONTAL INSETS: Symmetrical padding on left AND right so dialog is dead-center in landscape
    val rawStartNav = maxOf(navBarLeftDp, parentNavBars.calculateStartPadding(layoutDirection))
    val rawEndNav = maxOf(navBarRightDp, parentNavBars.calculateEndPadding(layoutDirection))

    val effectiveStartNav = if (isLandscape && rawStartNav > 0.dp) {
        maxOf(rawStartNav, resNavBarWidthDp, 48.dp)
    } else rawStartNav

    val effectiveEndNav = if (isLandscape && (rawEndNav > 0.dp || rawStartNav == 0.dp)) {
        maxOf(rawEndNav, resNavBarWidthDp, 48.dp)
    } else rawEndNav

    val safeHorizontalPad = if (isLandscape) {
        maxOf(
            effectiveStartNav,
            effectiveEndNav,
            cutoutInsets.calculateStartPadding(layoutDirection),
            cutoutInsets.calculateEndPadding(layoutDirection),
            48.dp
        ) + 16.dp
    } else {
        14.dp
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(
                    start = safeHorizontalPad,
                    end = safeHorizontalPad,
                    top = topInset,
                    bottom = bottomInset
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isLandscape) 680.dp else 1100.dp)
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
                        if (pathHistory.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    currentFilePath = pathHistory.removeAt(pathHistory.size - 1)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Wstecz",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

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
                                if (initialLine != null && initialLine > 0 && currentFilePath == filePath) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentFilePath,
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                val displaySize = if (isDir) null else fileSize ?: fileContentData?.size?.let { s ->
                                    if (s < 1024) "$s B" else if (s < 1024 * 1024) "${s / 1024} KB" else "%.1f MB".format(s / (1024.0 * 1024.0))
                                }
                                if (displaySize != null) {
                                    Text(
                                        text = " • $displaySize",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                } else if (isDir) {
                                    Text(
                                        text = " • Katalog",
                                        fontSize = 11.sp,
                                        color = AccentCyan.copy(alpha = 0.8f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zamknij",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderDark, thickness = 1.dp)

                // Main Viewer Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(BgDark)
                ) {
                    when {
                        fileContentLoading && (previewCategory == PreviewCategory.TEXT || previewCategory == PreviewCategory.MARKDOWN) -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = AccentCyan,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Odczytywanie zawartości...",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        fileContentData?.isDir == true -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
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
                                    text = currentFilePath,
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
                                            val targetFolder = fileContentData?.path?.ifBlank { null } ?: currentFilePath
                                            onOpenFolderInExplorer(targetFolder)
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

                        // Downloading Raw File state for media/binary files
                        isDownloading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = AccentCyan,
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Pobieranie pliku do podglądu...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                if (downloadProgress > 0f) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier
                                            .width(220.dp)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = AccentCyan,
                                        trackColor = BorderDark
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${(downloadProgress * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        downloadError != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
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
                                    text = "Nie udało się pobrać pliku do podglądu",
                                    color = AccentRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = downloadError!!,
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { startRawDownload() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Spróbuj ponownie", color = BgDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Rich Media Viewers
                        previewCategory == PreviewCategory.AUDIO && isDownloaded -> {
                            AudioPlayerCard(
                                cachedFile = cachedFile,
                                fileName = effectiveName,
                                fileSize = fileSize
                            )
                        }

                        previewCategory == PreviewCategory.PDF && isDownloaded -> {
                            PdfViewerCard(
                                cachedFile = cachedFile,
                                fileName = effectiveName
                            )
                        }

                        previewCategory == PreviewCategory.IMAGE && isDownloaded -> {
                            ImageViewerCard(
                                cachedFile = cachedFile,
                                fileName = effectiveName,
                                fileSize = fileSize
                            )
                        }

                        // Media files pending automatic download
                        !isDownloaded && onDownloadRawFile != null &&
                        (previewCategory == PreviewCategory.AUDIO || previewCategory == PreviewCategory.PDF || previewCategory == PreviewCategory.IMAGE) -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = AccentCyan,
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Przygotowywanie pliku do podglądu...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                if (downloadProgress > 0f) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier
                                            .width(220.dp)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = AccentCyan,
                                        trackColor = BorderDark
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${(downloadProgress * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Generic Binary or not-yet-downloaded binary
                        previewCategory == PreviewCategory.GENERIC_BINARY ||
                        ((previewCategory == PreviewCategory.AUDIO || previewCategory == PreviewCategory.PDF || previewCategory == PreviewCategory.IMAGE) && !isDownloaded) -> {
                            GenericBinaryCard(
                                fileName = effectiveName,
                                filePath = currentFilePath,
                                fileSize = fileSize ?: fileContentData?.size?.let { "$it B" },
                                mimeType = fileContentData?.mimeType,
                                isDownloaded = isDownloaded,
                                cachedFile = cachedFile,
                                onDownload = { startRawDownload() },
                                onAskAgentAboutFile = onAskAgentAboutFile?.let { fn ->
                                    { fn(currentFilePath, effectiveName) }
                                }
                            )
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            fileContentLoading = true
                                            fileContentError = null
                                            onReadFile(currentFilePath) { res ->
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
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Spróbuj ponownie", color = TextPrimary, fontSize = 13.sp, maxLines = 1, softWrap = false)
                                    }

                                    if (onOpenFolderInExplorer != null && (fileContentError?.contains("katalog", ignoreCase = true) == true || fileContentError?.contains("Error reading", ignoreCase = true) == true)) {
                                        Button(
                                            onClick = {
                                                onDismiss()
                                                onOpenFolderInExplorer(currentFilePath)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = BgDark, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Otwórz w Eksploratorze", color = BgDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }

                        // Rendered Markdown Document Viewer
                        previewCategory == PreviewCategory.MARKDOWN && isRenderedMarkdownView && fileContentData != null -> {
                            val content = fileContentData!!.content
                            val scrollState = rememberScrollState()

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
                                        text = "Dokument Markdown jest pusty (0 B)",
                                        fontSize = 13.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(16.dp)
                                ) {
                                    MarkdownText(
                                        markdown = content,
                                        textColor = TextPrimary,
                                        onLinkClick = handleMarkdownLinkClick
                                    )
                                }
                            }
                        }

                        // Text content viewer (code and raw view)
                        fileContentData != null -> {
                            val content = fileContentData!!.content
                            val isLikelyBinary = remember(content) {
                                if (content.isNotEmpty()) {
                                    val sample = content.take(2048)
                                    val hasNullByte = sample.any { it == '\u0000' }
                                    val badChars = sample.count { it == '\uFFFD' }
                                    val controlChars = sample.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
                                    hasNullByte || badChars >= 2 || (sample.length > 20 && controlChars.toFloat() / sample.length > 0.05f)
                                } else false
                            }

                            if (isLikelyBinary) {
                                GenericBinaryCard(
                                    fileName = effectiveName,
                                    filePath = currentFilePath,
                                    fileSize = fileSize ?: fileContentData?.size?.let { "$it B" },
                                    mimeType = fileContentData?.mimeType,
                                    isDownloaded = isDownloaded,
                                    cachedFile = cachedFile,
                                    onDownload = { startRawDownload() },
                                    onAskAgentAboutFile = onAskAgentAboutFile?.let { fn ->
                                        { fn(currentFilePath, effectiveName) }
                                    }
                                )
                            } else {
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
                                                val rawLine = lines[idx]
                                                val displayLine = if (rawLine.length > 2000) {
                                                    rawLine.take(2000) + " … [skrócono]"
                                                } else {
                                                    rawLine
                                                }

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
                                                        text = displayLine,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (isHighlighted) Color.White else TextPrimary,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderDark, thickness = 1.dp)

                // Dialog Bottom Actions Bar
                val actionsScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark)
                        .horizontalScroll(actionsScrollState)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Markdown view toggle (Rendered Rich vs Raw Code)
                    if (previewCategory == PreviewCategory.MARKDOWN && fileContentData != null && !fileContentLoading) {
                        OutlinedButton(
                            onClick = { isRenderedMarkdownView = !isRenderedMarkdownView },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 34.dp)
                        ) {
                            Icon(
                                imageVector = if (isRenderedMarkdownView) Icons.Default.Code else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRenderedMarkdownView) "Pokaż kod" else "Podgląd",
                                fontSize = 12.sp,
                                color = AccentCyan
                            )
                        }
                    }

                    // Copy Button (For text and markdown preview)
                    if (previewCategory == PreviewCategory.TEXT || previewCategory == PreviewCategory.MARKDOWN) {
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
                    }

                    // Open in external app button (when file is downloaded to cache)
                    if (isDownloaded && cachedFile.exists()) {
                        OutlinedButton(
                            onClick = {
                                openFileWithExternalApp(context, cachedFile, fileContentData?.mimeType)
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Otwórz w aplikacji", fontSize = 12.sp, color = AccentCyan)
                        }

                        // Save to downloads button
                        OutlinedButton(
                            onClick = {
                                val ok = saveFileToDownloads(context, cachedFile, effectiveName, fileContentData?.mimeType)
                                if (ok) {
                                    Toast.makeText(context, "Zapisano w folderze Pobrane", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Nie udało się zapisać pliku", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Zapisz w Pobranych", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    // Open Folder in Explorer Button
                    if (onOpenFolderInExplorer != null) {
                        val folderTarget = if (fileContentData?.isDir == true) {
                            currentFilePath
                        } else {
                            currentFilePath.substringBeforeLast('/', "").ifBlank {
                                currentFilePath.substringBeforeLast('\\', "").ifBlank { "." }
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

                    // Ask AI Agent about file Button
                    if (onAskAgentAboutFile != null && fileContentData?.isDir != true) {
                        Button(
                            onClick = {
                                val actualName = fileContentData?.name?.ifBlank { null } ?: effectiveName
                                onDismiss()
                                onAskAgentAboutFile(currentFilePath, actualName)
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
}

/**
 * Built-in Audio Player for mp3, wav, ogg, m4a, flac, etc.
 */
@Composable
fun AudioPlayerCard(
    cachedFile: File,
    fileName: String,
    fileSize: String?
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isPrepared by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val mediaPlayer = remember(cachedFile.absolutePath) {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                setDataSource(cachedFile.absolutePath)
                setOnPreparedListener { mp ->
                    durationMs = mp.duration
                    isPrepared = true
                }
                setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = 0
                    sliderProgress = 0f
                }
                setOnErrorListener { _, what, extra ->
                    playbackError = "Błąd odtwarzacza ($what, $extra)"
                    isPlaying = false
                    true
                }
                prepareAsync()
            } catch (e: Exception) {
                playbackError = e.localizedMessage ?: "Nie można zainicjalizować odtwarzacza"
            }
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isSeeking && isPrepared) {
                currentPositionMs = mediaPlayer.currentPosition
                sliderProgress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
            }
            delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Music Disc / Icon Artwork
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(SurfaceVariantDark)
                .border(2.dp, AccentCyan.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = fileName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (fileSize != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = fileSize,
                fontSize = 12.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }

        if (playbackError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = playbackError!!,
                color = AccentRed,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Scrubber Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderProgress.coerceIn(0f, 1f),
                onValueChange = {
                    isSeeking = true
                    sliderProgress = it
                    currentPositionMs = (it * durationMs).toInt()
                },
                onValueChangeFinished = {
                    isSeeking = false
                    if (isPrepared) {
                        mediaPlayer.seekTo(currentPositionMs)
                    }
                },
                enabled = isPrepared,
                colors = SliderDefaults.colors(
                    thumbColor = AccentCyan,
                    activeTrackColor = AccentCyan,
                    inactiveTrackColor = BorderDark
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationMs(currentPositionMs),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = formatDurationMs(durationMs),
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Playback Controls (Rewind 10s, Play/Pause, Forward 10s)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            IconButton(
                onClick = {
                    if (isPrepared) {
                        val newPos = (mediaPlayer.currentPosition - 10000).coerceAtLeast(0)
                        mediaPlayer.seekTo(newPos)
                        currentPositionMs = newPos
                        sliderProgress = if (durationMs > 0) newPos.toFloat() / durationMs.toFloat() else 0f
                    }
                },
                enabled = isPrepared,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "-10s",
                    tint = if (isPrepared) TextSecondary else TextMuted,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = {
                    if (isPrepared) {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    }
                },
                enabled = isPrepared,
                modifier = Modifier
                    .size(58.dp)
                    .background(if (isPrepared) AccentCyan else BorderDark, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pauza" else "Odtwórz",
                    tint = BgDark,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = {
                    if (isPrepared) {
                        val newPos = (mediaPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                        mediaPlayer.seekTo(newPos)
                        currentPositionMs = newPos
                        sliderProgress = if (durationMs > 0) newPos.toFloat() / durationMs.toFloat() else 0f
                    }
                },
                enabled = isPrepared,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "+10s",
                    tint = if (isPrepared) TextSecondary else TextMuted,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Built-in PDF Viewer using Android's native PdfRenderer
 */
@Composable
fun PdfViewerCard(
    cachedFile: File,
    fileName: String
) {
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pdfError by remember { mutableStateOf<String?>(null) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPage by remember { mutableStateOf(false) }

    DisposableEffect(cachedFile.absolutePath) {
        var pfd: ParcelFileDescriptor? = null
        var r: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            r = PdfRenderer(pfd)
            renderer = r
            pageCount = r.pageCount
        } catch (e: Exception) {
            pdfError = "Błąd otwierania PDF: ${e.localizedMessage}"
        }

        onDispose {
            try {
                r?.close()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(currentPageIndex, renderer) {
        val currentRenderer = renderer
        if (currentRenderer != null && pageCount > 0) {
            isRenderingPage = true
            withContext(Dispatchers.IO) {
                try {
                    val page = currentRenderer.openPage(currentPageIndex)
                    // High-resolution rendering for crisp readability
                    val densityMultiplier = 2
                    val bmp = Bitmap.createBitmap(
                        (page.width * densityMultiplier).coerceAtLeast(1),
                        (page.height * densityMultiplier).coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pageBitmap = bmp
                } catch (e: Exception) {
                    pdfError = "Błąd renderowania strony: ${e.localizedMessage}"
                } finally {
                    isRenderingPage = false
                }
            }
        }
    }

    if (pdfError != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRed, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = pdfError!!, color = AccentRed, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // PDF Page Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariantDark)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Poprzednia strona",
                        tint = if (currentPageIndex > 0) TextPrimary else TextMuted
                    )
                }

                Text(
                    text = if (pageCount > 0) "Strona ${currentPageIndex + 1} z $pageCount" else "Ładowanie dokumentu...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                IconButton(
                    onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex++ },
                    enabled = currentPageIndex < pageCount - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Następna strona",
                        tint = if (currentPageIndex < pageCount - 1) TextPrimary else TextMuted
                    )
                }
            }

            val pdfScrollState = rememberScrollState()
            LaunchedEffect(currentPageIndex) {
                try {
                    pdfScrollState.scrollTo(0)
                } catch (_: Exception) {}
            }

            // Document Display Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(pdfScrollState),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isRenderingPage && pageBitmap == null) {
                    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp)
                    }
                } else pageBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Strona ${currentPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

/**
 * Built-in Image Viewer for png, jpg, jpeg, webp, gif, bmp
 */
@Composable
fun ImageViewerCard(
    cachedFile: File,
    fileName: String,
    fileSize: String?
) {
    val bitmap = remember(cachedFile.absolutePath) {
        try {
            BitmapFactory.decodeFile(cachedFile.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${bitmap.width} × ${bitmap.height} px" + if (fileSize != null) " • $fileSize" else "",
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = fileName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Plik graficzny (format wektorowy lub animowany)", fontSize = 12.sp, color = TextMuted)
        }
    }
}

/**
 * Universal Binary File Card with Actions
 */
@Composable
fun GenericBinaryCard(
    fileName: String,
    filePath: String,
    fileSize: String?,
    mimeType: String?,
    isDownloaded: Boolean,
    cachedFile: File,
    onDownload: () -> Unit,
    onAskAgentAboutFile: (() -> Unit)?
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = getFileIcon(fileName),
            contentDescription = null,
            tint = getFileIconColor(fileName),
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = fileName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Plik binarny",
            fontSize = 11.sp,
            color = AccentCyan,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = (fileSize ?: "") + (if (mimeType != null) " • $mimeType" else ""),
            fontSize = 12.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!isDownloaded) {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = BgDark, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pobierz plik do podglądu", color = BgDark, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { openFileWithExternalApp(context, cachedFile, mimeType) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = BgDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Otwórz w aplikacji", color = BgDark, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val ok = saveFileToDownloads(context, cachedFile, fileName, mimeType)
                        if (ok) {
                            Toast.makeText(context, "Zapisano w Pobranych", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Nie udało się zapisać pliku", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Zapisz", color = TextSecondary)
                }
            }
        }

        if (onAskAgentAboutFile != null) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onAskAgentAboutFile,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
            ) {
                Icon(imageVector = Icons.Default.SmartToy, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Zapytaj agenta o ten plik", color = AccentCyan)
            }
        }
    }
}
