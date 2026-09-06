package com.antigravity.mesh.network

import android.content.Context
import android.content.SharedPreferences
import com.antigravity.mesh.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MeshRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("antigravity_mesh_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scanner = LanScanner()

    private val _nodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val nodes: StateFlow<List<MeshNode>> = _nodes.asStateFlow()

    // Per-node chat history: nodeId -> list of messages
    private val _chatHistories = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val chatHistories: StateFlow<Map<String, List<ChatMessage>>> = _chatHistories.asStateFlow()

    // Per-node conversation session: nodeId -> conversationId
    private val _conversationIds = mutableMapOf<String, String>()

    init {
        loadSavedNodes()
        loadSavedChatHistories()
        loadSavedConversations()
    }

    private fun loadSavedNodes() {
        val jsonStr = prefs.getString("saved_nodes", null)
        if (jsonStr != null) {
            val type = object : TypeToken<List<MeshNode>>() {}.type
            val saved: List<MeshNode> = gson.fromJson(jsonStr, type) ?: emptyList()
            _nodes.value = saved
        } else {
            // Start with empty nodes on clean install
            _nodes.value = emptyList()
        }
    }

    private fun saveNodes(nodesList: List<MeshNode>) {
        val jsonStr = gson.toJson(nodesList)
        prefs.edit().putString("saved_nodes", jsonStr).apply()
    }

    private fun loadSavedChatHistories() {
        val jsonStr = prefs.getString("saved_chat_histories", null)
        if (jsonStr != null) {
            try {
                val type = object : TypeToken<Map<String, List<ChatMessage>>>() {}.type
                val saved: Map<String, List<ChatMessage>> = gson.fromJson(jsonStr, type) ?: emptyMap()
                _chatHistories.value = saved
            } catch (_: Exception) {
                _chatHistories.value = emptyMap()
            }
        }
    }

    private fun saveChatHistories(map: Map<String, List<ChatMessage>>) {
        val jsonStr = gson.toJson(map)
        prefs.edit().putString("saved_chat_histories", jsonStr).apply()
    }

    private fun loadSavedConversations() {
        val jsonStr = prefs.getString("saved_conversations", null)
        if (jsonStr != null) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val saved: Map<String, String> = gson.fromJson(jsonStr, type) ?: emptyMap()
                _conversationIds.putAll(saved)
            } catch (_: Exception) {
                // Ignore failure
            }
        }
    }

    private fun saveConversations() {
        val jsonStr = gson.toJson(_conversationIds)
        prefs.edit().putString("saved_conversations", jsonStr).apply()
    }

    suspend fun refreshAllNodes(): Unit = withContext(Dispatchers.IO) {
        val current = _nodes.value
        val updated = current.map { node ->
            refreshNode(node)
        }
        _nodes.value = updated
        saveNodes(updated)
    }

    private suspend fun refreshNode(node: MeshNode): MeshNode {
        val api = MeshApiService.create("http://${node.host}:${node.port}")
        val start = System.currentTimeMillis()
        return try {
            val health = api.checkHealth(node.token)
            val elapsed = System.currentTimeMillis() - start
            val sysInfo = try {
                api.getSystemInfo(node.token)
            } catch (_: Exception) {
                null
            }
            node.copy(
                isOnline = true,
                lastPingMs = elapsed,
                platform = health.platform,
                systemInfo = sysInfo
            )
        } catch (_: Exception) {
            node.copy(isOnline = false, lastPingMs = -1)
        }
    }

    suspend fun askAgent(targetNodeId: String, question: String): ChatMessage =
        withContext(Dispatchers.IO) {
            val target = _nodes.value.find { it.id == targetNodeId }
                ?: return@withContext ChatMessage(
                    nodeId = targetNodeId,
                    senderNode = targetNodeId,
                    isUser = false,
                    content = "Błąd: Nie znaleziono węzła '$targetNodeId'",
                    isError = true
                )

            val api = MeshApiService.create("http://${target.host}:${target.port}")
            try {
                val currentConvId = _conversationIds[targetNodeId]
                val res = api.askAgent(
                    target.token,
                    AskRequest(
                        question = question,
                        conversationId = currentConvId
                    )
                )
                if (!res.conversationId.isNullOrBlank()) {
                    _conversationIds[targetNodeId] = res.conversationId
                    saveConversations()
                }
                val reply = res.stdout?.trim()?.ifEmpty { res.stderr?.trim() }
                    ?: (res.error ?: "Agent nie zwrócił odpowiedzi.")
                ChatMessage(
                    nodeId = targetNodeId,
                    senderNode = target.name,
                    isUser = false,
                    content = reply,
                    isError = res.error != null || res.returncode != 0
                )
            } catch (e: Exception) {
                ChatMessage(
                    nodeId = targetNodeId,
                    senderNode = target.name,
                    isUser = false,
                    content = "Błąd połączenia z węzłem ${target.name} (${target.host}:${target.port}): ${e.localizedMessage}.\n\n💡 Upewnij się, że Antigravity Mesh jest włączony na tym komputerze (włącz 'Uruchamiaj przy starcie' w ikonie w zasobniku systemowym).",
                    isError = true
                )
            }
        }

    suspend fun askAgentStreaming(
        targetNodeId: String,
        question: String,
        onStatusUpdate: (String) -> Unit
    ): ChatMessage = withContext(Dispatchers.IO) {
        val target = _nodes.value.find { it.id == targetNodeId }
            ?: return@withContext ChatMessage(
                nodeId = targetNodeId,
                senderNode = targetNodeId,
                isUser = false,
                content = "Błąd: Nie znaleziono węzła '$targetNodeId'",
                isError = true
            )

        val currentConvId = _conversationIds[targetNodeId]
        val jsonBody = gson.toJson(
            AskRequest(
                question = question,
                conversationId = currentConvId
            )
        )
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)
        val url = "http://${target.host}:${target.port}/ask/stream"

        val request = Request.Builder()
            .url(url)
            .addHeader("X-Mesh-Token", target.token)
            .post(requestBody)
            .build()

        try {
            val client = MeshApiService.client
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext askAgent(targetNodeId, question)
                }
                return@withContext ChatMessage(
                    nodeId = targetNodeId,
                    senderNode = target.name,
                    isUser = false,
                    content = "Błąd węzła (${response.code}): ${response.message}",
                    isError = true
                )
            }

            val reader = response.body?.byteStream()?.bufferedReader(Charsets.UTF_8)
                ?: return@withContext askAgent(targetNodeId, question)

            var currentEvent = ""
            var finalResultJson: String? = null
            var errorMessage: String? = null

            reader.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("event:")) {
                        currentEvent = trimmed.removePrefix("event:").trim()
                    } else if (trimmed.startsWith("data:")) {
                        val data = trimmed.removePrefix("data:").trim()
                        when (currentEvent) {
                            "status" -> {
                                withContext(Dispatchers.Main) {
                                    onStatusUpdate(data)
                                }
                            }
                            "result" -> {
                                finalResultJson = data
                            }
                            "error" -> {
                                errorMessage = data
                            }
                        }
                    }
                }
            }

            if (finalResultJson != null) {
                val res = gson.fromJson(finalResultJson, ExecResponse::class.java)
                if (!res.conversationId.isNullOrBlank()) {
                    _conversationIds[targetNodeId] = res.conversationId
                    saveConversations()
                }
                val reply = res.stdout?.trim()?.ifEmpty { res.stderr?.trim() }
                    ?: (res.error ?: "Agent nie zwrócił odpowiedzi.")
                ChatMessage(
                    nodeId = targetNodeId,
                    senderNode = target.name,
                    isUser = false,
                    content = reply,
                    isError = res.error != null || res.returncode != 0
                )
            } else if (errorMessage != null) {
                ChatMessage(
                    nodeId = targetNodeId,
                    senderNode = target.name,
                    isUser = false,
                    content = "Błąd agenta: $errorMessage",
                    isError = true
                )
            } else {
                ChatMessage(
                    nodeId = targetNodeId,
                    senderNode = target.name,
                    isUser = false,
                    content = "Połączenie zostało przerwane przed zwróceniem wyniku.",
                    isError = true
                )
            }
        } catch (e: Exception) {
            ChatMessage(
                nodeId = targetNodeId,
                senderNode = target.name,
                isUser = false,
                content = "Błąd połączenia z węzłem ${target.name} (${target.host}:${target.port}): ${e.localizedMessage}.\n\n💡 Upewnij się, że Antigravity Mesh jest włączony na tym komputerze (włącz 'Uruchamiaj przy starcie' w ikonie w zasobniku systemowym).",
                isError = true
            )
        }
    }

    suspend fun executeCommand(targetNodeId: String, command: String): String =
        withContext(Dispatchers.IO) {
            val target = _nodes.value.find { it.id == targetNodeId }
                ?: return@withContext "Nie znaleziono węzła '$targetNodeId'"

            val api = MeshApiService.create("http://${target.host}:${target.port}")
            try {
                val res = api.executeCommand(target.token, ExecRequest(cmd = command))
                res.stdout ?: (res.stderr ?: (res.error ?: "Wykonano (brak wyjścia)"))
            } catch (e: Exception) {
                "Błąd: ${e.localizedMessage}"
            }
        }

    suspend fun scanAndPair(): List<PairResponse> = withContext(Dispatchers.IO) {
        val foundIps = scanner.scanSubnet(subnetPrefix = null, port = 8888)
        val paired = mutableListOf<PairResponse>()

        for (ip in foundIps) {
            val api = MeshApiService.create("http://$ip:8888")
            try {
                val res = api.pairNode(
                    PairRequest(
                        nodeName = "Android-Phone",
                        token = "android-token-client"
                    )
                )
                paired.add(res)
                // Add or update node
                val existing = _nodes.value.toMutableList()
                val idx = existing.indexOfFirst { it.host == ip }
                val newNode = MeshNode(
                    id = res.nodeName.lowercase().replace(" ", "-"),
                    name = res.nodeName,
                    host = ip,
                    port = 8888,
                    token = res.token,
                    platform = res.platform,
                    isOnline = true
                )
                if (idx >= 0) {
                    existing[idx] = newNode
                } else {
                    existing.add(newNode)
                }
                _nodes.value = existing
                saveNodes(existing)
            } catch (_: Exception) {
                // Ignore failure
            }
        }
        paired
    }

    fun addChatMessage(msg: ChatMessage) {
        val current = _chatHistories.value.toMutableMap()
        val nodeMessages = current.getOrDefault(msg.nodeId, emptyList())
        current[msg.nodeId] = nodeMessages + msg
        _chatHistories.value = current
        saveChatHistories(current)
    }

    fun clearChatHistory(nodeId: String) {
        val current = _chatHistories.value.toMutableMap()
        current.remove(nodeId)
        _chatHistories.value = current
        saveChatHistories(current)
        _conversationIds.remove(nodeId)
        saveConversations()
    }

    fun getMessagesForNode(nodeId: String): List<ChatMessage> {
        return _chatHistories.value[nodeId] ?: emptyList()
    }

    suspend fun pairWithHost(host: String, port: Int = 8888): Result<MeshNode> = withContext(Dispatchers.IO) {
        try {
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            val actualHost = if (cleanHost.contains(":")) cleanHost.substringBefore(":") else cleanHost
            val actualPort = if (cleanHost.contains(":")) cleanHost.substringAfter(":").toIntOrNull() ?: port else port

            val api = MeshApiService.create("http://$actualHost:$actualPort")
            val res = api.pairNode(
                PairRequest(
                    nodeName = "Android-Phone",
                    token = "android-token-client"
                )
            )
            val baseNode = MeshNode(
                id = res.nodeName.lowercase().replace(" ", "-"),
                name = res.nodeName,
                host = actualHost,
                port = actualPort,
                token = res.token,
                platform = res.platform,
                isOnline = true
            )
            val refreshed = refreshNode(baseNode)
            val existing = _nodes.value.toMutableList()
            val idx = existing.indexOfFirst { it.host == actualHost && it.port == actualPort }
            if (idx >= 0) {
                existing[idx] = refreshed
            } else {
                existing.add(refreshed)
            }
            _nodes.value = existing
            saveNodes(existing)
            Result.success(refreshed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun removeNode(nodeId: String) {
        val existing = _nodes.value.toMutableList()
        existing.removeAll { it.id == nodeId }
        _nodes.value = existing
        saveNodes(existing)
        clearChatHistory(nodeId)
    }
}
