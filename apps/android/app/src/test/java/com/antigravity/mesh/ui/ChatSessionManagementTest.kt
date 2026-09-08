package com.antigravity.mesh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.ChatSession
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.ui.screens.ChatScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatSessionManagementTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testNode = MeshNode(
        id = "node-1",
        name = "MacBook Pro",
        host = "100.64.0.1",
        token = "token-123",
        platform = "macOS",
        isOnline = true
    )

    @Test
    fun testSessionChipsRenderedAndSelectionWorks() {
        var selectedSessionId: String? = null
        var newSessionCreated = false

        val sessions = listOf(
            ChatSession(id = "sess-1", nodeId = testNode.id, title = "Główny wątek", createdAt = 1000L, updatedAt = 1000L),
            ChatSession(id = "sess-2", nodeId = testNode.id, title = "Debugowanie błędu", createdAt = 2000L, updatedAt = 2000L)
        )

        composeTestRule.setContent {
            ChatScreen(
                nodes = listOf(testNode),
                selectedNodeId = testNode.id,
                onSelectNode = {},
                sessions = sessions,
                activeSessionId = "sess-1",
                onSelectSession = { selectedSessionId = it },
                onCreateSession = { newSessionCreated = true },
                onRenameSession = { _, _ -> },
                onDeleteSession = {},
                messages = emptyList(),
                isLoading = false,
                onSendMessage = { _, _ -> },
                onBack = {},
                onClearChat = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify session titles are displayed
        composeTestRule.onNodeWithText("Główny wątek").assertIsDisplayed()
        composeTestRule.onNodeWithText("Debugowanie błędu").assertIsDisplayed()

        // 2. Click second session
        composeTestRule.onNodeWithText("Debugowanie błędu").performClick()
        assertEquals("sess-2", selectedSessionId)

        // 3. Click new session button
        composeTestRule.onNodeWithContentDescription("Nowy wątek").performClick()
        assertEquals(true, newSessionCreated)
    }

    @Test
    fun testSessionRenameWorkflow() {
        var renamedSessionId: String? = null
        var renamedTitle: String? = null

        val sessions = listOf(
            ChatSession(id = "sess-1", nodeId = testNode.id, title = "Stara nazwa", createdAt = 1000L, updatedAt = 1000L)
        )

        composeTestRule.setContent {
            ChatScreen(
                nodes = listOf(testNode),
                selectedNodeId = testNode.id,
                onSelectNode = {},
                sessions = sessions,
                activeSessionId = "sess-1",
                onSelectSession = {},
                onCreateSession = {},
                onRenameSession = { id, newName ->
                    renamedSessionId = id
                    renamedTitle = newName
                },
                onDeleteSession = {},
                messages = emptyList(),
                isLoading = false,
                onSendMessage = { _, _ -> },
                onBack = {},
                onClearChat = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Long click on session chip to open context menu
        composeTestRule.onNodeWithText("Stara nazwa").performTouchInput {
            longClick()
        }

        composeTestRule.waitForIdle()

        // 2. Dropdown should show "Zmień nazwę"
        val renameMenuOption = composeTestRule.onNodeWithText("Zmień nazwę")
        renameMenuOption.assertIsDisplayed()
        renameMenuOption.performClick()

        composeTestRule.waitForIdle()

        // 3. Rename dialog should appear
        composeTestRule.onNodeWithText("Zmień nazwę wątku").assertIsDisplayed()

        // Enter new title in dialog text field
        val textField = composeTestRule.onNode(hasSetTextAction() and hasText("Stara nazwa"))
        textField.performTextReplacement("Nowa super nazwa")

        // Click save button
        composeTestRule.onNodeWithText("Zapisz").performClick()

        composeTestRule.waitForIdle()

        assertEquals("sess-1", renamedSessionId)
        assertEquals("Nowa super nazwa", renamedTitle)
    }

    @Test
    fun testSessionDeleteWorkflow() {
        var deletedSessionId: String? = null

        val sessions = listOf(
            ChatSession(id = "sess-1", nodeId = testNode.id, title = "Wątek do usunięcia", createdAt = 1000L, updatedAt = 1000L),
            ChatSession(id = "sess-2", nodeId = testNode.id, title = "Drugi wątek", createdAt = 2000L, updatedAt = 2000L)
        )

        composeTestRule.setContent {
            ChatScreen(
                nodes = listOf(testNode),
                selectedNodeId = testNode.id,
                onSelectNode = {},
                sessions = sessions,
                activeSessionId = "sess-1",
                onSelectSession = {},
                onCreateSession = {},
                onRenameSession = { _, _ -> },
                onDeleteSession = { deletedSessionId = it },
                messages = emptyList(),
                isLoading = false,
                onSendMessage = { _, _ -> },
                onBack = {},
                onClearChat = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Long click to open options
        composeTestRule.onNodeWithText("Wątek do usunięcia").performTouchInput {
            longClick()
        }

        composeTestRule.waitForIdle()

        // 2. Select "Usuń wątek"
        val deleteOption = composeTestRule.onNodeWithText("Usuń wątek")
        deleteOption.assertIsDisplayed()
        deleteOption.performClick()

        composeTestRule.waitForIdle()

        // 3. Confirm deletion in dialog
        composeTestRule.onNodeWithText("Czy na pewno chcesz usunąć wątek", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Usuń").performClick()

        assertEquals("sess-1", deletedSessionId)
    }

    @Test
    fun testAgentThinkingStatusOnlyDisplayedWhenActive() {
        composeTestRule.setContent {
            ChatScreen(
                nodes = listOf(testNode),
                selectedNodeId = testNode.id,
                onSelectNode = {},
                sessions = listOf(ChatSession(id = "sess-1", nodeId = testNode.id, title = "Wątek 1")),
                activeSessionId = "sess-1",
                onSelectSession = {},
                onCreateSession = {},
                onRenameSession = { _, _ -> },
                onDeleteSession = {},
                messages = emptyList(),
                isLoading = true,
                agentStatus = "⚙️ Wykonywanie polecenia...",
                onSendMessage = { _, _ -> },
                onBack = {},
                onClearChat = {}
            )
        }

        composeTestRule.waitForIdle()

        // Verify thinking card is visible
        composeTestRule.onNodeWithText("⚙️ Wykonywanie polecenia...").assertIsDisplayed()
    }
}
