package com.antigravity.mesh.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

class LanScanner {

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddr = addr.hostAddress
                        if (hostAddr != null && !hostAddr.startsWith("127.")) {
                            return hostAddr
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return null
    }

    suspend fun scanSubnet(subnetPrefix: String? = null, port: Int = 8888): List<String> =
        withContext(Dispatchers.IO) {
            val prefix = subnetPrefix ?: getLocalIpAddress()?.substringBeforeLast(".") ?: "192.168.1"
            val semaphore = kotlinx.coroutines.sync.Semaphore(32)
            val jobs = (1..254).map { hostPart ->
                async {
                    semaphore.withPermit {
                        val ip = "$prefix.$hostPart"
                        if (isPortOpen(ip, port, timeoutMs = 500)) {
                            ip
                        } else {
                            null
                        }
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
