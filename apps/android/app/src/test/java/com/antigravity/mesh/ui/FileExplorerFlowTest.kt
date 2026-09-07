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
}
