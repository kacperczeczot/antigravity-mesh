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

    fun sendChatMessage(nodeId: String, question: String, onLoadingChange: (Boolean) -> Unit) {
        viewModelScope.launch {
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
            } finally {
                _agentWorkingStatus.value = null
                onLoadingChange(false)
            }
        }
    }

    fun clearChatHistory(nodeId: String) {
        repository.clearChatHistory(nodeId)
    }

    fun pairWithHost(host: String, port: Int = 8888, onResult: (Result<MeshNode>) -> Unit) {
        viewModelScope.launch {
            val res = repository.pairWithHost(host, port)
            repository.refreshAllNodes()
            onResult(res)
        }
    }

    fun removeNode(nodeId: String) {
        repository.removeNode(nodeId)
    }

    fun renameNode(nodeId: String, newName: String?) {
        repository.renameNode(nodeId, newName)
    }

    fun togglePinNode(nodeId: String) {
        repository.togglePinNode(nodeId)
    }
}
