package com.antigravity.mesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.network.MeshRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: MeshRepository = MeshRepository(application)

    val nodes: StateFlow<List<MeshNode>> = repository.nodes
    val chatHistories: StateFlow<Map<String, List<ChatMessage>>> = repository.chatHistories

    init {
        refreshAllNodes()
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
            val reply = repository.askAgent(nodeId, question)
            repository.addChatMessage(reply)
            onLoadingChange(false)
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
}
