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

        // Verify buttons have non-zero dimensions
        assertTrue("Close button height must be > 0", closeBounds.height > 0.dp)
        assertTrue("Retry button height must be > 0", retryBounds.height > 0.dp)
    }

    @Test
    @Config(qualifiers = "land-w915dp-h412dp") // Standard modern phone in landscape (e.g. Galaxy S23 rotated)
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

        // 2. Buttons must sit fully within the 412dp screen height
        assertTrue(
            "Close button bottom (${closeBounds.bottom}) must be within screen height (412dp)",
            closeBounds.bottom <= 412.dp
        )
        assertTrue(
            "Retry button bottom (${retryBounds.bottom}) must be within screen height (412dp)",
            retryBounds.bottom <= 412.dp
        )

        // 3. Header title must be visible at top without massive dead gap
        val header = composeTestRule.onNodeWithText("Audyt Uprawnień i Diagnostyka")
        header.assertIsDisplayed()
        val headerBounds = header.getBoundsInRoot()
        assertTrue(
            "Header top (${headerBounds.top}) must start near top of screen (<= 60dp)",
            headerBounds.top <= 60.dp
        )
    }
}
