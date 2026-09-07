package com.antigravity.mesh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.ui.components.UpdateDialog
import com.antigravity.mesh.updater.ReleaseUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleOffer = ReleaseUpdateChecker.UpdateOffer(
        latestVersion = "2.3.5",
        currentVersion = "2.3.4",
        apkUrl = "https://github.com/kacperczeczot/antigravity-mesh/releases/download/v2.3.5/app-release.apk",
        releaseNotes = "Poprawki błędów nawigacji i wyświetlania okien dialogowych."
    )

    @Test
    fun testUpdateDialogInitialStateAndInstallAction() {
        var startUpdateCalled = false
        var dismissed = false

        composeTestRule.setContent {
            UpdateDialog(
                offer = sampleOffer,
                isDownloading = false,
                progressFraction = 0f,
                progressStatus = "",
                errorMessage = null,
                onDismiss = { dismissed = true },
                onStartUpdate = { startUpdateCalled = true }
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify dialog header and versions
        composeTestRule.onNodeWithText("Nowa aktualizacja!").assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.3.4").assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.3.5").assertIsDisplayed()

        // 2. Verify release notes
        composeTestRule.onNode(hasText("Poprawki błędów nawigacji", substring = true)).assertIsDisplayed()

        // 3. Click "Aktualizuj"
        val updateBtn = composeTestRule.onNodeWithText("Aktualizuj")
        updateBtn.assertIsDisplayed()
        updateBtn.performClick()

        assertTrue("Kliknięcie w 'Aktualizuj' powinno uruchomić aktualizację", startUpdateCalled)
    }

    @Test
    fun testUpdateDialogDownloadingStateWithCancelOption() {
        var cancelCalled = false

        composeTestRule.setContent {
            UpdateDialog(
                offer = sampleOffer,
                isDownloading = true,
                progressFraction = 0.45f,
                progressStatus = "Pobieranie: 45%",
                errorMessage = null,
                onDismiss = {},
                onStartUpdate = {},
                onCancelDownload = { cancelCalled = true }
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify progress status is displayed
        composeTestRule.onNodeWithText("Pobieranie: 45%").assertIsDisplayed()

        // 2. Verify "Anuluj" button is active during download
        val cancelBtn = composeTestRule.onNodeWithText("Anuluj")
        cancelBtn.assertIsDisplayed()
        cancelBtn.performClick()

        assertTrue("Kliknięcie w 'Anuluj' powinno anulować pobieranie", cancelCalled)
    }
}
