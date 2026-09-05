package com.antigravity.mesh.network

import com.antigravity.mesh.data.*
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

    companion object {
        fun create(baseUrl: String): MeshApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            return Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MeshApiService::class.java)
        }
    }
}
