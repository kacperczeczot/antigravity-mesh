package com.antigravity.mesh.network

import com.antigravity.mesh.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface MeshApiService {

    @GET("/health")
    suspend fun checkHealth(
        @Header("X-Mesh-Token") token: String
    ): HealthResponse

    @GET("/system")
    suspend fun getSystemInfo(
        @Header("X-Mesh-Token") token: String
    ): SystemInfoResponse

    @POST("/exec")
    suspend fun executeCommand(
        @Header("X-Mesh-Token") token: String,
        @Body request: ExecRequest
    ): ExecResponse

    @POST("/ask")
    suspend fun askAgent(
        @Header("X-Mesh-Token") token: String,
        @Body request: AskRequest
    ): ExecResponse

    @POST("/pair")
    suspend fun pairNode(
        @Body request: PairRequest
    ): PairResponse

    @POST("/query")
    suspend fun queryFiles(
        @Header("X-Mesh-Token") token: String,
        @Body request: FileQueryRequest
    ): FileQueryResponse

    @POST("/read-file")
    suspend fun readFile(
        @Header("X-Mesh-Token") token: String,
        @Body request: ReadFileRequest
    ): ReadFileResponse

    @GET("/permissions")
    suspend fun checkPermissions(
        @Header("X-Mesh-Token") token: String
    ): PermissionAuditReport

    @POST("/permissions/fix")
    suspend fun fixPermission(
        @Header("X-Mesh-Token") token: String,
        @Body request: PermissionFixRequest
    ): PermissionFixResponse

    // ========================================================================
    // Modern v2.7 API v1 Methods
    // ========================================================================

    @GET("/api/v1/node")
    suspend fun getNodeInfo(
        @Header("X-Mesh-Token") token: String
    ): NodeInfoResponse

    @POST("/api/v1/tasks")
    suspend fun submitTask(
        @Header("X-Mesh-Token") token: String,
        @Body request: SubmitTaskRequest
    ): TaskData

    @GET("/api/v1/tasks")
    suspend fun listTasks(
        @Header("X-Mesh-Token") token: String,
        @Query("limit") limit: Int = 50
    ): List<TaskData>

    @GET("/api/v1/tasks/{id}")
    suspend fun getTask(
        @Header("X-Mesh-Token") token: String,
        @Path("id") taskId: String
    ): TaskData

    @GET("/api/v1/tasks/{id}/logs")
    suspend fun getTaskLogs(
        @Header("X-Mesh-Token") token: String,
        @Path("id") taskId: String,
        @Query("offset") offset: Int = 0
    ): TaskLogsResponse

    @DELETE("/api/v1/tasks/{id}")
    suspend fun cancelTask(
        @Header("X-Mesh-Token") token: String,
        @Path("id") taskId: String
    ): retrofit2.Response<Unit>

    companion object {
        // Fast client for health checks and system info (fast timeout: 4s connect, 5s read)
        val fastClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()
        }

        // Pairing client (fast connect 4s, but 30s read timeout allowing desktop user approval)
        val pairingClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        // Audit client for system permission queries (5s connect, 20s read)
        val auditClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        // Streaming/exec client for long-running AI queries (long read timeout)
        val client: OkHttpClient by lazy {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(logging)
                .build()
        }

        fun create(baseUrl: String, isStreaming: Boolean = false, isPairing: Boolean = false, isAudit: Boolean = false, client: OkHttpClient? = null): MeshApiService {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val httpUrl = normalizedUrl.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Nieprawidłowy adres URL węzła: $normalizedUrl")

            val okClient = client ?: when {
                isStreaming -> Companion.client
                isPairing -> pairingClient
                isAudit -> auditClient
                else -> fastClient
            }

            return Retrofit.Builder()
                .baseUrl(httpUrl)
                .client(okClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MeshApiService::class.java)
        }
    }
}
