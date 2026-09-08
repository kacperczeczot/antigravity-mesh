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
    private var skipNextAutoDequeue = false

    fun sendChatMessage(nodeId: String, question: String, onLoadingChange: (Boolean) -> Unit) {
        val sessionId = getActiveSessionId(nodeId)

        if (isGenerating) {
            // Do not abort running task! Enqueue next message cleanly into queue deck.
            repository.enqueueMessage(nodeId, sessionId, question)
            return
        }

        executeChatPrompt(nodeId, sessionId, question, onLoadingChange)
    }

    fun sendChatMessageImmediate(nodeId: String, question: String, onLoadingChange: (Boolean) -> Unit) {
        val sessionId = getActiveSessionId(nodeId)
        skipNextAutoDequeue = true
        val prevJob = currentChatJob
        prevJob?.cancel()

        executeChatPrompt(nodeId, sessionId, question, onLoadingChange)
    }

    fun editQueuedMessage(id: String): String? {
        val item = repository.messageQueue.value.find { it.id == id }
        if (item != null) {
            repository.removeQueuedMessage(id)
            repository.removeChatMessage(id)
            return item.text
        }
        return null
    }

    fun fastTrackQueuedMessage(nodeId: String, messageId: String, onLoadingChange: (Boolean) -> Unit) {
        val sessionId = getActiveSessionId(nodeId)
        val queued = repository.messageQueue.value.find { it.id == messageId }
        val promptText = queued?.text
            ?: repository.chatHistories.value[nodeId]?.find { it.id == messageId }?.content
            ?: return

        repository.removeQueuedMessage(messageId)
        repository.removeChatMessage(messageId)
        skipNextAutoDequeue = true
        val prevJob = currentChatJob
        prevJob?.cancel()

        executeChatPrompt(nodeId, sessionId, promptText, onLoadingChange, queuedMessageId = messageId)
    }

    fun cancelQueuedMessage(nodeId: String, messageId: String) {
        repository.removeQueuedMessage(messageId)
        repository.removeChatMessage(messageId)
    }

    private fun executeChatPrompt(
        nodeId: String,
        sessionId: String,
        question: String,
        onLoadingChange: (Boolean) -> Unit,
        queuedMessageId: String? = null
    ) {
        var thisJob: Job? = null
        thisJob = viewModelScope.launch {
            isGenerating = true
            _generatingSession.value = Pair(nodeId, sessionId)
            if (queuedMessageId != null && repository.chatHistories.value[nodeId]?.any { it.id == queuedMessageId } == true) {
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
                if (currentChatJob == thisJob || currentChatJob == null) {
                    _generatingSession.value = null
                    _agentWorkingStatus.value = null
                    onLoadingChange(false)
                    isGenerating = false
                    if (currentChatJob == thisJob) {
                        currentChatJob = null
                    }

                    // Process next queued message if available and not skipped
                    val shouldDequeue = !skipNextAutoDequeue
                    skipNextAutoDequeue = false
                    if (shouldDequeue) {
                        val next = repository.dequeueNextMessage(nodeId, sessionId)
                            ?: repository.dequeueAnyNextMessage()
                        if (next != null) {
                            executeChatPrompt(next.nodeId, next.sessionId, next.text, onLoadingChange, queuedMessageId = next.id)
                        }
                    }
                }
            }
        }
        currentChatJob = thisJob
    }

    fun recoverNodeTask(nodeId: String, messageId: String? = null, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _agentWorkingStatus.value = "Weryfikacja stanu na węźle..."
            val res = repository.checkAndRecoverNodeTask(nodeId, messageId)
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
                            if (messageId != null) {
                                repository.updateChatMessage(nodeId, messageId) {
                                    it.copy(content = replyContent, isError = false, canRecover = false)
                                }
                            } else {
                                val chatMsg = ChatMessage(
                                    nodeId = nodeId,
                                    senderNode = node?.displayName ?: nodeId,
                                    isUser = false,
                                    content = replyContent,
                                    conversationId = task.conversationId
                                )
                                repository.addChatMessage(chatMsg)
                            }
                            _agentWorkingStatus.value = null
                            onComplete(true, null)
                            dequeueNextQueuedMessage(nodeId, task.conversationId ?: getActiveSessionId(nodeId))
                            break
                        } else {
                            val errText = task.error?.takeIf { it.isNotBlank() }
                                ?: task.result?.takeIf { it.isNotBlank() }
                                ?: "Zadanie zakończyło się błędem na węźle."
                            if (messageId != null) {
                                repository.updateChatMessage(nodeId, messageId) {
                                    it.copy(content = errText, isError = true, canRecover = false)
                                }
                            }
                            _agentWorkingStatus.value = null
                            onComplete(false, errText)
                            break
                        }
                    }
                }
                is MeshRepository.RecoverResult.Completed -> {
                    _agentWorkingStatus.value = null
                    onComplete(true, null)
                    dequeueNextQueuedMessage(nodeId, getActiveSessionId(nodeId))
                }
                is MeshRepository.RecoverResult.Failed -> {
                    _agentWorkingStatus.value = null
                    onComplete(false, res.error)
                }
                is MeshRepository.RecoverResult.NotFound -> {
                    _agentWorkingStatus.value = null
                    onComplete(false, "Nie znaleziono aktywnego zadania na węźle")
                }
            }
        }
    }

    fun deleteChatMessage(nodeId: String, messageId: String) {
        repository.removeChatMessage(messageId)
    }

    private fun dequeueNextQueuedMessage(nodeId: String, sessionId: String) {
        val shouldDequeue = !skipNextAutoDequeue
        skipNextAutoDequeue = false
        if (shouldDequeue) {
            val next = repository.dequeueNextMessage(nodeId, sessionId)
                ?: repository.dequeueAnyNextMessage()
            if (next != null) {
                executeChatPrompt(next.nodeId, next.sessionId, next.text, {}, queuedMessageId = next.id)
            }
        }
    }

    fun stopGenerating() {
        skipNextAutoDequeue = true
        val activeNodeId = _generatingSession.value?.first
        if (activeNodeId != null) {
            viewModelScope.launch {
                repository.cancelActiveNodeTask(activeNodeId)
            }
        }
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

    private val activeDownloadJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val activeUploadJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    private fun getDownloadKey(nodeId: String, filePath: String): String = "$nodeId:${filePath.trim()}"
    private fun getUploadKey(nodeId: String, targetDir: String, fileName: String): String = "$nodeId:${targetDir.trim()}:${fileName.trim()}"

    fun isDownloading(nodeId: String, filePath: String): Boolean {
        val key = getDownloadKey(nodeId, filePath)
        return activeDownloadJobs[key]?.isActive == true
    }

    fun isUploading(nodeId: String, targetDir: String, fileName: String): Boolean {
        val key = getUploadKey(nodeId, targetDir, fileName)
        return activeUploadJobs[key]?.isActive == true
    }

    fun cancelDownload(nodeId: String, filePath: String) {
        val key = getDownloadKey(nodeId, filePath)
        activeDownloadJobs.remove(key)?.cancel()
    }

    fun cancelUpload(nodeId: String, targetDir: String, fileName: String) {
        val key = getUploadKey(nodeId, targetDir, fileName)
        activeUploadJobs.remove(key)?.cancel()
    }

    fun downloadRawFile(
        nodeId: String,
        filePath: String,
        destFile: java.io.File,
        onProgress: ((Float) -> Unit)? = null,
        onDone: (Result<java.io.File>) -> Unit
    ) {
        val key = getDownloadKey(nodeId, filePath)
        if (activeDownloadJobs[key]?.isActive == true) {
            // Already downloading this exact file! Reject re-entrant / duplicate clicks.
            return
        }

        val job = viewModelScope.launch {
            try {
                val res = repository.downloadRawFile(nodeId, filePath, destFile, onProgress)
                onDone(res)
            } catch (e: kotlinx.coroutines.CancellationException) {
                onDone(Result.failure(Exception("Pobieranie zostało anulowane")))
            } finally {
                activeDownloadJobs.remove(key)
            }
        }
        activeDownloadJobs[key] = job
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
        val key = getUploadKey(nodeId, targetDir, fileName)
        if (activeUploadJobs[key]?.isActive == true) {
            // Already uploading! Reject duplicate clicks.
            return
        }

        val job = viewModelScope.launch {
            try {
                val res = repository.uploadFile(nodeId, targetDir, fileName, fileUri, contentResolver, onProgress)
                onDone(res)
            } catch (e: kotlinx.coroutines.CancellationException) {
                onDone(Result.failure(Exception("Wgrywanie zostało anulowane")))
            } finally {
                activeUploadJobs.remove(key)
            }
        }
        activeUploadJobs[key] = job
    }

    private val _permissionsAuditReport = kotlinx.coroutines.flow.MutableStateFlow<com.antigravity.mesh.data.PermissionAuditReport?>(null)
    val permissionsAuditReport: StateFlow<com.antigravity.mesh.data.PermissionAuditReport?> = _permissionsAuditReport

    private val _isAuditLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isAuditLoading: StateFlow<Boolean> = _isAuditLoading

    private val _auditError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val auditError: StateFlow<String?> = _auditError

    fun runPermissionsAudit(nodeId: String) {
        if (_isAuditLoading.value) return
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

    private val _fixingAction = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val fixingAction: StateFlow<String?> = _fixingAction

    fun fixPermission(nodeId: String, action: String, onResult: (String) -> Unit) {
        if (_fixingAction.value != null) return
        viewModelScope.launch {
            _fixingAction.value = action
            val res = repository.fixPermission(nodeId, action)
            res.onSuccess {
                onResult(it.message)
                runPermissionsAudit(nodeId)
            }.onFailure {
                onResult("Błąd: ${it.localizedMessage}")
            }
            _fixingAction.value = null
        }
    }

    fun clearPermissionsAudit() {
        _permissionsAuditReport.value = null
        _auditError.value = null
    }
}

