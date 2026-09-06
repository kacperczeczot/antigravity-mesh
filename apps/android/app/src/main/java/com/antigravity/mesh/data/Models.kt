package com.antigravity.mesh.data

import com.google.gson.annotations.SerializedName

data class MeshNode(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 8888,
    val token: String,
    val platform: String = "Unknown",
    val isOnline: Boolean = false,
    val lastPingMs: Long = 0,
    val systemInfo: SystemInfoResponse? = null,
    val isPinned: Boolean = false,
    val customName: String? = null
) {
    val displayName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: name
}

data class SystemInfoResponse(
    @SerializedName("node_name") val nodeName: String? = null,
    @SerializedName("os_name") val osName: String? = null,
    @SerializedName("os_version") val osVersion: String? = null,
    @SerializedName("cpu_brand") val cpuBrand: String? = null,
    @SerializedName("cpu_count") val cpuCount: Int? = null,
    @SerializedName("cpu_usage_pct") val cpuUsagePct: Double? = null,
    @SerializedName("memory") val memory: MemoryInfo? = null,
    @SerializedName("disks") val disks: List<DiskInfo>? = null,
    @SerializedName("cwd") val cwd: String? = null,
    @SerializedName("engine") val engine: String? = null
)

data class MemoryInfo(
    @SerializedName("total_mb") val totalMb: Long = 0,
    @SerializedName("used_mb") val usedMb: Long = 0,
    @SerializedName("free_mb") val freeMb: Long = 0,
    @SerializedName("usage_pct") val usagePct: Double = 0.0
)

data class DiskInfo(
    val name: String = "",
    @SerializedName("mount_point") val mountPoint: String = "",
    @SerializedName("total_gb") val totalGb: Long = 0,
    @SerializedName("available_gb") val availableGb: Long = 0
)

data class HealthResponse(
    val status: String = "",
    val platform: String = "",
    val node: String = "",
    val engine: String? = null
)

data class ExecRequest(
    val cmd: String
)

data class ExecResponse(
    val returncode: Int = 0,
    val stdout: String? = null,
    val stderr: String? = null,
    val error: String? = null,
    @SerializedName("conversation_id") val conversationId: String? = null
)

data class AskRequest(
    val question: String,
    @SerializedName("auto_approve") val autoApprove: Boolean = true,
    @SerializedName("conversation_id") val conversationId: String? = null
)

data class PairRequest(
    @SerializedName("node_name") val nodeName: String,
    val host: String? = null,
    val port: Int = 8888,
    val token: String,
    val pin: String? = null
)

data class PairResponse(
    val status: String = "",
    @SerializedName("node_name") val nodeName: String = "",
    val token: String = "",
    val platform: String = "",
    val error: String? = null
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val nodeId: String = "",
    val senderNode: String,
    val isUser: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

data class FileQueryRequest(
    val path: String = ".",
    @SerializedName("max_depth") val maxDepth: Int = 1
)

data class FileQueryResponse(
    val path: String = "",
    @SerializedName("current_path") val currentPath: String = "",
    @SerializedName("parent_path") val parentPath: String? = null,
    val count: Int = 0,
    val items: List<FileItem> = emptyList(),
    val error: String? = null
)

data class FileItem(
    val name: String = "",
    val type: String = "file",
    @SerializedName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
    val modified: Long = 0,
    val path: String = ""
) {
    val isDirectory: Boolean
        get() = isDir || type == "dir"

    val formattedSize: String
        get() {
            if (isDirectory) return ""
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
                else -> String.format(java.util.Locale.US, "%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
            }
        }
}

data class ReadFileRequest(
    val path: String
)

data class ReadFileResponse(
    val path: String = "",
    val name: String = "",
    val size: Long = 0,
    val content: String = "",
    @SerializedName("is_binary") val isBinary: Boolean = false,
    @SerializedName("is_dir") val isDir: Boolean = false,
    @SerializedName("mime_type") val mimeType: String? = null,
    val error: String? = null
)

data class UploadFileResponse(
    val success: Boolean = false,
    val path: String? = null,
    @SerializedName("bytes_written") val bytesWritten: Long = 0L,
    val error: String? = null
)
