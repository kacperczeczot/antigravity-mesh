package com.antigravity.mesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.components.MarkdownText
import com.antigravity.mesh.ui.theme.*

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ChatScreen(
    nodes: List<MeshNode>,
    selectedNodeId: String,
    onBack: () -> Unit = {},
    onSelectNode: (String) -> Unit,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    agentStatus: String? = null,
    onSendMessage: (String, String) -> Unit,
    onClearChat: (String) -> Unit = {}
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Bar with Back Button & Node Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wróć",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    val currentNode = nodes.find { it.id == selectedNodeId }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentNode?.displayName ?: "Rozmawiaj z Agentem",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (currentNode?.isPinned == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Przypięty",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        val statusText = buildString {
                            append(if (currentNode?.isOnline == true) "Online" else "Offline")
                            append(" • ${currentNode?.host}")
                            if (currentNode?.customName != null) {
                                append(" (${currentNode.name})")
                            }
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (currentNode?.isOnline == true) AccentGreen else AccentRed
                        )
                    }
                }

                if (messages.isNotEmpty()) {
                    IconButton(onClick = { onClearChat(selectedNodeId) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Wyczyść czat",
                            tint = TextMuted
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val sortedChips = remember(nodes) {
                nodes.sortedWith(
                    compareByDescending<MeshNode> { it.isPinned }
                        .thenByDescending { it.isOnline }
                        .thenBy { it.displayName.lowercase() }
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sortedChips, key = { it.id }) { node ->
                    val isSelected = node.id == selectedNodeId
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectNode(node.id) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (node.isPinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isSelected) BgDark else AccentCyan
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(node.displayName)
                            }
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (node.isOnline) AccentGreen else AccentRed)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan,
                            selectedLabelColor = BgDark,
                            containerColor = SurfaceVariantDark,
                            labelColor = TextPrimary
                        )
                    )
                }
            }
        }


        // Messages List
        if (messages.isEmpty() && !isLoading) {
            // Empty state for this node's conversation
            val selectedNode = nodes.find { it.id == selectedNodeId }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Czat z ${selectedNode?.displayName ?: selectedNodeId}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Zadaj pytanie agentowi AI na tym urządzeniu",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg)
                }

                if (isLoading) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceVariantDark.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = agentStatus ?: "Agent myśli...",
                                    fontSize = 12.sp,
                                    color = if (agentStatus != null) AccentCyan else TextSecondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Input Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Zadaj pytanie agentowi...", color = TextMuted) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceVariantDark,
                    unfocusedContainerColor = SurfaceVariantDark,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isLoading) {
                        onSendMessage(selectedNodeId, inputText.trim())
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank() && !isLoading) AccentCyan else SurfaceVariantDark)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Wyślij",
                    tint = if (inputText.isNotBlank() && !isLoading) BgDark else TextMuted
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AccentIndigo.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) AccentCyan
                    else if (message.isError) AccentRed.copy(alpha = 0.15f)
                    else SurfaceDark
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) AccentCyan else if (message.isError) AccentRed else BorderDark,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            if (!isUser) {
                Text(
                    text = message.senderNode,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (message.isError) AccentRed else AccentCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (isUser) {
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = BgDark
                )
            } else {
                MarkdownText(
                    markdown = message.content,
                    textColor = TextPrimary
                )
            }
        }
    }
}
