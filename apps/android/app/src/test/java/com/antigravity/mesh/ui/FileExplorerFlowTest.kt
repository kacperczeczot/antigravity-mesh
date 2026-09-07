package com.antigravity.mesh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.data.FileItem
import com.antigravity.mesh.data.FileQueryResponse
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.screens.FileExplorerScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2400dp")
class FileExplorerFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testNode = MeshNode(
        id = "node-1",
        name = "Mac Studio",
        host = "100.64.0.1",
        token = "test-token",
        platform = "macOS",
        isOnline = true
    )

    private val sampleFiles = listOf(
        FileItem(name = "src", path = "/project/src", isDir = true, size = 0),
        FileItem(name = "docs", path = "/project/docs", isDir = true, size = 0),
        FileItem(name = "README.md", path = "/project/README.md", isDir = false, size = 1500),
        FileItem(name = "config.yaml", path = "/project/config.yaml", isDir = false, size = 420)
    )

    @Test
    fun testFileExplorerRendersListAndBreadcrumbs() {
        composeTestRule.setContent {
            FileExplorerScreen(
                node = testNode,
                initialPath = "/project",
                onLoadFiles = { path, onResult ->
                    onResult(
                        Result.success(
                            FileQueryResponse(
                                path = path ?: "/project",
                                currentPath = path ?: "/project",
                                items = sampleFiles
                            )
                        )
                    )
                },
                onReadFile = { _, _ -> },
                onAskAgentAboutFile = { _, _ -> },
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify directory entries and files are present
        composeTestRule.onNodeWithText("src").assertIsDisplayed()
        composeTestRule.onNodeWithText("docs").assertIsDisplayed()
        composeTestRule.onNodeWithText("README.md").assertIsDisplayed()
        composeTestRule.onNodeWithText("config.yaml").assertIsDisplayed()

        // 2. Verify breadcrumb shows current path segment
        composeTestRule.onNode(hasText("project", substring = true)).assertIsDisplayed()
    }

    @Test
    fun testFileExplorerSearchFiltering() {
        composeTestRule.setContent {
            FileExplorerScreen(
                node = testNode,
                initialPath = "/project",
                onLoadFiles = { path, onResult ->
                    onResult(
                        Result.success(
                            FileQueryResponse(
                                path = path ?: "/project",
                                currentPath = path ?: "/project",
                                items = sampleFiles
                            )
                        )
                    )
                },
                onReadFile = { _, _ -> },
                onAskAgentAboutFile = { _, _ -> },
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Type "config" into search field
        composeTestRule.onNode(hasSetTextAction()).performTextInput("config")
        composeTestRule.waitForIdle()

        // 2. Only config.yaml should be visible, others filtered out
        composeTestRule.onNodeWithText("config.yaml").assertIsDisplayed()
        composeTestRule.onNodeWithText("README.md").assertDoesNotExist()
        composeTestRule.onNodeWithText("src").assertDoesNotExist()
    }

    @Test
    fun testFileExplorerFileClickOpensViewerDialog() {
        var readFileRequestedPath: String? = null

        composeTestRule.setContent {
            FileExplorerScreen(
                node = testNode,
                initialPath = "/project",
                onLoadFiles = { path, onResult ->
                    onResult(
                        Result.success(
                            FileQueryResponse(
                                path = path ?: "/project",
                                currentPath = path ?: "/project",
                                items = sampleFiles
                            )
                        )
                    )
                },
                onReadFile = { path, onResult ->
                    readFileRequestedPath = path
                },
                onAskAgentAboutFile = { _, _ -> },
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // Click README.md to open in viewer
        composeTestRule.onNodeWithText("README.md").performClick()
        composeTestRule.waitForIdle()

        // Verify read file was triggered for README.md path
        assertEquals("/project/README.md", readFileRequestedPath)
    }

    @Test
    fun testSymlinkToFileShowsBadgeAndOpensViewer() {
        // A symlink to a regular file should open the file viewer, NOT navigate into a directory
        var readFileRequestedPath: String? = null

        val filesWithSymlink = listOf(
            FileItem(
                name = "shortcuts.sh",
                path = "/project/shortcuts.sh",
                isDir = false,
                isSymlink = true,
                symlinkTarget = "/usr/local/bin/shortcuts.sh",
                size = 2048
            ),
            FileItem(name = "README.md", path = "/project/README.md", isDir = false, size = 1500)
        )

        composeTestRule.setContent {
            FileExplorerScreen(
                node = testNode,
                initialPath = "/project",
                onLoadFiles = { path, onResult ->
                    onResult(
                        Result.success(
                            FileQueryResponse(
                                path = path ?: "/project",
                                currentPath = path ?: "/project",
                                items = filesWithSymlink
                            )
                        )
                    )
                },
                onReadFile = { path, _ ->
                    readFileRequestedPath = path
                },
                onAskAgentAboutFile = { _, _ -> },
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // Symlink badge "symlink" should be visible for the symlink item
        composeTestRule.onNodeWithText("symlink").assertIsDisplayed()

        // The symlink name itself should be displayed
        composeTestRule.onNodeWithText("shortcuts.sh").assertIsDisplayed()

        // Clicking the symlink-to-file should open the FileViewerDialog (onReadFile called), NOT navigate
        composeTestRule.onNodeWithText("shortcuts.sh").performClick()
        composeTestRule.waitForIdle()

        // The file viewer should have been triggered, not directory navigation
        assertEquals("/project/shortcuts.sh", readFileRequestedPath)
    }

    @Test
    fun testSymlinkToDirectoryNavigatesIntoDirectory() {
        // A symlink to a directory should navigate into it, just like a real directory
        var lastLoadedPath: String? = null

        val filesWithSymlinkDir = listOf(
            FileItem(
                name = "linked_dir",
                path = "/project/linked_dir",
                isDir = true,   // daemon follows symlink and reports is_dir: true
                isSymlink = true,
                symlinkTarget = "/data/shared",
                size = 0
            )
        )

        composeTestRule.setContent {
            FileExplorerScreen(
                node = testNode,
                initialPath = "/project",
                onLoadFiles = { path, onResult ->
                    lastLoadedPath = path
                    val items = if (path == "/project/linked_dir") {
                        listOf(FileItem(name = "file_inside.txt", path = "/project/linked_dir/file_inside.txt", isDir = false, size = 100))
                    } else {
                        filesWithSymlinkDir
                    }
                    onResult(
                        Result.success(
                            FileQueryResponse(
                                path = path ?: "/project",
                                currentPath = path ?: "/project",
                                items = items
                            )
                        )
                    )
                },
                onReadFile = { _, _ -> },
                onAskAgentAboutFile = { _, _ -> },
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // The symlink-to-dir should be listed with symlink badge
        composeTestRule.onNodeWithText("linked_dir").assertIsDisplayed()

        // Clicking a symlink-to-dir should navigate into it (call loadDirectory, not open viewer)
        composeTestRule.onNodeWithText("linked_dir").performClick()
        composeTestRule.waitForIdle()

        // Should have navigated into the symlinked directory
        assertEquals("/project/linked_dir", lastLoadedPath)

        // Should display the file inside the symlinked directory
        composeTestRule.onNodeWithText("file_inside.txt").assertIsDisplayed()
    }

    @Test
    fun testFileExplorerOpenFolderFromViewerDialogNavigatesToDirectory() {
        var lastLoadedPath: String? = null

        composeTestRule.setContent {
            FileExplorerScreen(
                node = testNode,
                initialPath = "/project",
                onLoadFiles = { path, onResult ->
                    lastLoadedPath = path
                    if (path == "/home") {
                        onResult(
                            Result.success(
                                FileQueryResponse(
                                    path = "/home",
                                    currentPath = "/home",
                                    items = listOf(
                                        FileItem(name = "user", type = "dir", isDir = true)
                                    )
                                )
                            )
                        )
                    } else {
                        onResult(
                            Result.success(
                                FileQueryResponse(
                                    path = "/project",
                                    currentPath = "/project",
                                    items = listOf(
                                        FileItem(name = "home", type = "file", isDir = false, size = 25)
                                    )
                                )
                            )
                        )
                    }
                },
                onReadFile = { path, onResult ->
                    // Return isDir = true when reading /project/home
                    onResult(
                        Result.success(
                            com.antigravity.mesh.data.ReadFileResponse(
                                path = "/home",
                                name = "home",
                                isDir = true,
                                error = "'/home' jest katalogiem, a nie plikiem"
                            )
                        )
                    )
                },
                onAskAgentAboutFile = { _, _ -> },
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Initially on /project, click on "home" (listed as file)
        composeTestRule.onNodeWithText("home").performClick()
        composeTestRule.waitForIdle()

        // 2. FileViewerDialog opens and recognizes it is a directory
        composeTestRule.onNodeWithText("To jest katalog").assertIsDisplayed()

        // 3. Click "Otwórz w Eksploratorze Plików" button
        val openFolderBtn = composeTestRule.onNodeWithText("Otwórz w Eksploratorze Plików")
        openFolderBtn.assertIsDisplayed()
        openFolderBtn.performClick()
        composeTestRule.waitForIdle()

        // 4. Verify that FileExplorerScreen requested loading /home
        assertEquals("/home", lastLoadedPath)
        // And user is now inside /home seeing "user"
        composeTestRule.onNodeWithText("user").assertIsDisplayed()
    }
}

