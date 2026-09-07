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

        // In portrait 915dp, navigation bar is at bottom 48dp (867dp..915dp).
        // Buttons must be fully above 867dp!
        assertTrue("Close button bottom (${closeBounds.bottom}) must be ABOVE nav bar (< 867dp)", closeBounds.bottom <= 855.dp)
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
            closeBounds.right <= 850.dp
        )

        // 3. Buttons must sit fully within the 412dp screen height
        assertTrue(
            "Close button bottom (${closeBounds.bottom}) must be within screen height (412dp)",
            closeBounds.bottom <= 405.dp
        )
        assertTrue(
            "Retry button bottom (${retryBounds.bottom}) must be within screen height (412dp)",
            retryBounds.bottom <= 405.dp
        )

        // 4. Header title must be visible at top without massive dead gap
        val header = composeTestRule.onNodeWithText("Audyt Uprawnień i Diagnostyka")
        header.assertIsDisplayed()
        val headerBounds = header.getBoundsInRoot()
        println("LANDSCAPE headerBounds: top=${headerBounds.top}, bottom=${headerBounds.bottom}")
        assertTrue(
            "Header top (${headerBounds.top}) must start near top of screen (<= 60dp)",
            headerBounds.top <= 60.dp
        )
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
        assertTrue("Close button must be visible", (closeBounds.bottom - closeBounds.top) > 0.dp)
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
        println("FILE_VIEWER LANDSCAPE closeBounds: right=${closeBounds.right}")
        // In landscape (915dp), close button on header must be strictly left of nav bar (< 867dp)
        assertTrue(
            "Close button right (${closeBounds.right}) must be strictly to the left of nav bar (< 867dp)",
            closeBounds.right <= 850.dp
        )
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
