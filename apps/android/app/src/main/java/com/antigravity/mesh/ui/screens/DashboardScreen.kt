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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.R
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.components.NodeCard
import com.antigravity.mesh.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DashboardFilter { ALL, ONLINE, PINNED }

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
    onAddManualNode: (host: String, port: Int, pinOrToken: String?, onComplete: (Result<MeshNode>) -> Unit) -> Unit = { _, _, _, _ -> },
    onDeleteNode: (MeshNode) -> Unit = {},
    onRenameNode: (nodeId: String, newName: String?) -> Unit = { _, _ -> },
    onUpdateNodeDetails: (nodeId: String, newName: String?, newHost: String?, newPort: Int?) -> Unit = { id, name, _, _ -> onRenameNode(id, name) },
    onTogglePinNode: (MeshNode) -> Unit = {},
    onNodeFilesClick: (MeshNode) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Auto-refresh cluster metrics every 4 seconds while dashboard is visible & active
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            var isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> isResumed = true
                    Lifecycle.Event.ON_PAUSE -> isResumed = false
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
            val refreshJob = scope.launch {
                while (isActive) {
                    delay(4000L)
                    if (isResumed && !isScanning) {
                        onRefreshAll()
                    }
                }
            }
            onDispose {
                refreshJob.cancel()
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        var showAddDialog by remember { mutableStateOf(false) }
        var nodeToRename by remember { mutableStateOf<MeshNode?>(null) }
        var nodeToDelete by remember { mutableStateOf<MeshNode?>(null) }
        var manualHost by remember { mutableStateOf("") }
        var manualPort by remember { mutableStateOf("8888") }
        var manualPinOrToken by remember { mutableStateOf("") }
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

            IconButton(
                onClick = {
                    if (hasUpdateAvailable) onOpenUpdateDialog() else onCheckUpdates()
                }
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
        }

        // Update Announcement Banner (if available)
        if (hasUpdateAvailable && updateVersion != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenUpdateDialog),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Dostępna nowa wersja: v$updateVersion",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onOpenUpdateDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = BgDark),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 28.dp)
                    ) {
                        Text("Aktualizuj", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        var searchQuery by rememberSaveable { mutableStateOf("") }
        var selectedFilter by rememberSaveable { mutableStateOf(DashboardFilter.ALL) }

        if (nodes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Szukaj maszyn po nazwie lub IP...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Wyczyść", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceVariantDark,
                    unfocusedContainerColor = SurfaceVariantDark,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == DashboardFilter.ALL,
                    onClick = { selectedFilter = DashboardFilter.ALL },
                    label = { Text("Wszystkie (${nodes.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan,
                        selectedLabelColor = BgDark,
                        containerColor = SurfaceDark,
                        labelColor = TextSecondary
                    )
                )
                FilterChip(
                    selected = selectedFilter == DashboardFilter.ONLINE,
                    onClick = { selectedFilter = DashboardFilter.ONLINE },
                    label = { Text("Online (${nodes.count { it.isOnline }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentGreen,
                        selectedLabelColor = BgDark,
                        containerColor = SurfaceDark,
                        labelColor = TextSecondary
                    )
                )
                FilterChip(
                    selected = selectedFilter == DashboardFilter.PINNED,
                    onClick = { selectedFilter = DashboardFilter.PINNED },
                    label = { Text("Przypięte (${nodes.count { it.isPinned }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan,
                        selectedLabelColor = BgDark,
                        containerColor = SurfaceDark,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val filteredNodes = remember(nodes, searchQuery, selectedFilter) {
            nodes.distinctBy { it.id }
                .filter { node ->
                    val matchesFilter = when (selectedFilter) {
                        DashboardFilter.ALL -> true
                        DashboardFilter.ONLINE -> node.isOnline
                        DashboardFilter.PINNED -> node.isPinned
                    }
                    val matchesQuery = searchQuery.isBlank() ||
                        node.displayName.contains(searchQuery, ignoreCase = true) ||
                        node.name.contains(searchQuery, ignoreCase = true) ||
                        node.host.contains(searchQuery, ignoreCase = true) ||
                        (node.customName?.contains(searchQuery, ignoreCase = true) == true)
                    matchesFilter && matchesQuery
                }
                .sortedWith(
                    compareByDescending<MeshNode> { it.isPinned }
                        .thenByDescending { it.isOnline }
                        .thenBy { it.displayName.lowercase() }
                )
        }

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
            } else if (filteredNodes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Brak pasujących maszyn",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Żadne urządzenie nie spełnia wybranych filtrów ani frazy wyszukiwania.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(filteredNodes, key = { it.id }) { node ->
                val nodeMessages = chatHistories[node.id] ?: emptyList()
                val lastMessage = nodeMessages.lastOrNull()
                NodeCard(
                    node = node,
                    lastMessage = lastMessage,
                    onChatClick = onNodeChat,
                    onRefreshClick = onNodeRefresh,
                    onFilesClick = onNodeFilesClick,
                    onDeleteClick = { nodeToDelete = it },
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
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Skanowanie…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(imageVector = Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Skanuj LAN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "💡 Maszyny z Tailscale lub VPN (100.x.y.z) dodaj za pomocą „Dodaj ręcznie”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
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
                        OutlinedTextField(
                            value = manualPinOrToken,
                            onValueChange = { manualPinOrToken = it; addNodeError = null },
                            label = { Text("PIN lub token (opcjonalnie)") },
                            placeholder = { Text("np. 4-cyfrowy PIN z menu komputera") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Text(
                            text = "💡 Jeśli nie podasz PIN-u, na ekranie komputera pojawi się okno z prośbą o zatwierdzenie połączenia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
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
                            val rawInput = manualHost.trim()
                            if (rawInput.isBlank()) {
                                addNodeError = "Wprowadź poprawny adres hosta lub IP"
                                return@Button
                            }

                            var clean = rawInput
                            if (clean.startsWith("http://", ignoreCase = true)) clean = clean.substring(7)
                            if (clean.startsWith("https://", ignoreCase = true)) clean = clean.substring(8)
                            clean = clean.trimEnd('/')

                            var parsedHost = clean
                            var parsedPort = manualPort.trim().toIntOrNull() ?: 8888

                            if (clean.contains(":")) {
                                parsedHost = clean.substringBefore(":").trim()
                                val portFromHost = clean.substringAfter(":").trim().toIntOrNull()
                                if (portFromHost != null) {
                                    parsedPort = portFromHost
                                }
                            }

                            if (parsedHost.isBlank() || parsedHost.contains(" ") || parsedHost.contains("/")) {
                                addNodeError = "Nieprawidłowy format hosta (np. 192.168.1.50 lub moj-mac.local)"
                                return@Button
                            }

                            if (parsedPort !in 1..65535) {
                                addNodeError = "Port musi być liczbą z zakresu 1-65535"
                                return@Button
                            }

                            isAddingNode = true
                            addNodeError = null
                            val pinParam = manualPinOrToken.trim().ifBlank { null }
                            onAddManualNode(parsedHost, parsedPort, pinParam) { result ->
                                isAddingNode = false
                                result.onSuccess {
                                    showAddDialog = false
                                    manualHost = ""
                                    manualPort = "8888"
                                    manualPinOrToken = ""
                                }.onFailure { err ->
                                    val msg = err.localizedMessage?.takeIf { it.isNotBlank() }
                                        ?: err.message?.takeIf { it.isNotBlank() }
                                        ?: "Nie można połączyć z węzłem"
                                    addNodeError = msg
                                }
                            }
                        },
                        enabled = !isAddingNode,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                    ) {
                        if (isAddingNode) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BgDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Autoryzacja…", color = BgDark)
                        } else {
                            Text("Połącz i sparuj", color = BgDark, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            isAddingNode = false
                            addNodeError = null
                            manualPinOrToken = ""
                        }
                    ) {
                        Text("Anuluj", color = TextSecondary)
                    }
                }
            )
        }

        // Edit Node Details Dialog
        nodeToRename?.let { targetNode ->
            var renameText by remember(targetNode) { mutableStateOf(targetNode.customName ?: "") }
            var editHostText by remember(targetNode) { mutableStateOf(targetNode.host) }
            var editPortText by remember(targetNode) { mutableStateOf(targetNode.port.toString()) }
            var editError by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { nodeToRename = null },
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = "Edycja urządzenia",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Oryginalna nazwa: ${targetNode.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Własna nazwa (opcjonalnie)") },
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
                        OutlinedTextField(
                            value = editHostText,
                            onValueChange = { editHostText = it; editError = null },
                            label = { Text("Adres hosta / IP") },
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
                            value = editPortText,
                            onValueChange = { editPortText = it; editError = null },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        if (editError != null) {
                            Text(
                                text = editError!!,
                                color = AccentRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanHost = editHostText.trim()
                            val cleanPort = editPortText.trim().toIntOrNull()
                            if (cleanHost.isBlank()) {
                                editError = "Host nie może być pusty"
                                return@Button
                            }
                            if (cleanPort == null || cleanPort !in 1..65535) {
                                editError = "Port musi być w zakresie 1-65535"
                                return@Button
                            }
                            onUpdateNodeDetails(
                                targetNode.id,
                                renameText.trim().ifBlank { null },
                                cleanHost,
                                cleanPort
                            )
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
                                    onUpdateNodeDetails(targetNode.id, null, targetNode.host, targetNode.port)
                                    nodeToRename = null
                                }
                            ) {
                                Text("Domyślna nazwa", color = TextMuted)
                            }
                        }
                        TextButton(onClick = { nodeToRename = null }) {
                            Text("Anuluj", color = TextSecondary)
                        }
                    }
                }
            )
        }

        // Delete Node Confirmation Dialog
        nodeToDelete?.let { targetNode ->
            AlertDialog(
                onDismissRequest = { nodeToDelete = null },
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = "Usunąć urządzenie?",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Czy na pewno chcesz usunąć urządzenie „${targetNode.displayName}” (${targetNode.host}) z klastra?\n\nHistoria czatu z tą maszyną również zostanie skasowana.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteNode(targetNode)
                            nodeToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("Usuń", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { nodeToDelete = null }) {
                        Text("Anuluj", color = TextSecondary)
                    }
                }
            )
        }
    }
}
