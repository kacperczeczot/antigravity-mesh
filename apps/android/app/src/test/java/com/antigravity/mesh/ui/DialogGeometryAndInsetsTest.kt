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

        // In portrait 915dp, navigation bar is at bottom 48dp (867dp..915dp).
        // Buttons must be fully above 867dp, BUT card must NOT float in the air with a giant 115dp void!
        assertTrue("Close button bottom (${closeBounds.bottom}) must be ABOVE nav bar (< 867dp)", closeBounds.bottom <= 855.dp)
        assertTrue("Close button bottom (${closeBounds.bottom}) must fill vertical space (>= 810dp)", closeBounds.bottom >= 810.dp)
        assertTrue("Retry button bottom (${retryBounds.bottom}) must be ABOVE nav bar (< 867dp)", retryBounds.bottom <= 855.dp)
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

        // 2. In landscape (915dp width), side navigation bar is at the right (867dp..915dp).
        // Close button (which is on the right) MUST NOT be inside the navigation bar!
        assertTrue(
            "Close button right (${closeBounds.right}) must be strictly to the LEFT of the side nav bar (< 867dp)",
            closeBounds.right <= 855.dp
        )
        // And card must NOT be squeezed into a tiny 560dp block with 180dp dead margins!
        assertTrue(
            "Close button right (${closeBounds.right}) must expand into landscape width (>= 750dp)",
            closeBounds.right >= 750.dp
        )

        // 3. Buttons must sit fully within the 412dp screen height, using vertical space
        assertTrue(
            "Close button bottom (${closeBounds.bottom}) must be within screen height (<= 406dp)",
            closeBounds.bottom <= 406.dp
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
            "Header top (${headerBounds.top}) must start near top of screen (<= 25dp)",
            headerBounds.top <= 25.dp
        )
        // Header left must not have a massive 180px void (dialog start 12dp + padding 14dp + icon 28dp + spacer 8dp = 62dp)
        assertTrue(
            "Header left (${headerBounds.left}) must not have huge artificial gap (<= 65dp)",
            headerBounds.left <= 65.dp
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

        // In landscape (915dp), header must start near top of screen
        assertTrue("Header close button must start near top of screen (<= 35dp)", closeBounds.top <= 35.dp)

        // Close button on header must expand into landscape (>= 750dp) but be strictly left of nav bar (<= 855dp)
        assertTrue(
            "Close button right (${closeBounds.right}) must expand into landscape width (>= 750dp)",
            closeBounds.right >= 750.dp
        )
        assertTrue(
            "Close button right (${closeBounds.right}) must be strictly to the left of nav bar (<= 855dp)",
            closeBounds.right <= 855.dp
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
        // Navigation bar in 640dp height starts at 592dp (640 - 48).
        assertTrue("Close button must be above nav bar in compact portrait", closeBounds.bottom <= 585.dp)
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
        // In 640dp landscape, side nav bar is at 592dp..640dp.
        assertTrue("Close button must be to the left of side nav bar (< 592dp)", closeBounds.right <= 580.dp)
        // Must fit within 360dp height
        assertTrue("Close button must fit within 360dp height", closeBounds.bottom <= 355.dp)
    }
}
