package com.antigravity.mesh.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.theme.*

@Composable
fun NodeCard(
    node: MeshNode,
    lastMessage: ChatMessage? = null,
    onChatClick: (MeshNode) -> Unit,
    onRefreshClick: (MeshNode) -> Unit,
    onDeleteClick: ((MeshNode) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
            .clickable { onChatClick(node) },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = if (node.isOnline) AccentCyan else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${node.host}:${node.port} • ${node.platform}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Online/Offline badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (node.isOnline) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (node.isOnline) AccentGreen else AccentRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (node.isOnline) "${node.lastPingMs}ms" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (node.isOnline) AccentGreen else AccentRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // System specs (CPU, RAM) if online
            if (node.isOnline && node.systemInfo != null) {
                val sys = node.systemInfo

                // CPU
                if (sys.cpuBrand != null || sys.cpuUsagePct != null) {
                    val usage = (sys.cpuUsagePct ?: 0.0).toFloat().coerceIn(0f, 100f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CPU: ${sys.cpuBrand?.take(22) ?: "Procesor"}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${String.format("%.1f", usage)}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { usage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentCyan,
                        trackColor = SurfaceVariantDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // RAM
                if (sys.memory != null) {
                    val mem = sys.memory
                    val usedGb = mem.usedMb / 1024.0
                    val totalGb = mem.totalMb / 1024.0
                    val pct = (mem.usagePct / 100.0).toFloat().coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RAM (${String.format("%.1f", usedGb)} / ${String.format("%.1f", totalGb)} GB)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${String.format("%.1f", mem.usagePct)}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentIndigo
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentIndigo,
                        trackColor = SurfaceVariantDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Disks preview
                if (!sys.disks.isNullOrEmpty()) {
                    val mainDisk = sys.disks.first()
                    Text(
                        text = "Dysk: ${mainDisk.name} (${mainDisk.availableGb} GB wolne z ${mainDisk.totalGb} GB)",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // Last Message Conversation Preview
            if (lastMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceVariantDark.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lastMessage.isUser) "Ty: " else "${lastMessage.senderNode}: ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (lastMessage.isUser) AccentCyan else AccentGreen
                        )
                        Text(
                            text = lastMessage.content,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDeleteClick != null) {
                    IconButton(
                        onClick = { onDeleteClick(node) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Usuń węzeł",
                            tint = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = { onRefreshClick(node) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Odśwież",
                        tint = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onChatClick(node) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = BgDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Zapytaj Agenta",
                        color = BgDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
