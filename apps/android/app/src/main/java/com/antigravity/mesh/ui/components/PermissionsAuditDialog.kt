package com.antigravity.mesh.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.antigravity.mesh.data.*
import com.antigravity.mesh.ui.theme.*

@Composable
fun PermissionsAuditDialog(
    node: MeshNode,
    report: PermissionAuditReport?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onFixAction: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val rootInsets = remember(view) { ViewCompat.getRootWindowInsets(view) }
    val navBarPx = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    val navBarDp = with(density) { navBarPx.toDp() }
    val statusBarPx = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    val statusBarDp = with(density) { statusBarPx.toDp() }

    val navBarResId = remember { context.resources.getIdentifier("navigation_bar_height", "dimen", "android") }
    val resNavBarDp = if (navBarResId > 0) {
        with(density) { context.resources.getDimensionPixelSize(navBarResId).toDp() }
    } else {
        0.dp
    }
    val statusBarResId = remember { context.resources.getIdentifier("status_bar_height", "dimen", "android") }
    val resStatusBarDp = if (statusBarResId > 0) {
        with(density) { context.resources.getDimensionPixelSize(statusBarResId).toDp() }
    } else {
        0.dp
    }

    val parentNavBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val parentSystemBarsBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val parentStatusBarsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val startInset = maxOf(14.dp, cutoutInsets.calculateStartPadding(layoutDirection))
    val endInset = maxOf(14.dp, cutoutInsets.calculateEndPadding(layoutDirection))

    val effectiveNavBar = maxOf(navBarDp, resNavBarDp, parentNavBarsBottom, parentSystemBarsBottom, 48.dp)
    val effectiveStatusBar = maxOf(statusBarDp, resStatusBarDp, parentStatusBarsTop, 24.dp)
    val bottomInset = effectiveNavBar + 16.dp
    val topInset = effectiveStatusBar + 8.dp

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
                    start = startInset,
                    end = endInset,
                    top = topInset,
                    bottom = bottomInset
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, AntigravityCardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceVariantDark)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AccentCyan.copy(alpha = 0.15f))
                                    .border(1.dp, AccentCyan.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Audyt Uprawnień i Diagnostyka",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${node.displayName} (${node.platform})",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Content
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            isLoading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = AccentCyan,
                                        modifier = Modifier.size(42.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Wykonywanie audytu środowiska węzła...",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Weryfikacja macOS TCC, uprawnień dysku, sygnatury i CLI",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            errorMessage != null -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Nie udało się pobrać audytu uprawnień",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = errorMessage,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Button(
                                        onClick = onRefresh,
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                                    ) {
                                        Text("Spróbuj ponownie", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            report != null -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Status Summary Banner
                                    item {
                                        val isAllGood = report.allGranted || report.overallStatus == "all_granted"
                                        val isWarn = report.overallStatus == "warnings"

                                        val bannerBg = when {
                                            isAllGood -> AccentGreen.copy(alpha = 0.12f)
                                            isWarn -> AccentAmber.copy(alpha = 0.12f)
                                            else -> AccentRed.copy(alpha = 0.12f)
                                        }
                                        val bannerBorder = when {
                                            isAllGood -> AccentGreen.copy(alpha = 0.45f)
                                            isWarn -> AccentAmber.copy(alpha = 0.45f)
                                            else -> AccentRed.copy(alpha = 0.45f)
                                        }
                                        val bannerIcon = when {
                                            isAllGood -> Icons.Default.CheckCircle
                                            isWarn -> Icons.Default.Warning
                                            else -> Icons.Default.ErrorOutline
                                        }
                                        val bannerColor = when {
                                            isAllGood -> AccentGreen
                                            isWarn -> AccentAmber
                                            else -> AccentRed
                                        }
                                        val statusTitle = when {
                                            isAllGood -> "Wszystkie uprawnienia nadane"
                                            isWarn -> "Węzeł działa z ostrzeżeniami"
                                            else -> "Wymagana akcja w systemie"
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(bannerBg)
                                                .border(1.dp, bannerBorder, RoundedCornerShape(12.dp))
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = bannerIcon,
                                                contentDescription = null,
                                                tint = bannerColor,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = statusTitle,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = report.summary.ifBlank { "Audyt przeprowadzony pomyślnie." },
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }

                                    // System Core Permissions Card
                                    item {
                                        AuditSectionCard(title = "Uprawnienia Systemowe (OS / TCC)") {
                                            // Accessibility
                                            AuditCheckRow(
                                                label = "Dostępność (Accessibility)",
                                                isOk = report.accessibility.granted,
                                                statusBadge = if (report.accessibility.granted) "Aktywne" else "Brak",
                                                message = report.accessibility.message,
                                                actions = if (!report.accessibility.granted && onFixAction != null) {
                                                    {
                                                        OutlinedButton(
                                                            onClick = { onFixAction("open_accessibility") },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentIndigo)
                                                        ) {
                                                            Text("Napraw: Otwórz w macOS", fontSize = 10.sp, color = AccentIndigo)
                                                        }
                                                    }
                                                } else null
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderDark)

                                            // Full Disk Access
                                            val fdaOk = report.fullDiskAccess.granted || report.fullDiskAccess.status == "not_applicable"
                                            AuditCheckRow(
                                                label = "Pełny dostęp do dysku (FDA)",
                                                isOk = fdaOk,
                                                statusBadge = if (report.fullDiskAccess.granted) "Aktywny" else if (report.fullDiskAccess.status == "not_applicable") "N/D" else "Brak",
                                                message = report.fullDiskAccess.message,
                                                actions = if (!report.fullDiskAccess.granted && report.fullDiskAccess.status != "not_applicable" && onFixAction != null) {
                                                    {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            OutlinedButton(
                                                                onClick = { onFixAction("open_fda") },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentIndigo)
                                                            ) {
                                                                Text("Otwórz FDA", fontSize = 10.sp, color = AccentIndigo)
                                                            }
                                                            OutlinedButton(
                                                                onClick = { onFixAction("reveal_in_finder") },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight)
                                                            ) {
                                                                Text("Pokaż w Finderze", fontSize = 10.sp, color = TextPrimary)
                                                            }
                                                        }
                                                    }
                                                } else null
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderDark)

                                            // Codesign
                                            val signOk = !report.codesign.quarantineActive && report.codesign.valid
                                            AuditCheckRow(
                                                label = "Podpis cyfrowy & Kwarantanna",
                                                isOk = signOk,
                                                statusBadge = if (report.codesign.quarantineActive) "Kwarantanna!" else if (report.codesign.valid) "Poprawny" else "Ad-hoc",
                                                message = report.codesign.message,
                                                actions = if (report.codesign.quarantineActive && onFixAction != null) {
                                                    {
                                                        OutlinedButton(
                                                            onClick = { onFixAction("remove_quarantine") },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed)
                                                        ) {
                                                            Text("Usuń kwarantannę", fontSize = 10.sp, color = AccentRed)
                                                        }
                                                    }
                                                } else null
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderDark)

                                            // Process Execution
                                            AuditCheckRow(
                                                label = "Wykonywanie procesów potomnych",
                                                isOk = report.processExecution.canSpawn,
                                                statusBadge = if (report.processExecution.canSpawn) "${report.processExecution.latencyMs} ms" else "Błąd",
                                                message = report.processExecution.message
                                            )
                                        }
                                    }

                                    // Filesystem Access Card
                                    item {
                                        AuditSectionCard(title = "Dostęp do Ścieżek i Katalogów") {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                report.filesystem.paths.forEach { pathItem ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = pathItem.name,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = TextPrimary
                                                            )
                                                            Text(
                                                                text = pathItem.path,
                                                                fontSize = 10.sp,
                                                                color = TextMuted,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                            if (pathItem.error != null) {
                                                                Text(
                                                                    text = pathItem.error,
                                                                    fontSize = 10.sp,
                                                                    color = AccentRed
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        val (badgeText, badgeColor) = when {
                                                            pathItem.writable -> "R/W (Zapis)" to AccentGreen
                                                            pathItem.readable -> "Tylko odczyt" to AccentAmber
                                                            else -> "Brak dostępu" to AccentRed
                                                        }
                                                        StatusBadge(text = badgeText, color = badgeColor)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Toolchains & CLI Card
                                    item {
                                        AuditSectionCard(title = "Narzędzia CLI i Środowisko Programistyczne") {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                report.toolchains.items.forEach { tool ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = tool.name,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = TextPrimary
                                                            )
                                                            if (tool.version != null) {
                                                                Text(
                                                                    text = tool.version,
                                                                    fontSize = 10.sp,
                                                                    color = TextSecondary,
                                                                    fontFamily = FontFamily.Monospace
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        StatusBadge(
                                                            text = if (tool.found) "Dostępny" else "Niedostępny",
                                                            color = if (tool.found) AccentGreen else TextMuted
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Network Diagnostic Card
                                    item {
                                        AuditSectionCard(title = "Sieć i Łączność Węzła") {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = "Port nasłuchu", fontSize = 12.sp, color = TextSecondary)
                                                    Text(text = "${report.network.listenPort}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = "Tailscale Mesh IP", fontSize = 12.sp, color = TextSecondary)
                                                    if (report.network.tailscaleIp != null) {
                                                        Text(
                                                            text = report.network.tailscaleIp,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = AccentCyan,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    } else {
                                                        StatusBadge(text = "Nie wykryto", color = AccentAmber)
                                                    }
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = "Dostęp do Internetu", fontSize = 12.sp, color = TextSecondary)
                                                    val pingText = if (report.network.pingMs != null) "Tak (${report.network.pingMs} ms)" else if (report.network.internetConnectivity) "Tak" else "Brak"
                                                    StatusBadge(
                                                        text = pingText,
                                                        color = if (report.network.internetConnectivity) AccentGreen else AccentRed
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Recommendations Card
                                    if (report.recommendations.isNotEmpty()) {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(AccentAmber.copy(alpha = 0.08f))
                                                    .border(1.dp, AccentAmber.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                                    .padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = AccentAmber,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Zalecenia konfiguracyjne (${report.recommendations.size})",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = AccentAmber
                                                    )
                                                }

                                                report.recommendations.forEach { rec ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(SurfaceDark.copy(alpha = 0.5f))
                                                            .padding(10.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = rec,
                                                            fontSize = 12.sp,
                                                            color = TextPrimary,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            if (onFixAction != null) {
                                                                if (rec.contains("Dostępności")) {
                                                                    OutlinedButton(
                                                                        onClick = { onFixAction("open_accessibility") },
                                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(26.dp),
                                                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentIndigo)
                                                                    ) {
                                                                        Text("Napraw", fontSize = 10.sp, color = AccentIndigo)
                                                                    }
                                                                } else if (rec.contains("Pełny dostęp do dysku")) {
                                                                    OutlinedButton(
                                                                        onClick = { onFixAction("open_fda") },
                                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(26.dp),
                                                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentIndigo)
                                                                    ) {
                                                                        Text("Otwórz FDA", fontSize = 10.sp, color = AccentIndigo)
                                                                    }
                                                                } else if (rec.contains("kwarantanny")) {
                                                                    OutlinedButton(
                                                                        onClick = { onFixAction("remove_quarantine") },
                                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(26.dp),
                                                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed)
                                                                    ) {
                                                                        Text("Usuń", fontSize = 10.sp, color = AccentRed)
                                                                    }
                                                                }
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Recommendation", rec))
                                                                    Toast.makeText(context, "Skopiowano do schowka", Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.ContentCopy,
                                                                    contentDescription = "Kopiuj",
                                                                    tint = AccentCyan,
                                                                    modifier = Modifier.size(15.dp)
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
                        }
                    }

                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceVariantDark)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onRefresh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Uruchom test ponownie", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                        ) {
                            Text("Zamknij", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = AccentCyan,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun AuditCheckRow(
    label: String,
    isOk: Boolean,
    statusBadge: String,
    message: String,
    actions: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextPrimary)
            StatusBadge(
                text = statusBadge,
                color = if (isOk) AccentGreen else AccentAmber
            )
        }
        if (message.isNotBlank()) {
            Text(
                text = message,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (actions != null) {
            Box(modifier = Modifier.padding(top = 6.dp)) {
                actions()
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
