package com.antigravity.mesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.ChatSession
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.network.MeshRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: MeshRepository = MeshRepository(application)

    val nodes: StateFlow<List<MeshNode>> = repository.nodes
    val chatHistories: StateFlow<Map<String, List<ChatMessage>>> = repository.chatHistories

    private val _agentWorkingStatus = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val agentWorkingStatus: StateFlow<String?> = _agentWorkingStatus

    // Tracks (nodeId, sessionId) actively generating AI content
    private val _generatingSession = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)
    val generatingSession: StateFlow<Pair<String, String>?> = _generatingSession

    fun isSessionGenerating(nodeId: String, sessionId: String?): Boolean {
        val cur = _generatingSession.value ?: return false
        return cur.first == nodeId && cur.second == sessionId
    }

    private var autoRefreshJob: Job? = null

    init {
        refreshAllNodes()
    }

    fun startAutoRefresh(intervalMs: Long = 4000L) {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                repository.refreshAllNodes()
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun refreshAllNodes() {
        viewModelScope.launch {
            repository.refreshAllNodes()
        }
    }

    fun scanAndPair(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.scanAndPair()
            repository.refreshAllNodes()
            onComplete()
        }
    }

    val sessions = repository.sessions
    val activeSessionIds = repository.activeSessionIds
    val messageQueue = repository.messageQueue

    fun getSessionsForNode(nodeId: String): List<ChatSession> = repository.getSessionsForNode(nodeId)
    fun getActiveSessionId(nodeId: String): String = repository.getActiveSessionId(nodeId)
    fun selectSession(nodeId: String, sessionId: String) = repository.selectSession(nodeId, sessionId)
    fun createSession(nodeId: String, title: String? = null): ChatSession = repository.createSession(nodeId, title)

    private var currentChatJob: Job? = null
    private var isGenerating = false

    fun sendChatMessage(nodeId: String, question: String, onLoadingChange: (Boolean) -> Unit) {
        val sessionId = getActiveSessionId(nodeId)

        if (isGenerating) {
            // Do not abort running task! Enqueue next message cleanly.
            val queued = repository.enqueueMessage(nodeId, sessionId, question)
            repository.addChatMessage(
                ChatMessage(
                    id = queued.id,
                    nodeId = nodeId,
                    senderNode = "Ty",
                    isUser = true,
                    content = question,
                    isQueued = true,
                    conversationId = sessionId
                )
            )
            return
        }

        executeChatPrompt(nodeId, sessionId, question, onLoadingChange)
    }

    private fun executeChatPrompt(
        nodeId: String,
        sessionId: String,
        question: String,
        onLoadingChange: (Boolean) -> Unit,
        queuedMessageId: String? = null
    ) {
        currentChatJob = viewModelScope.launch {
            isGenerating = true
            _generatingSession.value = Pair(nodeId, sessionId)
            if (queuedMessageId != null) {
                repository.markMessageDispatched(queuedMessageId)
            } else {
                repository.addChatMessage(
                    ChatMessage(
                        nodeId = nodeId,
                        senderNode = "Ty",
                        isUser = true,
                        content = question,
                        conversationId = sessionId
                    )
                )
            }
            onLoadingChange(true)
            _agentWorkingStatus.value = "Inicjalizacja zapytania..."
            try {
                val reply = repository.askAgentStreaming(nodeId, question, sessionId) { status ->
                    _agentWorkingStatus.value = status
                }
                repository.addChatMessage(reply.copy(conversationId = sessionId))
            } catch (e: kotlinx.coroutines.CancellationException) {
                repository.addChatMessage(
                    ChatMessage(
                        nodeId = nodeId,
                        senderNode = "System",
                        isUser = false,
                        content = "⏹ Generowanie odpowiedzi zostało przerwane.",
                        isError = false,
                        conversationId = sessionId
                    )
                )
                throw e
            } finally {
                _generatingSession.value = null
                _agentWorkingStatus.value = null
                onLoadingChange(false)
                isGenerating = false
                currentChatJob = null

                // Process next queued message if available
                val next = repository.dequeueNextMessage(nodeId, sessionId)
                if (next != null) {
                    executeChatPrompt(nodeId, sessionId, next.text, onLoadingChange, queuedMessageId = next.id)
                }
            }
        }
    }

    fun recoverNodeTask(nodeId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _agentWorkingStatus.value = "Weryfikacja stanu na węźle..."
            val res = repository.checkAndRecoverNodeTask(nodeId)
            when (res) {
                is MeshRepository.RecoverResult.Running -> {
                    _agentWorkingStatus.value = res.progress
                    val node = repository.nodes.value.find { it.id == nodeId }
                    while (isActive) {
                        kotlinx.coroutines.delay(2000)
                        val task = repository.getTask(nodeId, res.taskId) ?: break
                        if (task.status == com.antigravity.mesh.data.TaskStatus.RUNNING || task.status == com.antigravity.mesh.data.TaskStatus.QUEUED) {
                            _agentWorkingStatus.value = task.progress ?: "Agent przetwarza w tle..."
                        } else if (task.status == com.antigravity.mesh.data.TaskStatus.COMPLETED) {
                            val replyContent = task.result?.takeIf { it.isNotBlank() } ?: "Zadanie ukończone na węźle."
                            val chatMsg = ChatMessage(
                                nodeId = nodeId,
                                senderNode = node?.displayName ?: nodeId,
                                isUser = false,
                                content = replyContent,
                                conversationId = task.conversationId
                            )
                            repository.addChatMessage(chatMsg)
                            _agentWorkingStatus.value = null
                            onComplete(true)
                            break
                        } else {
                            _agentWorkingStatus.value = null
                            onComplete(false)
                            break
                        }
                    }
                }
                is MeshRepository.RecoverResult.Completed -> {
                    _agentWorkingStatus.value = null
                    onComplete(true)
                }
                is MeshRepository.RecoverResult.Failed -> {
                    _agentWorkingStatus.value = null
                    onComplete(false)
                }
                is MeshRepository.RecoverResult.NotFound -> {
                    _agentWorkingStatus.value = null
                    onComplete(false)
                }
            }
        }
    }

    fun stopGenerating() {
        currentChatJob?.cancel()
        currentChatJob = null
        isGenerating = false
        _agentWorkingStatus.value = null
        _generatingSession.value = null
    }

    fun renameSession(nodeId: String, sessionId: String, newTitle: String) = repository.renameSession(nodeId, sessionId, newTitle)
    fun deleteSession(nodeId: String, sessionId: String) = repository.deleteSession(nodeId, sessionId)

    fun clearChatHistory(nodeId: String, sessionId: String? = null) {
        repository.clearChatHistory(nodeId, sessionId)
    }

    fun pairWithHost(host: String, port: Int = 8888, pinOrToken: String? = null, onResult: (Result<MeshNode>) -> Unit) {
        viewModelScope.launch {
            val res = repository.pairWithHost(host, port, pinOrToken)
            onResult(res)
            if (res.isSuccess) {
                repository.refreshAllNodes()
            }
        }
    }

    fun removeNode(nodeId: String) {
        repository.removeNode(nodeId)
    }

    fun renameNode(nodeId: String, newName: String?) {
        repository.renameNode(nodeId, newName)
    }

    fun updateNodeDetails(nodeId: String, newName: String?, newHost: String?, newPort: Int?) {
        repository.updateNodeDetails(nodeId, newName, newHost, newPort)
    }

    fun togglePinNode(nodeId: String) {
        repository.togglePinNode(nodeId)
    }

    fun loadFiles(nodeId: String, path: String? = null, onResult: (Result<com.antigravity.mesh.data.FileQueryResponse>) -> Unit) {
        viewModelScope.launch {
            val res = repository.listFiles(nodeId, path)
            onResult(res)
        }
    }

    fun readFile(nodeId: String, filePath: String, onResult: (Result<com.antigravity.mesh.data.ReadFileResponse>) -> Unit) {
        viewModelScope.launch {
            val res = repository.readFile(nodeId, filePath)
            onResult(res)
        }
    }

    fun getRawFileStreamUrl(nodeId: String, filePath: String): String? {
        return repository.getRawFileStreamUrl(nodeId, filePath)
    }

    fun downloadRawFile(
        nodeId: String,
        filePath: String,
        destFile: java.io.File,
        onProgress: ((Float) -> Unit)? = null,
        onDone: (Result<java.io.File>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.downloadRawFile(nodeId, filePath, destFile, onProgress)
            onDone(res)
        }
    }

    fun uploadFile(
        nodeId: String,
        targetDir: String,
        fileName: String,
        fileUri: android.net.Uri,
        contentResolver: android.content.ContentResolver,
        onProgress: ((Float) -> Unit)? = null,
        onDone: (Result<com.antigravity.mesh.data.UploadFileResponse>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.uploadFile(nodeId, targetDir, fileName, fileUri, contentResolver, onProgress)
            onDone(res)
        }
    }

    private val _permissionsAuditReport = kotlinx.coroutines.flow.MutableStateFlow<com.antigravity.mesh.data.PermissionAuditReport?>(null)
    val permissionsAuditReport: StateFlow<com.antigravity.mesh.data.PermissionAuditReport?> = _permissionsAuditReport

    private val _isAuditLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isAuditLoading: StateFlow<Boolean> = _isAuditLoading

    private val _auditError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val auditError: StateFlow<String?> = _auditError

    fun runPermissionsAudit(nodeId: String) {
        viewModelScope.launch {
            _isAuditLoading.value = true
            _auditError.value = null
            val res = repository.checkPermissions(nodeId)
            res.onSuccess {
                _permissionsAuditReport.value = it
            }.onFailure {
                _auditError.value = it.localizedMessage ?: "Błąd podczas audytu uprawnień"
            }
            _isAuditLoading.value = false
        }
    }

    fun fixPermission(nodeId: String, action: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.fixPermission(nodeId, action)
            res.onSuccess {
                onResult(it.message)
                runPermissionsAudit(nodeId)
            }.onFailure {
                onResult("Błąd: ${it.localizedMessage}")
            }
        }
    }

    fun clearPermissionsAudit() {
        _permissionsAuditReport.value = null
        _auditError.value = null
    }
}

