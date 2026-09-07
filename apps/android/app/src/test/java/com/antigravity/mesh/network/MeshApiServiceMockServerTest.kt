package com.antigravity.mesh.network

import com.antigravity.mesh.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class MeshApiServiceMockServerTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var apiService: MeshApiService

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/").toString()
        apiService = MeshApiService.create(baseUrl)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun testCheckHealthSuccess() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","node":"Mac-Node","platform":"macOS"}""")
        )

        val response = apiService.checkHealth("test-token")
        val recordedRequest = mockServer.takeRequest()

        assertEquals("/health", recordedRequest.path)
        assertEquals("test-token", recordedRequest.getHeader("X-Mesh-Token"))
        assertEquals("ok", response.status)
        assertEquals("Mac-Node", response.node)
        assertEquals("macOS", response.platform)
    }

    @Test
    fun testCheckHealthUnauthorized401() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"Invalid or expired token"}""")
        )

        try {
            apiService.checkHealth("bad-token")
            fail("Oczekiwano HttpException dla kodu 401")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
    }

    @Test
    fun testExecuteCommandServerPanic500() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""Internal Server Error: panic in worker thread""")
        )

        try {
            apiService.executeCommand("test-token", ExecRequest(cmd = "cat /proc/cpuinfo"))
            fail("Oczekiwano HttpException dla kodu 500")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
        }
    }

    @Test
    fun testAskAgentPayloadSerialization() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"returncode":0,"stdout":"Agent odpowiedział pomyślnie.","conversation_id":"conv-123"}""")
        )

        val askReq = AskRequest(
            question = "Wyszukaj błędy w logach systemowych.",
            autoApprove = true,
            conversationId = "conv-123"
        )
        val response = apiService.askAgent("test-token", askReq)
        val recorded = mockServer.takeRequest()

        assertEquals("/ask", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("Wyszukaj błędy"))
        assertEquals(0, response.returncode)
        assertEquals("conv-123", response.conversationId)
        assertEquals("Agent odpowiedział pomyślnie.", response.stdout)
    }

    @Test
    fun testQueryFilesWithSpecialAndUnicodeCharacters() = runBlocking {
        val json = """
            {
                "path": "/dane/projekty/żółć i gęśl",
                "current_path": "/dane/projekty/żółć i gęśl",
                "items": [
                    {"name":"plik_1.txt","type":"file","size":2048,"modified":1700000000,"path":"/dane/projekty/żółć i gęśl/plik_1.txt"},
                    {"name":"katalog_podrzędny","type":"dir","size":0,"modified":1700000100,"path":"/dane/projekty/żółć i gęśl/katalog_podrzędny"}
                ]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(json)
        )

        val response = apiService.queryFiles("test-token", FileQueryRequest(path = "/dane/projekty/żółć i gęśl"))
        assertEquals(2, response.items.size)
        assertEquals("plik_1.txt", response.items[0].name)
        assertFalse(response.items[0].isDirectory)
        assertEquals(2048L, response.items[0].size)
        assertEquals("2.0 KB", response.items[0].formattedSize)

        assertEquals("katalog_podrzędny", response.items[1].name)
        assertTrue(response.items[1].isDirectory)
        assertEquals("", response.items[1].formattedSize)
    }

    @Test
    fun testReadFileWithUtf8Content() = runBlocking {
        val sampleCode = """
            fun main() {
                println("Cześć świecie! 🚀")
            }
        """.trimIndent()

        val json = """
            {
                "path": "/project/Main.kt",
                "content": ${com.google.gson.Gson().toJson(sampleCode)},
                "size": ${sampleCode.toByteArray().size}
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(json)
        )

        val response = apiService.readFile("test-token", ReadFileRequest(path = "/project/Main.kt"))
        assertNotNull(response.content)
        assertTrue(response.content!!.contains("Cześć świecie! 🚀"))
    }

    @Test
    fun testMalformedJsonResponseThrowsException() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok", invalid_json_here...""")
        )

        try {
            apiService.checkHealth("test-token")
            fail("Oczekiwano błędu parsowania niepoprawnego formatu JSON")
        } catch (e: Exception) {
            // Success: JSON syntax exception caught
            assertTrue(e is com.google.gson.JsonSyntaxException || e is java.io.IOException)
        }
    }
}
