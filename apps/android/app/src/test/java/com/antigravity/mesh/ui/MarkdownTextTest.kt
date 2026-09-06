package com.antigravity.mesh.ui

import com.antigravity.mesh.ui.components.parseInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun testParseInlineMarkdownSingleBacktick() {
        val result = parseInlineMarkdown("Plik `test.txt` został utworzony")
        assertEquals("Plik test.txt został utworzony", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownTripleBacktickInline() {
        val result = parseInlineMarkdown("Zawartość: ```text code sample``` koniec")
        assertEquals("Zawartość: text code sample koniec", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownLinks() {
        val result = parseInlineMarkdown("Link: [plik](file:///Users/kacper/test.txt)")
        assertEquals("Link: plik", result.text)
    }

    @Test
    fun testParseInlineMarkdownBold() {
        val result = parseInlineMarkdown("Tekst **pogrubiony** normalny")
        assertEquals("Tekst pogrubiony normalny", result.text)
    }
}
