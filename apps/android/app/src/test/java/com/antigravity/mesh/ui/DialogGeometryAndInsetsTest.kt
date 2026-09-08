package com.antigravity.mesh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.antigravity.mesh.data.AccessibilityCheck
import com.antigravity.mesh.data.FullDiskAccessCheck
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.data.PermissionAuditReport
import com.antigravity.mesh.ui.components.PermissionsAuditDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Geometric layout and insets verification tests.
 * Validates that dialogs never overlap system navigation bars,
 * never clip action buttons, and remain properly centered in landscape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialogGeometryAndInsetsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testNode = MeshNode(
        id = "node-test",
        name = "Test Mac",
        host = "100.64.0.10",
        token = "test-token",
        platform = "macOS",
        isOnline = true
    )

    private val sampleReport = PermissionAuditReport(
        platform = "macos",
        overallStatus = "all_granted",
        allGranted = true,
        summary = "Wszystkie uprawnienia nadane",
        accessibility = AccessibilityCheck(granted = true, status = "granted"),
        fullDiskAccess = FullDiskAccessCheck(granted = true, status = "granted")
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp") // Standard modern phone in portrait (e.g. Galaxy S23)
    fun testPortraitDialogHasActionButtonsVisibleAndAccessible() {
        composeTestRule.setContent {
            PermissionsAuditDialog(
                node = testNode,
                report = sampleReport,
                isLoading = false,
                errorMessage = null,
                onRefresh = {},
                onDismiss = {}
            )
        }

        composeTestRule.waitForIdle()

        val closeBtn = composeTestRule.onNodeWithText("Zamknij")
        closeBtn.assertIsDisplayed()
        val retryBtn = composeTestRule.onNodeWithText("Uruchom test ponownie")
        retryBtn.assertIsDisplayed()

        val closeBounds = closeBtn.getBoundsInRoot()
        val retryBounds = retryBtn.getBoundsInRoot()

        println("PORTRAIT closeBounds: top=${closeBounds.top}, bottom=${closeBounds.bottom}, left=${closeBounds.left}, right=${closeBounds.right}")
        println("PORTRAIT retryBounds: top=${retryBounds.top}, bottom=${retryBounds.bottom}, left=${retryBounds.left}, right=${retryBounds.right}")

        // Verify Header position (no 100dp gaping hole revealing background screen)
        val header = composeTestRule.onNodeWithText("Audyt Uprawnień i Diagnostyka")
        header.assertIsDisplayed()
        val headerBounds = header.getBoundsInRoot()
        assertTrue("Header top (${headerBounds.top}) must start near status bar (<= 55dp)", headerBounds.top <= 55.dp)

        // Verify that ALL 4 permission items are displayed and rendered
        composeTestRule.onNodeWithText("Dostępność (Accessibility)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pełny dostęp do dysku (FDA)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Podpis cyfrowy & Kwarantanna").assertIsDisplayed()
        composeTestRule.onNodeWithText("Wykonywanie procesów potomnych").assertIsDisplayed()

        // Card starts 16dp below safe top and ends 16dp above safe bottom
        assertTrue("Close button bottom (${closeBounds.bottom}) must leave safe margin from bottom edge", closeBounds.bottom <= 915.dp - 16.dp)
        assertTrue("Close button bottom (${closeBounds.bottom}) must fill vertical space (>= 810dp)", closeBounds.bottom >= 810.dp)
        assertTrue("Retry button bottom (${retryBounds.bottom}) must leave safe margin from bottom edge", retryBounds.bottom <= 915.dp - 16.dp)
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land") // Standard modern phone in landscape (e.g. Galaxy S23 rotated)
    fun testLandscapeDialogIsCenteredAndNotClipped() {
        composeTestRule.setContent {
            PermissionsAuditDialog(
                node = testNode,
                report = sampleReport,
                isLoading = false,
                errorMessage = null,
                onRefresh = {},
                onDismiss = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Both buttons must be displayed and clickable without scrolling required
        val closeBtn = composeTestRule.onNodeWithText("Zamknij")
        closeBtn.assertIsDisplayed()
        val retryBtn = composeTestRule.onNodeWithText("Uruchom test ponownie")
        retryBtn.assertIsDisplayed()

        val closeBounds = closeBtn.getBoundsInRoot()
        val retryBounds = retryBtn.getBoundsInRoot()

        println("LANDSCAPE closeBounds: top=${closeBounds.top}, bottom=${closeBounds.bottom}, left=${closeBounds.left}, right=${closeBounds.right}")
        println("LANDSCAPE retryBounds: top=${retryBounds.top}, bottom=${retryBounds.bottom}, left=${retryBounds.left}, right=${retryBounds.right}")

        // 2. In landscape (915dp width), card has uniform 16dp horizontal margin
        assertTrue(
            "Close button right (${closeBounds.right}) must leave safe margin from right edge",
            closeBounds.right <= 915.dp - 16.dp
        )
        // And card must expand into landscape width (>= 750dp)
        assertTrue(
            "Close button right (${closeBounds.right}) must expand into landscape width (>= 750dp)",
            closeBounds.right >= 750.dp
        )

        // 3. Buttons must sit fully within the 412dp screen height with 12dp safe margin
        assertTrue(
            "Close button bottom (${closeBounds.bottom}) must leave safe margin from bottom edge (<= 400dp)",
            closeBounds.bottom <= 412.dp - 12.dp
        )
        assertTrue(
            "Close button bottom (${closeBounds.bottom}) must maximize vertical space (>= 360dp)",
            closeBounds.bottom >= 360.dp
        )

        // 4. Header title must start near top of screen (<= 25dp) to maximize space
        val header = composeTestRule.onNodeWithText("Audyt Uprawnień i Diagnostyka")
        header.assertIsDisplayed()
        val headerBounds = header.getBoundsInRoot()
        println("LANDSCAPE headerBounds: top=${headerBounds.top}, bottom=${headerBounds.bottom}, left=${headerBounds.left}")
        assertTrue(
            "Header top (${headerBounds.top}) must start near top of screen (<= 30dp)",
            headerBounds.top <= 30.dp
        )
        // Header left must not have a massive 180px void (dialog start 12dp + padding 14dp + icon 28dp + spacer 8dp = 62dp)
        assertTrue(
            "Header left (${headerBounds.left}) must not have huge artificial gap (<= 70dp)",
            headerBounds.left <= 70.dp
        )

        // 5. CRITICAL: All 4 permission items MUST BE VISIBLE in landscape without scrolling!
        composeTestRule.onNodeWithText("Dostępność (Accessibility)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pełny dostęp do dysku (FDA)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Podpis cyfrowy & Kwarantanna").assertIsDisplayed()
        composeTestRule.onNodeWithText("Wykonywanie procesów potomnych").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun testFileViewerPortraitDialogHasCloseButtonAboveNavBar() {
        composeTestRule.setContent {
            com.antigravity.mesh.ui.components.FileViewerDialog(
                filePath = "test.txt",
                onDismiss = {},
                onReadFile = { _, onResult ->
                    onResult(Result.success(com.antigravity.mesh.data.ReadFileResponse(content = "hello", size = 5)))
                }
            )
        }
        composeTestRule.waitForIdle()
        val closeBtn = composeTestRule.onNodeWithContentDescription("Zamknij")
        closeBtn.assertIsDisplayed()
        val closeBounds = closeBtn.getBoundsInRoot()
        println("FILE_VIEWER PORTRAIT closeBounds: top=${closeBounds.top}, bottom=${closeBounds.bottom}, right=${closeBounds.right}")

        // Header must start near status bar
        assertTrue("Header close button must start near status bar (<= 65dp)", closeBounds.top <= 65.dp)

        // File name must be displayed
        composeTestRule.onAllNodesWithText("test.txt").onFirst().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    fun testFileViewerLandscapeDialogIsInsideSafeBounds() {
        composeTestRule.setContent {
            com.antigravity.mesh.ui.components.FileViewerDialog(
                filePath = "test.txt",
                onDismiss = {},
                onReadFile = { _, onResult ->
                    onResult(Result.success(com.antigravity.mesh.data.ReadFileResponse(content = "hello", size = 5)))
                }
            )
        }
        composeTestRule.waitForIdle()
        val closeBtn = composeTestRule.onNodeWithContentDescription("Zamknij")
        closeBtn.assertIsDisplayed()
        val closeBounds = closeBtn.getBoundsInRoot()
        println("FILE_VIEWER LANDSCAPE closeBounds: top=${closeBounds.top}, bottom=${closeBounds.bottom}, right=${closeBounds.right}")

        // In landscape (915dp), header starts near top with 12dp safe margin
        assertTrue("Header close button must start near top of screen (<= 45dp)", closeBounds.top <= 45.dp)

        // Close button on header must expand into landscape (>= 750dp) and leave safe margin from right edge
        assertTrue(
            "Close button right (${closeBounds.right}) must expand into landscape width (>= 750dp)",
            closeBounds.right >= 750.dp
        )
        assertTrue(
            "Close button right (${closeBounds.right}) must leave safe margin from right edge",
            closeBounds.right <= 915.dp - 16.dp
        )

        // File name must be displayed
        composeTestRule.onAllNodesWithText("test.txt").onFirst().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp") // Compact phone portrait
    fun testCompactPortraitDialogButtonsAboveNavBar() {
        composeTestRule.setContent {
            PermissionsAuditDialog(
                node = testNode,
                report = sampleReport,
                isLoading = false,
                errorMessage = null,
                onRefresh = {},
                onDismiss = {}
            )
        }
        composeTestRule.waitForIdle()
        val closeBtn = composeTestRule.onNodeWithText("Zamknij")
        closeBtn.assertIsDisplayed()
        val closeBounds = closeBtn.getBoundsInRoot()
        println("COMPACT PORTRAIT closeBounds: bottom=${closeBounds.bottom} (screen=640dp)")
        // Card has 16dp safe margin from bottom
        assertTrue("Close button must leave safe margin from bottom in compact portrait", closeBounds.bottom <= 640.dp - 16.dp)
        assertTrue("Close button must fill height in compact portrait", closeBounds.bottom >= 550.dp)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land") // Compact phone landscape (height only 360dp)
    fun testCompactLandscapeDialogFitsOnScreen() {
        composeTestRule.setContent {
            PermissionsAuditDialog(
                node = testNode,
                report = sampleReport,
                isLoading = false,
                errorMessage = null,
                onRefresh = {},
                onDismiss = {}
            )
        }
        composeTestRule.waitForIdle()
        val closeBtn = composeTestRule.onNodeWithText("Zamknij")
        closeBtn.assertIsDisplayed()
        val closeBounds = closeBtn.getBoundsInRoot()
        println("COMPACT LANDSCAPE closeBounds: right=${closeBounds.right}, bottom=${closeBounds.bottom} (screen=640x360)")
        // Card has 16dp safe margin from right
        assertTrue("Close button must leave safe margin from right edge (< 640dp)", closeBounds.right <= 640.dp - 16.dp)
        // Must fit within 360dp height with 12dp safe margin
        assertTrue("Close button must fit within 360dp height", closeBounds.bottom <= 360.dp - 12.dp)
    }
}
