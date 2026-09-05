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

class MeshRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("antigravity_mesh_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scanner = LanScanner()

    private val _nodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val nodes: StateFlow<List<MeshNode>> = _nodes.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    init {
        loadSavedNodes()
    }

    private fun loadSavedNodes() {
        val jsonStr = prefs.getString("saved_nodes", null)
        if (jsonStr != null) {
            val type = object : TypeToken<List<MeshNode>>() {}.type
            val saved: List<MeshNode> = gson.fromJson(jsonStr, type)
            _nodes.value = saved
        } else {
            // Default configured nodes for user's cluster
            val defaultNodes = listOf(
                MeshNode(
                    id = "mac-mini",
                    name = "Mac mini (M4)",
                    host = "192.168.68.52",
                    port = 8888,
                    token = "cee512ed02bf479644140fbc25a34076",
                    platform = "Darwin"
                ),
                MeshNode(
                    id = "windows-pc",
                    name = "Windows PC (Workstation)",
                    host = "192.168.68.51",
                    port = 8888,
                    token = "mesh-secret-key-2026",
                    platform = "Windows"
                )
            )
            _nodes.value = defaultNodes
            saveNodes(defaultNodes)
        }
    }

    private fun saveNodes(nodesList: List<MeshNode>) {
        val jsonStr = gson.toJson(nodesList)
        prefs.edit().putString("saved_nodes", jsonStr).apply()
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
                    senderNode = targetNodeId,
                    isUser = false,
                    content = "Błąd: Nie znaleziono węzła '$targetNodeId'",
                    isError = true
                )

            val api = MeshApiService.create("http://${target.host}:${target.port}")
            try {
                val res = api.askAgent(target.token, AskRequest(question = question))
                val reply = res.stdout?.trim()?.ifEmpty { res.stderr?.trim() }
                    ?: (res.error ?: "Agent nie zwrócił odpowiedzi.")
                ChatMessage(
                    senderNode = target.name,
                    isUser = false,
                    content = reply,
                    isError = res.error != null || res.returncode != 0
                )
            } catch (e: Exception) {
                ChatMessage(
                    senderNode = target.name,
                    isUser = false,
                    content = "Błąd połączenia z węzłem: ${e.localizedMessage}",
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
        val foundIps = scanner.scanSubnet("192.168.68", 8888)
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
        _chatMessages.value = _chatMessages.value + msg
    }
}
