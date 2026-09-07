package com.antigravity.mesh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.data.ReadFileResponse
import com.antigravity.mesh.ui.components.FileViewerDialog
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
        composeTestRule.onNodeWithText("Pobierz plik do podglądu").assertIsDisplayed()
    }
}
