package com.antigravity.mesh.network

import android.content.Context
import android.content.SharedPreferences
import com.antigravity.mesh.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
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
        if (current.isEmpty()) return@withContext
        val updated = coroutineScope {
            current.map { node ->
                async { refreshNode(node) }
            }.awaitAll()
        }
        _nodes.value = updated
        saveNodes(updated)
    }

    private suspend fun refreshNode(node: MeshNode): MeshNode {
        val start = System.currentTimeMillis()
        return try {
            val api = MeshApiService.create("http://${node.host}:${node.port}")
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
                platform = health.platform.ifBlank { node.platform },
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

            val api = MeshApiService.create("http://${target.host}:${target.port}", isStreaming = true)
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
            response.use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 404) {
                        return@withContext askAgent(targetNodeId, question)
                    }
                    return@withContext ChatMessage(
                        nodeId = targetNodeId,
                        senderNode = target.name,
                        isUser = false,
                        content = "Błąd węzła (${resp.code}): ${resp.message}",
                        isError = true
                    )
                }

                val reader = resp.body?.byteStream()?.bufferedReader(Charsets.UTF_8)
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

            val api = MeshApiService.create("http://${target.host}:${target.port}", isStreaming = true)
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
            try {
                val api = MeshApiService.create("http://$ip:8888")
                val res = api.pairNode(
                    PairRequest(
                        nodeName = "Android-Phone",
                        token = "android-token-client"
                    )
                )
                paired.add(res)

                val safeNodeName = res.nodeName.trim().ifBlank { ip }
                val rawId = safeNodeName.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-').ifBlank { "node-${ip.replace('.', '-')}" }

                val existing = _nodes.value.toMutableList()
                val idx = existing.indexOfFirst {
                    (it.host == ip && it.port == 8888) || it.id == rawId
                }

                val targetId = if (idx >= 0) {
                    existing[idx].id
                } else {
                    var candidate = rawId
                    var counter = 2
                    while (existing.any { it.id == candidate }) {
                        candidate = "$rawId-$counter"
                        counter++
                    }
                    candidate
                }

                val newNode = MeshNode(
                    id = targetId,
                    name = safeNodeName,
                    host = ip,
                    port = 8888,
                    token = res.token,
                    platform = res.platform.ifBlank { "Linux" },
                    isOnline = true
                )

                if (idx >= 0) {
                    val prev = existing[idx]
                    existing[idx] = newNode.copy(
                        customName = prev.customName,
                        isPinned = prev.isPinned
                    )
                } else {
                    existing.add(newNode)
                }
                _nodes.value = existing
                saveNodes(existing)
            } catch (_: Exception) {
                // Ignore failure for unreachable IPs
            }
        }
        paired
    }

    fun addChatMessage(msg: ChatMessage) {
        val current = _chatHistories.value.toMutableMap()
        val nodeMessages = current.getOrDefault(msg.nodeId, emptyList())
        val updated = nodeMessages + msg
        // Limit history per node to latest 100 messages to prevent SharedPreferences bloating
        current[msg.nodeId] = if (updated.size > 100) updated.takeLast(100) else updated
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

    suspend fun pairWithHost(host: String, port: Int = 8888, pinOrToken: String? = null): Result<MeshNode> = withContext(Dispatchers.IO) {
        try {
            withTimeout(32000L) {
                var clean = host.trim()
                if (clean.startsWith("http://", ignoreCase = true)) clean = clean.substring(7)
                if (clean.startsWith("https://", ignoreCase = true)) clean = clean.substring(8)
                clean = clean.trimEnd('/')

                val actualHost = if (clean.contains(":")) clean.substringBefore(":").trim() else clean.trim()
                val actualPort = if (clean.contains(":")) clean.substringAfter(":").trim().toIntOrNull() ?: port else port

                if (actualHost.isBlank() || actualHost.contains(" ") || actualHost.contains("/")) {
                    return@withTimeout Result.failure<MeshNode>(IllegalArgumentException("Nieprawidłowy adres hosta: $host"))
                }
                if (actualPort !in 1..65535) {
                    return@withTimeout Result.failure<MeshNode>(IllegalArgumentException("Nieprawidłowy port: $actualPort"))
                }

                val api = MeshApiService.create("http://$actualHost:$actualPort", isPairing = true)
                val res = api.pairNode(
                    PairRequest(
                        nodeName = "Android-Phone",
                        token = pinOrToken?.trim()?.ifBlank { "android-token-client" } ?: "android-token-client",
                        pin = pinOrToken?.trim()?.ifBlank { null }
                    )
                )

                val safeNodeName = res.nodeName.trim().ifBlank { actualHost }
                val rawId = safeNodeName.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-').ifBlank { "node-${actualHost.replace('.', '-')}" }

                val existing = _nodes.value.toMutableList()
                val idx = existing.indexOfFirst {
                    (it.host.equals(actualHost, ignoreCase = true) && it.port == actualPort) || it.id == rawId
                }

                val targetId = if (idx >= 0) {
                    existing[idx].id
                } else {
                    var candidate = rawId
                    var counter = 2
                    while (existing.any { it.id == candidate }) {
                        candidate = "$rawId-$counter"
                        counter++
                    }
                    candidate
                }

                val baseNode = MeshNode(
                    id = targetId,
                    name = safeNodeName,
                    host = actualHost,
                    port = actualPort,
                    token = res.token,
                    platform = res.platform.ifBlank { "Linux" },
                    isOnline = true
                )

                val refreshed = refreshNode(baseNode)

                if (idx >= 0) {
                    val prev = existing[idx]
                    existing[idx] = refreshed.copy(
                        id = prev.id,
                        customName = prev.customName,
                        isPinned = prev.isPinned
                    )
                } else {
                    existing.add(refreshed)
                }

                _nodes.value = existing
                saveNodes(existing)
                Result.success(refreshed)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Przekroczono limit czasu (brak zatwierdzenia na komputerze lub brak odpowiedzi)"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val customMsg = try {
                if (!errorBody.isNullOrBlank()) {
                    org.json.JSONObject(errorBody).optString("error").takeIf { it.isNotBlank() }
                } else null
            } catch (_: Exception) {
                null
            } ?: when (e.code()) {
                403 -> "Połączenie zostało odrzucone na komputerze lub podano błędny PIN"
                401 -> "Błąd autoryzacji: nieprawidłowy token"
                else -> "Błąd serwera (HTTP ${e.code()})"
            }
            Result.failure(Exception(customMsg))
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

    fun renameNode(nodeId: String, newName: String?) {
        updateNodeDetails(nodeId, newName, null, null)
    }

    fun updateNodeDetails(nodeId: String, newName: String?, newHost: String? = null, newPort: Int? = null) {
        val updated = _nodes.value.map { node ->
            if (node.id == nodeId) {
                var modified = node.copy(customName = newName?.trim()?.ifBlank { null })
                if (!newHost.isNullOrBlank()) {
                    modified = modified.copy(host = newHost.trim())
                }
                if (newPort != null && newPort in 1..65535) {
                    modified = modified.copy(port = newPort)
                }
                modified
            } else {
                node
            }
        }
        _nodes.value = updated
        saveNodes(updated)
    }

    fun togglePinNode(nodeId: String) {
        val updated = _nodes.value.map { node ->
            if (node.id == nodeId) {
                node.copy(isPinned = !node.isPinned)
            } else {
                node
            }
        }
        _nodes.value = updated
        saveNodes(updated)
    }

    suspend fun listFiles(nodeId: String, path: String? = null): Result<FileQueryResponse> =
        withContext(Dispatchers.IO) {
            val target = _nodes.value.find { it.id == nodeId }
                ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

            try {
                val api = MeshApiService.create("http://${target.host}:${target.port}")
                val queryPath = path?.trim()?.ifBlank { "." } ?: "."
                val res = api.queryFiles(target.token, FileQueryRequest(path = queryPath, maxDepth = 1))
                Result.success(res)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun readFile(nodeId: String, filePath: String): Result<ReadFileResponse> =
        withContext(Dispatchers.IO) {
            val target = _nodes.value.find { it.id == nodeId }
                ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

            val cleanPath = filePath.trim()
            val api = MeshApiService.create("http://${target.host}:${target.port}")

            try {
                val res = api.readFile(target.token, ReadFileRequest(path = cleanPath))
                if (res.error != null && res.content.isBlank()) {
                    throw Exception(res.error)
                }
                Result.success(res)
            } catch (e: Exception) {
                // Fallback for older daemons or nodes where /read-file endpoint returned 404 or failed:
                // Use /exec with cat or powershell to read the file!
                try {
                    val isWindows = cleanPath.matches(Regex("^[a-zA-Z]:.*")) || cleanPath.contains('\\')
                    val cmd = if (isWindows) {
                        "powershell -NoProfile -Command \"Get-Content -Path '$cleanPath' -Raw -Encoding UTF8\""
                    } else {
                        "cat '$cleanPath' 2>/dev/null || head -n 5000 '$cleanPath'"
                    }
                    val execRes = api.executeCommand(target.token, ExecRequest(cmd = cmd))
                    if (execRes.returncode == 0) {
                        val content = execRes.stdout ?: ""
                        Result.success(
                            ReadFileResponse(
                                path = cleanPath,
                                name = cleanPath.substringAfterLast('/').substringAfterLast('\\'),
                                size = content.toByteArray().size.toLong(),
                                content = content,
                                isBinary = false,
                                isDir = false,
                                error = null
                            )
                        )
                    } else {
                        val errText = execRes.stderr ?: e.localizedMessage ?: "Błąd odczytu pliku"
                        Result.failure(Exception(errText))
                    }
                } catch (fallbackErr: Exception) {
                    Result.failure(e)
                }
            }
        }

    fun getRawFileStreamUrl(nodeId: String, filePath: String): String? {
        val target = _nodes.value.find { it.id == nodeId } ?: return null
        val encodedPath = java.net.URLEncoder.encode(filePath.trim(), "UTF-8")
        val tokenParam = if (target.token.isNotBlank()) "&token=${java.net.URLEncoder.encode(target.token, "UTF-8")}" else ""
        return "http://${target.host}:${target.port}/file-raw?path=$encodedPath$tokenParam"
    }

    suspend fun downloadRawFile(
        nodeId: String,
        filePath: String,
        destFile: java.io.File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        val target = _nodes.value.find { it.id == nodeId }
            ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

        val cleanPath = filePath.trim()
        val encodedPath = java.net.URLEncoder.encode(cleanPath, "UTF-8")
        val tokenParam = if (target.token.isNotBlank()) "&token=${java.net.URLEncoder.encode(target.token, "UTF-8")}" else ""
        val rawUrl = "http://${target.host}:${target.port}/file-raw?path=$encodedPath$tokenParam"

        try {
            val url = java.net.URL(rawUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.requestMethod = "GET"
            if (target.token.isNotBlank()) {
                conn.setRequestProperty("X-Mesh-Token", target.token)
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(Exception("Błąd serwera ($code) podczas pobierania pliku"))
            }

            val contentLength = conn.contentLengthLong
            if (destFile.exists()) destFile.delete()

            conn.inputStream.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (contentLength > 0 && onProgress != null) {
                            onProgress((bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        nodeId: String,
        targetDir: String,
        fileName: String,
        fileUri: android.net.Uri,
        contentResolver: android.content.ContentResolver,
        onProgress: ((Float) -> Unit)? = null
    ): Result<UploadFileResponse> = withContext(Dispatchers.IO) {
        val target = _nodes.value.find { it.id == nodeId }
            ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

        val cleanDir = targetDir.trim()
        val encodedDir = java.net.URLEncoder.encode(cleanDir, "UTF-8")
        val encodedName = java.net.URLEncoder.encode(fileName.trim(), "UTF-8")
        val tokenParam = if (target.token.isNotBlank()) "&token=${java.net.URLEncoder.encode(target.token, "UTF-8")}" else ""
        val uploadUrl = "http://${target.host}:${target.port}/upload?dir=$encodedDir&filename=$encodedName$tokenParam"

        try {
            // Determine file size from contentResolver
            val fileSize = contentResolver.query(fileUri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx != -1) cursor.getLong(sizeIdx) else -1L
                } else -1L
            } ?: -1L

            val url = java.net.URL(uploadUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 300_000 // 5 minutes for large uploads
            conn.requestMethod = "POST"
            conn.doOutput = true
            if (target.token.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer ${target.token}")
                conn.setRequestProperty("X-Mesh-Token", target.token)
            }
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            if (fileSize > 0) {
                conn.setFixedLengthStreamingMode(fileSize)
            } else {
                conn.setChunkedStreamingMode(32 * 1024)
            }

            contentResolver.openInputStream(fileUri)?.use { input ->
                conn.outputStream.use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesWritten = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        if (fileSize > 0 && onProgress != null) {
                            onProgress((bytesWritten.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(Exception("Nie można odczytać pliku źródłowego"))

            val code = conn.responseCode
            val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = responseStream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                return@withContext Result.failure(Exception("Błąd serwera ($code): $responseBody"))
            }

            val parsedResponse = try {
                gson.fromJson(responseBody, UploadFileResponse::class.java)
            } catch (_: Exception) {
                UploadFileResponse(success = true, path = "$cleanDir/$fileName")
            }

            Result.success(parsedResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
