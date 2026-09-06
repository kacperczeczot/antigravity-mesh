package com.antigravity.mesh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.R
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.components.NodeCard
import com.antigravity.mesh.ui.theme.*

@Composable
fun DashboardScreen(
    nodes: List<MeshNode>,
    chatHistories: Map<String, List<ChatMessage>> = emptyMap(),
    isScanning: Boolean,
    onRefreshAll: () -> Unit,
    onScanAndPair: () -> Unit,
    onNodeChat: (MeshNode) -> Unit,
    onNodeRefresh: (MeshNode) -> Unit,
    hasUpdateAvailable: Boolean = false,
    updateVersion: String? = null,
    onCheckUpdates: () -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
    onAddManualNode: (host: String, port: Int, onComplete: (Result<MeshNode>) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteNode: (MeshNode) -> Unit = {},
    onRenameNode: (nodeId: String, newName: String?) -> Unit = { _, _ -> },
    onTogglePinNode: (MeshNode) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        var showAddDialog by remember { mutableStateOf(false) }
        var nodeToRename by remember { mutableStateOf<MeshNode?>(null) }
        var manualHost by remember { mutableStateOf("") }
        var manualPort by remember { mutableStateOf("8888") }
        var isAddingNode by remember { mutableStateOf(false) }
        var addNodeError by remember { mutableStateOf<String?>(null) }

        // Top Cluster Overview Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher),
                    contentDescription = "Antigravity Mesh Logo",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Antigravity Mesh",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            brush = AntigravityGradient
                        ),
                        fontWeight = FontWeight.Black
                    )
                    val onlineCount = nodes.count { it.isOnline }
                    Text(
                        text = "Aktywne węzły: $onlineCount / ${nodes.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentCyan
                    )
                }
            }

            Row {
                IconButton(
                    onClick = {
                        if (hasUpdateAvailable) onOpenUpdateDialog() else onCheckUpdates()
                    },
                    modifier = Modifier
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .background(SurfaceDark, RoundedCornerShape(12.dp))
                ) {
                    BadgedBox(
                        badge = {
                            if (hasUpdateAvailable) {
                                Badge(containerColor = AccentGreen)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Aktualizacje",
                            tint = if (hasUpdateAvailable) AccentGreen else TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onScanAndPair,
                    enabled = !isScanning,
                    modifier = Modifier
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .background(SurfaceDark, RoundedCornerShape(12.dp))
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentCyan)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Skanuj LAN",
                            tint = AccentCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .background(SurfaceDark, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = "Dodaj węzeł ręcznie",
                        tint = AccentCyan
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRefreshAll,
                    modifier = Modifier
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .background(SurfaceDark, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Odśwież wszystko",
                        tint = TextSecondary
                    )
                }
            }
        }

        // Update Announcement Banner (if available)
        if (hasUpdateAvailable && updateVersion != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.12f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Dostępna nowa wersja v$updateVersion",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Dotknij, aby zainstalować z GitHub Releases",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onOpenUpdateDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = BgDark),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Aktualizuj", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Nodes List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (nodes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = AccentCyan
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Brak skonfigurowanych węzłów",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Uruchom Antigravity Mesh na komputerze w tej samej sieci Wi-Fi, a następnie kliknij przycisk poniżej, aby automatycznie wykryć węzły.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            val sortedNodes = remember(nodes) {
                nodes.sortedWith(
                    compareByDescending<MeshNode> { it.isPinned }
                        .thenByDescending { it.isOnline }
                        .thenBy { it.displayName.lowercase() }
                )
            }

            items(sortedNodes, key = { it.id }) { node ->
                val nodeMessages = chatHistories[node.id] ?: emptyList()
                val lastMessage = nodeMessages.lastOrNull()
                NodeCard(
                    node = node,
                    lastMessage = lastMessage,
                    onChatClick = onNodeChat,
                    onRefreshClick = onNodeRefresh,
                    onDeleteClick = onDeleteNode,
                    onRenameClick = { nodeToRename = it },
                    onTogglePinClick = onTogglePinNode
                )
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onScanAndPair,
                        enabled = !isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderDark),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceDark,
                            contentColor = AccentCyan
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Skanuj LAN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AntigravityButtonGradient)
                            .clickable { showAddDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dodaj ręcznie", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { if (!isAddingNode) showAddDialog = false },
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = "Dodaj węzeł ręcznie",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Wprowadź adres IP lub nazwę hosta (np. adres Tailscale 100.x.y.z):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = manualHost,
                            onValueChange = { manualHost = it; addNodeError = null },
                            label = { Text("Adres IP lub host") },
                            placeholder = { Text("np. 100.95.177.97") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it.filter { ch -> ch.isDigit() }; addNodeError = null },
                            label = { Text("Port") },
                            placeholder = { Text("8888") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        if (addNodeError != null) {
                            Text(
                                text = addNodeError!!,
                                color = AccentRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (manualHost.isBlank()) {
                                addNodeError = "Wprowadź poprawny adres hosta lub IP"
                                return@Button
                            }
                            val portInt = manualPort.toIntOrNull() ?: 8888
                            isAddingNode = true
                            addNodeError = null
                            onAddManualNode(manualHost.trim(), portInt) { result ->
                                isAddingNode = false
                                result.onSuccess {
                                    showAddDialog = false
                                    manualHost = ""
                                    manualPort = "8888"
                                }.onFailure { err ->
                                    addNodeError = "Błąd połączenia: ${err.localizedMessage}"
                                }
                            }
                        },
                        enabled = !isAddingNode,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                    ) {
                        if (isAddingNode) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BgDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Łączenie…", color = BgDark)
                        } else {
                            Text("Połącz i sparuj", color = BgDark, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddDialog = false },
                        enabled = !isAddingNode
                    ) {
                        Text("Anuluj", color = TextSecondary)
                    }
                }
            )
        }

        // Rename Node Dialog
        nodeToRename?.let { targetNode ->
            var renameText by remember(targetNode) { mutableStateOf(targetNode.customName ?: targetNode.name) }

            AlertDialog(
                onDismissRequest = { nodeToRename = null },
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = "Zmień nazwę urządzenia",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Wprowadź własną nazwę dla tego urządzenia. Oryginalna nazwa w sieci: ${targetNode.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Własna nazwa") },
                            placeholder = { Text(targetNode.name) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRenameNode(targetNode.id, renameText.trim())
                            nodeToRename = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                    ) {
                        Text("Zapisz", color = BgDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        if (!targetNode.customName.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    onRenameNode(targetNode.id, null)
                                    nodeToRename = null
                                }
                            ) {
                                Text("Przywróć domyślną", color = TextMuted)
                            }
                        }
                        TextButton(onClick = { nodeToRename = null }) {
                            Text("Anuluj", color = TextSecondary)
                        }
                    }
                }
            )
        }
    }
}
