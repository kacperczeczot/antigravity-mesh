package com.antigravity.mesh.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class LanScanner {

    suspend fun scanSubnet(subnetPrefix: String = "192.168.68", port: Int = 8888): List<String> =
        withContext(Dispatchers.IO) {
            val jobs = (1..254).map { hostPart ->
                async {
                    val ip = "$subnetPrefix.$hostPart"
                    if (isPortOpen(ip, port, timeoutMs = 250)) {
                        ip
                    } else {
                        null
                    }
                }
            }
            jobs.awaitAll().filterNotNull()
        }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
