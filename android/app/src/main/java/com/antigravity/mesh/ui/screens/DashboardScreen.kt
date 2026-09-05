package com.antigravity.mesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.components.NodeCard
import com.antigravity.mesh.ui.theme.*

@Composable
fun DashboardScreen(
    nodes: List<MeshNode>,
    isScanning: Boolean,
    onRefreshAll: () -> Unit,
    onScanAndPair: () -> Unit,
    onNodeChat: (MeshNode) -> Unit,
    onNodeRefresh: (MeshNode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Cluster Overview Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Antigravity Mesh",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                val onlineCount = nodes.count { it.isOnline }
                Text(
                    text = "Aktywne węzły: $onlineCount / ${nodes.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentCyan
                )
            }

            Row {
                IconButton(
                    onClick = onScanAndPair,
                    enabled = !isScanning,
                    modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp))
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
                    onClick = onRefreshAll,
                    modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Odśwież wszystko",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Nodes List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(nodes) { node ->
                NodeCard(
                    node = node,
                    onChatClick = onNodeChat,
                    onRefreshClick = onNodeRefresh
                )
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onScanAndPair,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wykryj i sparuj nowe węzły w Wi-Fi")
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
