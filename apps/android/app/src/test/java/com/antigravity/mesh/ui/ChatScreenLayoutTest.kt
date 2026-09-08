package com.antigravity.mesh.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
import com.antigravity.mesh.data.QueuedMessage
import com.antigravity.mesh.ui.components.QueueDeck
import com.antigravity.mesh.ui.screens.ChatBubble
import com.antigravity.mesh.ui.screens.ChatScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatScreenLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testUserChatBubbleRendersContent() {
        val userMsg = ChatMessage(
            id = "msg-1",
            senderNode = "My Device",
            content = "Cześć agencie, wylistuj procesy.",
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            ChatBubble(message = userMsg)
        }

        composeTestRule.onNodeWithText("Cześć agencie, wylistuj procesy.").assertIsDisplayed()
    }

    @Test
    fun testAssistantChatBubbleRendersFullWidthCardWithNodeNameAndCopy() {
        val assistantMsg = ChatMessage(
            id = "msg-2",
            senderNode = "MacBook Pro (M3 Max)",
            content = """
                Oto wynik wykonania polecenia:
                ```bash
                ps aux | grep daemon
                ```
                Wszystko działa stabilnie.
            """.trimIndent(),
            isUser = false,
            timestamp = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            ChatBubble(message = assistantMsg)
        }

        composeTestRule.waitForIdle()

        // 1. Verify assistant header contains node name
        composeTestRule.onNodeWithText("MacBook Pro (M3 Max)").assertIsDisplayed()

        // 2. Verify copy content button is present with accessible description
        composeTestRule.onNodeWithContentDescription("Kopiuj treść").assertIsDisplayed()

        // 3. Verify inner markdown code block is rendered
        composeTestRule.onNode(hasText("ps aux | grep daemon", substring = true)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Wszystko działa stabilnie.").assertIsDisplayed()
    }

    @Test
    fun testChatScreenTopBarAndOverflowMenu() {
        var clearChatCalledWithNode: String? = null

        val testNode = MeshNode(
            id = "node-mac",
            name = "Mac Studio",
            host = "100.64.0.1",
            token = "test-token",
            platform = "macOS",
            isOnline = true
        )

        composeTestRule.setContent {
            ChatScreen(
                nodes = listOf(testNode),
                selectedNodeId = testNode.id,
                onSelectNode = {},
                messages = listOf(
                    ChatMessage(
                        id = "m1",
                        senderNode = "Mac Studio",
                        content = "Połączono pomyślnie",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                ),
                isLoading = false,
                onSendMessage = { _, _ -> },
                onBack = {},
                onClearChat = { clearChatCalledWithNode = it }
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify top bar shows connected node name
        composeTestRule.onAllNodesWithText("Mac Studio").onFirst().assertIsDisplayed()

        // 2. Open overflow menu (three vertical dots)
        val overflowBtn = composeTestRule.onNodeWithContentDescription("Więcej opcji")
        overflowBtn.assertIsDisplayed()
        overflowBtn.performClick()

        composeTestRule.waitForIdle()

        // 3. Verify menu options are visible and clickable
        val clearOption = composeTestRule.onNodeWithText("Wyczyść historię")
        clearOption.assertIsDisplayed()
        clearOption.performClick()

        composeTestRule.waitForIdle()

        // 4. Confirm in dialog
        val confirmBtn = composeTestRule.onNodeWithText("Wyczyść")
        if (confirmBtn.isDisplayed()) {
            confirmBtn.performClick()
            assertEquals("node-mac", clearChatCalledWithNode)
        }
    }

    @Test
    fun testOverflowMenuAlwaysVisibleEvenWhenMessagesEmptyAndOffline() {
        val offlineNode = MeshNode(
            id = "node-offline",
            name = "Offline Device",
            host = "100.64.0.2",
            token = "test-token",
            platform = "linux",
            isOnline = false
        )

        composeTestRule.setContent {
            ChatScreen(
                nodes = listOf(offlineNode),
                selectedNodeId = offlineNode.id,
                onSelectNode = {},
                messages = emptyList(),
                isLoading = false,
                onSendMessage = { _, _ -> },
                onBack = {},
                onPermissionsClick = null
            )
        }

        composeTestRule.waitForIdle()

        // Verify overflow menu button is unconditionally displayed
        composeTestRule.onNodeWithContentDescription("Więcej opcji").assertIsDisplayed()
    }

    @Test
    fun testQueueDeckRendersItemsWithEditFastTrackAndCancelButtons() {
        var editedItem: QueuedMessage? = null
        var fastTrackMessageId: String? = null
        var cancelMessageId: String? = null

        val queuedItems = listOf(
            QueuedMessage(id = "q-1", nodeId = "node-1", sessionId = "s-1", text = "Pierwsze zadanie w kolejce"),
            QueuedMessage(id = "q-2", nodeId = "node-1", sessionId = "s-1", text = "Drugie zadanie w kolejce")
        )

        composeTestRule.setContent {
            QueueDeck(
                queuedMessages = queuedItems,
                onEditMessage = { editedItem = it },
                onFastTrackMessage = { fastTrackMessageId = it },
                onCancelMessage = { cancelMessageId = it }
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify queue header with item count
        composeTestRule.onNodeWithText("Kolejka zadań (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Wyczyść wszystko").assertIsDisplayed()

        // 2. Verify both messages are rendered with sequential order badges
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pierwsze zadanie w kolejce").assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Drugie zadanie w kolejce").assertIsDisplayed()

        // 3. Verify Edit action
        val editButtons = composeTestRule.onAllNodes(hasContentDescription("Edytuj prompt") and hasClickAction())
        editButtons[0].performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals("q-1", editedItem?.id)
        assertEquals("Pierwsze zadanie w kolejce", editedItem?.text)

        // 4. Verify Fast-track action
        val fastTrackButtons = composeTestRule.onAllNodes(hasContentDescription("Wyślij teraz") and hasClickAction())
        fastTrackButtons[1].performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals("q-2", fastTrackMessageId)

        // 5. Verify Cancel action
        val cancelButtons = composeTestRule.onAllNodes(hasContentDescription("Usuń z kolejki") and hasClickAction())
        cancelButtons[0].performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals("q-1", cancelMessageId)
    }

    @Test
    fun testChatScreenRendersQueueDeckAboveComposer() {
        val testNode = MeshNode(
            id = "node-mac",
            name = "Mac Studio",
            host = "100.64.0.1",
            token = "test-token",
            platform = "macOS",
            isOnline = true
        )

        val queuedItems = listOf(
            QueuedMessage(id = "q-10", nodeId = "node-mac", sessionId = "s-1", text = "Kolejka w widoku czatu")
        )

        composeTestRule.setContent {
            ChatScreen(
                nodes = listOf(testNode),
                selectedNodeId = testNode.id,
                onSelectNode = {},
                messages = emptyList(),
                isLoading = true,
                onSendMessage = { _, _ -> },
                queuedMessages = queuedItems
            )
        }

        composeTestRule.waitForIdle()

        // Verify QueueDeck is visible in ChatScreen
        composeTestRule.onNodeWithText("Kolejka zadań (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kolejka w widoku czatu").assertIsDisplayed()
    }

    @Test
    fun testComposerImmediateSendButtonWhenLoading() {
        var immediateSendNodeId: String? = null
        var immediateSendQuestion: String? = null

        val testNode = MeshNode(
            id = "node-mac",
            name = "Mac Studio",
            host = "100.64.0.1",
            token = "test-token",
            platform = "macOS",
            isOnline = true
        )

        composeTestRule.setContent {
            ChatScreen(
                nodes = listOf(testNode),
                selectedNodeId = testNode.id,
                onSelectNode = {},
                messages = listOf(
                    ChatMessage(id = "m1", senderNode = "Mac Studio", content = "Przetwarzanie...", isUser = false)
                ),
                isLoading = true,
                onSendMessage = { _, _ -> },
                onSendImmediate = { nId, q ->
                    immediateSendNodeId = nId
                    immediateSendQuestion = q
                }
            )
        }

        composeTestRule.waitForIdle()

        // Enter prompt into text field
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Natychmiastowe zadanie priorytetowe")

        composeTestRule.waitForIdle()

        // Verify immediate send Bolt button is displayed and click
        val immediateBtn = composeTestRule.onNode(hasContentDescription("Wyślij natychmiast") and hasClickAction())
        immediateBtn.assertIsDisplayed()
        immediateBtn.performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.waitForIdle()

        assertEquals("node-mac", immediateSendNodeId)
        assertEquals("Natychmiastowe zadanie priorytetowe", immediateSendQuestion)
    }

    @Test
    fun testLongUserMessageCollapsibleAndExpandable() {
        val longContent = """
            Linia 1: Rozpoczęcie analizy
            Linia 2: Sprawdzenie środowiska uruchomieniowego
            Linia 3: Pobranie metryk systemowych
            Linia 4: Wykrycie aktywnych wątków daemona
            Linia 5: Sprawdzenie stanu pamięci
            Linia 6: Sprawdzenie dysków
            Linia 7: Sprawdzenie portów sieciowych
            Linia 8: Przygotowanie raportu podsumowującego
        """.trimIndent()

        val longMsg = ChatMessage(
            id = "long-msg-1",
            senderNode = "My Device",
            content = longContent,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            ChatBubble(message = longMsg)
        }

        composeTestRule.waitForIdle()

        // 1. Verify "Pokaż więcej" expand action is displayed
        val expandBtn = composeTestRule.onNode(hasText("Pokaż więcej", substring = true))
        expandBtn.assertIsDisplayed()

        // 2. Click to expand
        expandBtn.performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // 3. Verify "Zwiń" collapse action is now displayed
        composeTestRule.onNode(hasText("Zwiń", substring = true)).assertIsDisplayed()
    }

    @Test
    fun testLongUserMessageShowsCleanToggleWithoutLineCount() {
        val longContentWithoutNewlines = "A".repeat(300)
        val longMsg = ChatMessage(
            id = "long-msg-2",
            senderNode = "My Device",
            content = longContentWithoutNewlines,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            ChatBubble(message = longMsg)
        }

        composeTestRule.waitForIdle()

        // Verify clean "Pokaż więcej ▼" is shown without weird "(1 linii)" or "(2 linii)"
        composeTestRule.onNodeWithText("Pokaż więcej ▼").assertIsDisplayed()
    }
}

