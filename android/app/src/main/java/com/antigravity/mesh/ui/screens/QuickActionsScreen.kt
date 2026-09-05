package com.antigravity.mesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.theme.*

data class QuickAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val getCommand: (isWindows: Boolean) -> String
)

@Composable
fun QuickActionsScreen(
    nodes: List<MeshNode>,
    selectedNodeId: String,
    onSelectNode: (String) -> Unit,
    onRunCommand: (String, String) -> Unit,
    lastCommandOutput: String?,
    isExecuting: Boolean
) {
    val selectedNode = nodes.find { it.id == selectedNodeId } ?: nodes.firstOrNull()
    val isWin = selectedNode?.platform?.contains("Windows", ignoreCase = true) == true

    val actions = listOf(
        QuickAction(
            title = "Zajętość dysków",
            description = "Pobierz szczegółowy stan partycji i wolne gigabajty",
            icon = Icons.Default.Storage,
            getCommand = { win -> if (win) "wmic logicaldisk get caption,freespace,size" else "df -h" }
        ),
        QuickAction(
            title = "Procesy o najwyższym RAM",
            description = "Wykryj programy najbardziej obciążające pamięć",
            icon = Icons.Default.Memory,
            getCommand = { win ->
                if (win) "powershell -Command \"Get-Process | Sort-Object WorkingSet64 -Descending | Select-Object -First 5 ProcessName, @{Name='RAM (MB)';Expression={[math]::Round($_.WorkingSet64 / 1MB, 1)}}\""
                else "ps aux | sort -nrk 4 | head -n 6"
            }
        ),
        QuickAction(
            title = "Status projektów Git",
            description = "Sprawdź gałąź i niezapisane zmiany w repozytorium",
            icon = Icons.Default.Source,
            getCommand = { _ -> "git status" }
        ),
        QuickAction(
            title = "Wersje środowisk (Node/Python/Rust)",
            description = "Sprawdź dostępne kompilatory i wersje",
            icon = Icons.Default.Code,
            getCommand = { win -> if (win) "rustc --version & python --version" else "rustc --version && python3 --version" }
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Text(
            text = "Szybkie Akcje",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Węzeł: ${selectedNode?.name ?: "Brak wyboru"}",
            style = MaterialTheme.typography.bodySmall,
            color = AccentCyan
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action items
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(actions.size) { idx ->
                val a = actions[idx]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isExecuting && selectedNode != null) {
                            if (selectedNode != null) {
                                onRunCommand(selectedNode.id, a.getCommand(isWin))
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = a.icon, contentDescription = null, tint = AccentCyan)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = a.title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = a.description, fontSize = 12.sp, color = TextSecondary)
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }
        }

        // Terminal Output Box
        if (isExecuting || lastCommandOutput != null) {
            Text(
                text = "Wynik z konsoli:",
                style = MaterialTheme.typography.labelMedium,
                color = AccentCyan,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentCyan
                    )
                } else if (lastCommandOutput != null) {
                    Text(
                        text = lastCommandOutput,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
