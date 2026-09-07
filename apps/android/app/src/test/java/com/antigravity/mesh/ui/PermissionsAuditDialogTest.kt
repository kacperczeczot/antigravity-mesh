package com.antigravity.mesh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.data.AccessibilityCheck
import com.antigravity.mesh.data.FullDiskAccessCheck
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.data.PermissionAuditReport
import com.antigravity.mesh.ui.components.PermissionsAuditDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsAuditDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testNode = MeshNode(
        id = "node-1",
        name = "Kacper's MacBook Pro",
        host = "100.64.0.5",
        token = "test-token",
        platform = "macOS",
        isOnline = true
    )

    @Test
    fun testPermissionsAuditDialogRendersHeaderAndStatus() {
        val sampleReport = PermissionAuditReport(
            platform = "macos",
            overallStatus = "action_required",
            allGranted = false,
            summary = "Wymagane uprawnienia w systemie macOS",
            accessibility = AccessibilityCheck(granted = true, status = "granted"),
            fullDiskAccess = FullDiskAccessCheck(granted = false, status = "denied", message = "Brak dostępu do TCC")
        )

        composeTestRule.setContent {
            PermissionsAuditDialog(
                node = testNode,
                report = sampleReport,
                isLoading = false,
                errorMessage = null,
                onRefresh = {},
                onFixAction = {},
                onDismiss = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify header with title and node name
        composeTestRule.onNodeWithText("Audyt Uprawnień i Diagnostyka").assertIsDisplayed()
        composeTestRule.onNode(hasText("Kacper's MacBook Pro", substring = true)).assertIsDisplayed()

        // 2. Verify status banner
        composeTestRule.onNodeWithText("Wymagana akcja w systemie").assertIsDisplayed()
    }

    @Test
    fun testPermissionsAuditDialogErrorStateAndRetry() {
        var refreshCalled = false

        composeTestRule.setContent {
            PermissionsAuditDialog(
                node = testNode,
                report = null,
                isLoading = false,
                errorMessage = "Błąd połączenia z węzłem",
                onRefresh = { refreshCalled = true },
                onDismiss = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify error message is rendered
        composeTestRule.onNode(hasText("Błąd połączenia z węzłem", substring = true)).assertIsDisplayed()

        // 2. Verify retry button is active
        val retryBtn = composeTestRule.onNodeWithText("Spróbuj ponownie")
        retryBtn.performScrollTo().assertIsDisplayed()
        retryBtn.performClick()

        assertTrue("Kliknięcie w 'Spróbuj ponownie' powinno wywołać onRefresh", refreshCalled)
    }
}
