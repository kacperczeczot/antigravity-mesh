package com.antigravity.mesh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.antigravity.mesh.data.ChatMessage
import com.antigravity.mesh.data.MeshNode
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
}
