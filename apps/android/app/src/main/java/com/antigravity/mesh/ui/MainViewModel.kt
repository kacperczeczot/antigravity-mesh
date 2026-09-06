package com.antigravity.mesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.mesh.data.ChatMessage
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

    private var currentChatJob: Job? = null

    fun sendChatMessage(nodeId: String, question: String, onLoadingChange: (Boolean) -> Unit) {
        currentChatJob?.cancel()
        currentChatJob = viewModelScope.launch {
            repository.addChatMessage(
                ChatMessage(
                    nodeId = nodeId,
                    senderNode = "Ty",
                    isUser = true,
                    content = question
                )
            )
            onLoadingChange(true)
            _agentWorkingStatus.value = "Inicjalizacja zapytania..."
            try {
                val reply = repository.askAgentStreaming(nodeId, question) { status ->
                    _agentWorkingStatus.value = status
                }
                repository.addChatMessage(reply)
            } catch (e: kotlinx.coroutines.CancellationException) {
                repository.addChatMessage(
                    ChatMessage(
                        nodeId = nodeId,
                        senderNode = "System",
                        isUser = false,
                        content = "⏹ Generowanie odpowiedzi zostało przerwane.",
                        isError = false
                    )
                )
                throw e
            } finally {
                _agentWorkingStatus.value = null
                onLoadingChange(false)
                currentChatJob = null
            }
        }
    }

    fun stopGenerating() {
        currentChatJob?.cancel()
        currentChatJob = null
        _agentWorkingStatus.value = null
    }

    fun clearChatHistory(nodeId: String) {
        repository.clearChatHistory(nodeId)
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
}
