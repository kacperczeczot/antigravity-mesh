package com.antigravity.mesh.network

import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class MeshApiServiceTest {

    @Test
    fun testValidBaseUrlCreation() {
        val service = MeshApiService.create("http://192.168.1.100:8888")
        assertNotNull(service)

        val serviceTrailing = MeshApiService.create("http://10.0.0.5:8888/")
        assertNotNull(serviceTrailing)

        val serviceFast = MeshApiService.create("http://localhost:8888", isStreaming = false)
        assertNotNull(serviceFast)

        val serviceStream = MeshApiService.create("http://localhost:8888", isStreaming = true)
        assertNotNull(serviceStream)
    }

    @Test
    fun testInvalidBaseUrlThrowsExpectedException() {
        val invalidUrls = listOf(
            "htp://invalid-scheme",
            "not a url",
            "http://",
            ""
        )

        for (invalid in invalidUrls) {
            try {
                MeshApiService.create(invalid)
                fail("Expected IllegalArgumentException for url: $invalid")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }
}
