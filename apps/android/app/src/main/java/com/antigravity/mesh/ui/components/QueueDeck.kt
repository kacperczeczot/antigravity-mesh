package com.antigravity.mesh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.data.QueuedMessage
import com.antigravity.mesh.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueDeck(
    queuedMessages: List<QueuedMessage>,
    onEditMessage: (QueuedMessage) -> Unit,
    onFastTrackMessage: (String) -> Unit,
    onCancelMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = queuedMessages.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceDark,
            border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Header Bar (Compact single line)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = AccentAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Kolejka zadań (${queuedMessages.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber
                            )
                            Text(
                                text = "• Oczekuje",
                                fontSize = 10.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (queuedMessages.size > 1) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    queuedMessages.forEach { onCancelMessage(it.id) }
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = SurfaceElevated,
                                border = BorderStroke(1.dp, BorderDark),
                                modifier = Modifier.semantics { contentDescription = "Wyczyść wszystko" }
                            ) {
                                Text(
                                    text = "Wyczyść wszystko",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Queued Messages: compact single narrow row per item
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        queuedMessages.forEachIndexed { index, item ->
                            QueueItemRow(
                                index = index + 1,
                                item = item,
                                onEdit = { onEditMessage(item) },
                                onFastTrack = { onFastTrackMessage(item.id) },
                                onCancel = { onCancelMessage(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItemRow(
    index: Int,
    item: QueuedMessage,
    onEdit: () -> Unit,
    onFastTrack: () -> Unit,
    onCancel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceVariantDark,
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sequential index badge
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(AccentAmber.copy(alpha = 0.2f))
                    .border(1.dp, AccentAmber.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAmber
                )
            }

            // Prompt text (compact 1 line with ellipsis)
            Text(
                text = item.text,
                fontSize = 12.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Simple, compact action buttons on the right
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit prompt
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEdit()
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .size(26.dp)
                        .semantics { contentDescription = "Edytuj prompt" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Fast-track / Send now
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFastTrack()
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = AccentAmber.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(26.dp)
                        .semantics { contentDescription = "Wyślij teraz" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Remove / Cancel
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCancel()
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .size(26.dp)
                        .semantics { contentDescription = "Usuń z kolejki" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
