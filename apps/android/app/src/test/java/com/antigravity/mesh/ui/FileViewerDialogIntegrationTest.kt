package com.antigravity.mesh.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.data.ReadFileResponse
import com.antigravity.mesh.ui.components.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileViewerDialogIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFileViewerDialogRendersContentAndMarkdownToggle() {
        var isDismissed = false
        val sampleMarkdown = """
            # Nagłówek Dokumentu
            • 🧠 [**`00_fundamenty/`**](data/00_fundamenty/00_indeks.md) — Profil
            Tabela testowa:
            | Klucz | Wartość |
            | :--- | :---: |
            | Status | OK |
        """.trimIndent()

        composeTestRule.setContent {
            FileViewerDialog(
                filePath = "/Volumes/MAC_STORAGE_APFS/Developer/GitHub/antigravity-mesh/README.md",
                fileName = "README.md",
                fileSize = "2.4 KB",
                onDismiss = { isDismissed = true },
                onReadFile = { path, onResult ->
                    onResult(
                        Result.success(
                            ReadFileResponse(
                                path = path,
                                name = "README.md",
                                content = sampleMarkdown,
                                isDir = false,
                                size = sampleMarkdown.length.toLong(),
                                mimeType = "text/markdown"
                            )
                        )
                    )
                }
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify header displays file name and size
        composeTestRule.onNodeWithText("README.md").assertIsDisplayed()
        composeTestRule.onNode(hasText("2.4 KB", substring = true)).assertIsDisplayed()

        // 2. Verify rendered markdown content is present
        composeTestRule.onNodeWithText("Nagłówek Dokumentu").assertIsDisplayed()

        // 3. Verify toggle button "Pokaż kod" is present and can be clicked
        val toggleButton = composeTestRule.onNodeWithText("Pokaż kod")
        toggleButton.assertIsDisplayed()
        toggleButton.performClick()

        composeTestRule.waitForIdle()

        // 4. After clicking "Pokaż kod", button text changes to "Podgląd"
        composeTestRule.onNodeWithText("Podgląd").assertIsDisplayed()

        // 5. Code view line numbers should be displayed
        composeTestRule.onNode(hasText("1", substring = true)).assertIsDisplayed()
    }

    @Test
    fun testFileViewerDialogDirectoryNavigation() {
        var openedFolderInExplorer: String? = null
        val dirPath = "/Volumes/MAC_STORAGE_APFS/Developer/GitHub/antigravity-mesh/data"

        composeTestRule.setContent {
            FileViewerDialog(
                filePath = dirPath,
                fileName = "data",
                onDismiss = {},
                onReadFile = { path, onResult ->
                    onResult(
                        Result.success(
                            ReadFileResponse(
                                path = path,
                                name = "data",
                                content = "",
                                isDir = true,
                                size = 0L,
                                mimeType = "inode/directory"
                            )
                        )
                    )
                },
                onOpenFolderInExplorer = { openedFolderInExplorer = it }
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify directory state is rendered
        composeTestRule.onNodeWithText("To jest katalog").assertIsDisplayed()
        // Path appears in header and in directory center card
        composeTestRule.onAllNodes(hasText(dirPath, substring = true)).assertCountEquals(2)

        // 2. Verify "Otwórz w Eksploratorze Plików" button is active and triggers callback
        val openExplorerBtn = composeTestRule.onNodeWithText("Otwórz w Eksploratorze Plików")
        openExplorerBtn.assertIsDisplayed()
        openExplorerBtn.performClick()

        assertEquals(dirPath, openedFolderInExplorer)
    }

    @Test
    fun testFileViewerDialogErrorRetryFlow() {
        var readAttempts = 0
        var shouldSucceed = false

        composeTestRule.setContent {
            FileViewerDialog(
                filePath = "/test/doc.txt",
                fileName = "doc.txt",
                onDismiss = {},
                onReadFile = { path, onResult ->
                    readAttempts++
                    if (shouldSucceed) {
                        onResult(
                            Result.success(
                                ReadFileResponse(
                                    path = path,
                                    name = "doc.txt",
                                    content = "Udało się odczytać!",
                                    isDir = false,
                                    size = 18L,
                                    mimeType = "text/plain"
                                )
                            )
                        )
                    } else {
                        onResult(Result.failure(Exception("Brak uprawnień do pliku")))
                    }
                }
            )
        }

        // 1. First attempt fails, shows error message
        composeTestRule.onNodeWithText("Brak uprawnień do pliku").assertIsDisplayed()
        assertEquals(1, readAttempts)

        // 2. Click "Spróbuj ponownie" with recovery
        shouldSucceed = true
        composeTestRule.onNodeWithText("Spróbuj ponownie").performClick()

        // 3. Second attempt succeeds and displays content
        composeTestRule.onNodeWithText("Udało się odczytać!").assertIsDisplayed()
        assertEquals(2, readAttempts)
    }

    @Test
    fun testFileViewerDialogBinaryFileCard() {
        composeTestRule.setContent {
            FileViewerDialog(
                filePath = "/test/installer.dmg",
                fileName = "installer.dmg",
                fileSize = "50 MB",
                onDismiss = {},
                onReadFile = { path, onResult ->
                    onResult(
                        Result.success(
                            ReadFileResponse(
                                path = path,
                                name = "installer.dmg",
                                content = "[Zawartość binarna / podgląd tekstowy niedostępny]",
                                isBinary = true,
                                isDir = false,
                                size = 52428800L,
                                mimeType = "application/x-apple-diskimage"
                            )
                        )
                    )
                }
            )
        }

        composeTestRule.waitForIdle()

        // Should display the binary file card instead of raw text
        composeTestRule.onNodeWithText("Plik binarny").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pobierz plik do podglądu").assertIsDisplayed()
    }

    @Test
    fun testFileViewerDialogKrzaczkiFallbackToBinaryCard() {
        val krzaczkiContent = "PNG\r\n\u001a\n\u0000\u0000\u0000\rIHDR\u0000\u0000\u0001\u0000\uFFFD\uFFFD\uFFFD\uFFFD"

        composeTestRule.setContent {
            FileViewerDialog(
                filePath = "/test/unknown_format",
                fileName = "unknown_format",
                fileSize = "1 KB",
                onDismiss = {},
                onReadFile = { path, onResult ->
                    onResult(
                        Result.success(
                            ReadFileResponse(
                                path = path,
                                name = "unknown_format",
                                content = krzaczkiContent,
                                isBinary = false, // Mistakenly reported as false by server
                                isDir = false,
                                size = krzaczkiContent.length.toLong(),
                                mimeType = null
                            )
                        )
                    )
                }
            )
        }

        composeTestRule.waitForIdle()

        // isLikelyBinary should intercept the krzaczki and display GenericBinaryCard
        composeTestRule.onNodeWithText("Plik binarny").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pobierz plik do podglądu").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testFileViewerDialogBottomActionsAllDisplayedAndClickable() {
        var askedAgentPath: String? = null
        var openedExplorerPath: String? = null

        composeTestRule.setContent {
            FileViewerDialog(
                filePath = "/test/document.md",
                fileName = "document.md",
                fileSize = "500 B",
                onDismiss = {},
                onReadFile = { path, onResult ->
                    onResult(
                        Result.success(
                            ReadFileResponse(
                                path = path,
                                name = "document.md",
                                content = "# Test Content\nSome markdown line.",
                                isDir = false,
                                size = 30L,
                                mimeType = "text/markdown"
                            )
                        )
                    )
                },
                onAskAgentAboutFile = { path, _ ->
                    askedAgentPath = path
                },
                onOpenFolderInExplorer = { path ->
                    openedExplorerPath = path
                }
            )
        }

        composeTestRule.waitForIdle()

        // Verify bottom action bar has all 4 action buttons visible
        val toggleCodeBtn = composeTestRule.onNodeWithText("Pokaż kod")
        toggleCodeBtn.assertIsDisplayed()

        val copyBtn = composeTestRule.onNodeWithText("Kopiuj")
        copyBtn.assertIsDisplayed()

        val explorerBtn = composeTestRule.onNodeWithText("Eksplorator")
        explorerBtn.assertIsDisplayed()

        val askAgentBtn = composeTestRule.onNodeWithText("Zapytaj agenta")
        askAgentBtn.assertIsDisplayed()

        // Verify buttons can be clicked and callbacks are dispatched
        explorerBtn.performClick()
        assertEquals("/test", openedExplorerPath)

        askAgentBtn.performClick()
        assertEquals("/test/document.md", askedAgentPath)
    }

    @Test
    fun testFileViewerDialogLongContentPreservesBottomActions() {
        val longContent = (1..300).joinToString("\n") { "Line $it: Lorem ipsum dolor sit amet consectetur adipiscing elit" }

        composeTestRule.setContent {
            FileViewerDialog(
                filePath = "/test/long_log.txt",
                fileName = "long_log.txt",
                fileSize = "15 KB",
                onDismiss = {},
                onReadFile = { path, onResult ->
                    onResult(
                        Result.success(
                            ReadFileResponse(
                                path = path,
                                name = "long_log.txt",
                                content = longContent,
                                isDir = false,
                                size = longContent.length.toLong(),
                                mimeType = "text/plain"
                            )
                        )
                    )
                },
                onOpenFolderInExplorer = {},
                onAskAgentAboutFile = { _, _ -> }
            )
        }

        composeTestRule.waitForIdle()

        // Even with 300 lines of content, bottom action buttons must be displayed and not pushed out of view
        composeTestRule.onNodeWithText("Kopiuj").assertIsDisplayed()
        composeTestRule.onNodeWithText("Eksplorator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zapytaj agenta").assertIsDisplayed()
    }

    @Test
    fun testDocumentAndVideoCategoryDetection() {
        assertEquals(PreviewCategory.DOCUMENT, detectPreviewCategory("umowa.docx", false, null))
        assertEquals(PreviewCategory.DOCUMENT, detectPreviewCategory("arkusz.xlsx", false, null))
        assertEquals(PreviewCategory.DOCUMENT, detectPreviewCategory("slajdy.pptx", false, null))
        assertEquals(PreviewCategory.DOCUMENT, detectPreviewCategory("notatka.odt", false, null))
        assertEquals(PreviewCategory.DOCUMENT, detectPreviewCategory("dokument.rtf", false, null))
        assertEquals(PreviewCategory.DOCUMENT, detectPreviewCategory("ksiazka.epub", false, null))

        assertEquals(PreviewCategory.VIDEO, detectPreviewCategory("nagranie.mp4", false, null))
        assertEquals(PreviewCategory.VIDEO, detectPreviewCategory("film.mkv", false, null))
        assertEquals(PreviewCategory.VIDEO, detectPreviewCategory("klip.mov", false, null))
        assertEquals(PreviewCategory.VIDEO, detectPreviewCategory("wideo.webm", false, null))

        assertEquals(PreviewCategory.PDF, detectPreviewCategory("plik.pdf", false, null))
        assertEquals(PreviewCategory.AUDIO, detectPreviewCategory("utwor.mp3", false, null))
        assertEquals(PreviewCategory.IMAGE, detectPreviewCategory("obrazek.png", false, null))
        assertEquals(PreviewCategory.GENERIC_BINARY, detectPreviewCategory("paczka.zip", false, null))
        assertEquals(PreviewCategory.GENERIC_BINARY, detectPreviewCategory("aplikacja.apk", true, null))
    }

    @Test
    fun testResolveMimeTypeFallback() {
        // Fallback for docx when server sends null or octet-stream
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            resolveMimeType("dokument.docx", null)
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            resolveMimeType("dokument.docx", "application/octet-stream")
        )

        // Fallback for xlsx
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            resolveMimeType("arkusz.xlsx", "application/octet-stream")
        )

        // Fallback for pptx
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            resolveMimeType("prezentacja.pptx", null)
        )

        // Fallback for apk
        assertEquals(
            "application/vnd.android.package-archive",
            resolveMimeType("antigravity.apk", null)
        )

        // Fallback for mp4
        assertEquals(
            "video/mp4",
            resolveMimeType("film.mp4", null)
        )
    }

    @Test
    fun testDocumentViewerCardRendersAndActionClickable() {
        var openInAppClicked = false
        val tempFile = java.io.File.createTempFile("test_doc", ".docx").apply { deleteOnExit() }

        composeTestRule.setContent {
            DocumentViewerCard(
                fileName = "WaznaUmowa.docx",
                fileSize = "45 KB",
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                cachedFile = tempFile,
                onOpenInApp = { openInAppClicked = true },
                onAskAgentAboutFile = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify file name and label
        composeTestRule.onNodeWithText("WaznaUmowa.docx").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dokument Microsoft Word").assertIsDisplayed()
        composeTestRule.onNode(hasText("45 KB", substring = true)).assertIsDisplayed()

        // 2. Click "Otwórz w aplikacji"
        val openBtn = composeTestRule.onNodeWithText("Otwórz w aplikacji")
        openBtn.assertIsDisplayed()
        openBtn.performClick()

        assertTrue("Kliknięcie w 'Otwórz w aplikacji' powinno wywołać akcję", openInAppClicked)
    }

    @Test
    fun testFileIconsAndColorsForWebAndCode() {
        // Web files
        assertEquals(androidx.compose.material.icons.Icons.Default.Html, getFileIcon("index.html"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFE44D26), getFileIconColor("index.html"))

        assertEquals(androidx.compose.material.icons.Icons.Default.Css, getFileIcon("style.css"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF264DE4), getFileIconColor("style.css"))

        assertEquals(androidx.compose.material.icons.Icons.Default.Javascript, getFileIcon("script.js"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF7DF1E), getFileIconColor("script.js"))

        // TypeScript & JSX
        assertEquals(BrandIcons.TypeScript, getFileIcon("app.ts"))
        assertEquals(BrandIcons.TypeScript, getFileIcon("component.tsx"))
        assertEquals(androidx.compose.material.icons.Icons.Default.Code, getFileIcon("component.jsx"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3178C6), getFileIconColor("app.ts"))

        // Rust, Kotlin, Go
        assertEquals(BrandIcons.Rust, getFileIcon("main.rs"))
        assertEquals(BrandIcons.Kotlin, getFileIcon("App.kt"))
        assertEquals(BrandIcons.Kotlin, getFileIcon("build.gradle.kts"))
        assertEquals(BrandIcons.Go, getFileIcon("server.go"))

        // Shell & Terminal
        assertEquals(androidx.compose.material.icons.Icons.Default.Terminal, getFileIcon("deploy.sh"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF4EAA25), getFileIconColor("deploy.sh"))

        // Database & JSON Data
        assertEquals(androidx.compose.material.icons.Icons.Default.Storage, getFileIcon("schema.sql"))
        assertEquals(androidx.compose.material.icons.Icons.Default.DataObject, getFileIcon("data.json"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFBBF24), getFileIconColor("data.json"))

        // Python
        assertEquals(BrandIcons.Python, getFileIcon("script.py"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3776AB), getFileIconColor("script.py"))
        assertEquals(BrandIcons.Python, getFileIcon("module.pyw"))
        assertEquals(BrandIcons.Python, getFileIcon("types.pyi"))

        // Special filenames
        assertEquals(BrandIcons.Docker, getFileIcon("Dockerfile"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF2496ED), getFileIconColor("Dockerfile"))

        assertEquals(BrandIcons.Git, getFileIcon(".gitignore"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF05032), getFileIconColor(".gitignore"))
    }
}

