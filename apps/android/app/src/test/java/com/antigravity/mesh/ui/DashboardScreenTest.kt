package com.antigravity.mesh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.screens.DashboardScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2400dp")
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val onlineNode = MeshNode(
        id = "node-1",
        name = "MacBook Pro M3",
        host = "100.64.0.5",
        token = "token-1",
        platform = "macOS",
        isOnline = true,
        isPinned = false
    )

    private val offlineNode = MeshNode(
        id = "node-2",
        name = "Ubuntu Linux Server",
        host = "100.64.0.9",
        token = "token-2",
        platform = "Linux",
        isOnline = false,
        isPinned = true
    )

    @Test
    fun testEmptyDashboardStateDisplaysSetupHelp() {
        var scanClicked = false

        composeTestRule.setContent {
            DashboardScreen(
                nodes = emptyList(),
                isScanning = false,
                onRefreshAll = {},
                onScanAndPair = { scanClicked = true },
                onNodeChat = {},
                onNodeRefresh = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify header title
        composeTestRule.onNodeWithText("Antigravity Mesh").assertIsDisplayed()

        // 2. Verify empty state banner
        composeTestRule.onNodeWithText("Brak skonfigurowanych węzłów").assertIsDisplayed()
        composeTestRule.onNode(hasText("Uruchom Antigravity Mesh na komputerze", substring = true)).assertIsDisplayed()

        // 3. Verify node counter shows 0 / 0
        composeTestRule.onNodeWithText("Aktywne węzły: 0 / 0").assertIsDisplayed()
    }

    @Test
    fun testDashboardWithNodesShowsListAndFilterTabs() {
        val sampleNodes = listOf(onlineNode, offlineNode)
        var openedChatNode: MeshNode? = null

        composeTestRule.setContent {
            DashboardScreen(
                nodes = sampleNodes,
                isScanning = false,
                onRefreshAll = {},
                onScanAndPair = {},
                onNodeChat = { openedChatNode = it },
                onNodeRefresh = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify node counter: 1 active out of 2 total
        composeTestRule.onNodeWithText("Aktywne węzły: 1 / 2").assertIsDisplayed()

        // 2. Both nodes rendered in ALL filter tab
        composeTestRule.onNodeWithText("MacBook Pro M3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ubuntu Linux Server").assertIsDisplayed()

        // 3. Click Online filter tab
        composeTestRule.onNodeWithText("Online (1)").performClick()
        composeTestRule.waitForIdle()

        // 4. In Online filter, offlineNode should disappear, onlineNode remains
        composeTestRule.onNodeWithText("MacBook Pro M3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ubuntu Linux Server").assertDoesNotExist()

        // 5. Click Pinned filter tab
        composeTestRule.onNodeWithText("Przypięte (1)").performClick()
        composeTestRule.waitForIdle()

        // 6. In Pinned filter, only offlineNode (which is pinned) is visible
        composeTestRule.onNodeWithText("Ubuntu Linux Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("MacBook Pro M3").assertDoesNotExist()

        // 7. Click on node card to open chat
        composeTestRule.onNodeWithText("Ubuntu Linux Server").performClick()
        assertEquals("node-2", openedChatNode?.id)
    }

    @Test
    fun testDashboardSearchFiltering() {
        val sampleNodes = listOf(onlineNode, offlineNode)

        composeTestRule.setContent {
            DashboardScreen(
                nodes = sampleNodes,
                isScanning = false,
                onRefreshAll = {},
                onScanAndPair = {},
                onNodeChat = {},
                onNodeRefresh = {}
            )
        }

        composeTestRule.waitForIdle()

        // Search by IP "100.64.0.9" (Ubuntu server)
        composeTestRule.onNode(hasSetTextAction()).performTextInput("100.64.0.9")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Ubuntu Linux Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("MacBook Pro M3").assertDoesNotExist()
    }

    @Test
    fun testDashboardUpdateBannerDisplaysWhenAvailable() {
        var updateDialogOpened = false

        composeTestRule.setContent {
            DashboardScreen(
                nodes = listOf(onlineNode),
                isScanning = false,
                onRefreshAll = {},
                onScanAndPair = {},
                onNodeChat = {},
                onNodeRefresh = {},
                hasUpdateAvailable = true,
                updateVersion = "2.3.5",
                onOpenUpdateDialog = { updateDialogOpened = true }
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify update banner is visible
        composeTestRule.onNodeWithText("Dostępna nowa wersja: v2.3.5").assertIsDisplayed()

        // 2. Click update button
        val updateBtn = composeTestRule.onNodeWithText("Aktualizuj")
        updateBtn.assertIsDisplayed()
        updateBtn.performClick()

        assertTrue("Kliknięcie w baner powinno otworzyć dialog aktualizacji", updateDialogOpened)
    }
}
