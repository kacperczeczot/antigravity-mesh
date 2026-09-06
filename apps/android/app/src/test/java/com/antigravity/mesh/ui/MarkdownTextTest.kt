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
        val resultAsterisks = parseInlineMarkdown("Tekst **pogrubiony** normalny")
        assertEquals("Tekst pogrubiony normalny", resultAsterisks.text)
        assertTrue(resultAsterisks.spanStyles.isNotEmpty())

        val resultUnderscores = parseInlineMarkdown("Tekst __pogrubiony__ normalny")
        assertEquals("Tekst pogrubiony normalny", resultUnderscores.text)
        assertTrue(resultUnderscores.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownBoldItalic() {
        val result = parseInlineMarkdown("Tekst ***bardzo ważny*** normalny")
        assertEquals("Tekst bardzo ważny normalny", result.text)
        assertTrue(result.spanStyles.isNotEmpty())

        val resultUnderscores = parseInlineMarkdown("Tekst ___bardzo ważny___ normalny")
        assertEquals("Tekst bardzo ważny normalny", resultUnderscores.text)
        assertTrue(resultUnderscores.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownItalic() {
        val resultAsterisk = parseInlineMarkdown("Tekst *pochylony* normalny")
        assertEquals("Tekst pochylony normalny", resultAsterisk.text)
        assertTrue(resultAsterisk.spanStyles.isNotEmpty())

        val resultUnderscore = parseInlineMarkdown("Tekst _pochylony_ normalny")
        assertEquals("Tekst pochylony normalny", resultUnderscore.text)
        assertTrue(resultUnderscore.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownStrikethrough() {
        val result = parseInlineMarkdown("Cena ~~100 zł~~ 80 zł")
        assertEquals("Cena 100 zł 80 zł", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownKbd() {
        val result = parseInlineMarkdown("Wciśnij <kbd>Ctrl</kbd> + <kbd>C</kbd>")
        assertEquals("Wciśnij  Ctrl  +  C ", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownSubAndSup() {
        val result = parseInlineMarkdown("Woda H<sub>2</sub>O oraz 2<sup>10</sup> = 1024")
        assertEquals("Woda H2O oraz 210 = 1024", result.text)
        assertTrue(result.spanStyles.size >= 2)
    }

    @Test
    fun testParseInlineMarkdownMath() {
        val result = parseInlineMarkdown("Wzór \$E=mc^2\$ Einsteina")
        assertEquals("Wzór  E=mc^2  Einsteina", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownCodeTag() {
        val result = parseInlineMarkdown("Konfiguracja <code>config.yaml</code> gotowa")
        assertEquals("Konfiguracja  config.yaml  gotowa", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun testPrettifyMathMacros() {
        val raw = """\mathbb{E}[X] = \sum_{i=1}^{n} x_i P(X = x_i)"""
        val pretty = com.antigravity.mesh.ui.components.prettifyMath(raw)
        assertEquals("𝔼[X] = ∑_i=1^n x_i P(X = x_i)", pretty)
    }

    @Test
    fun testParseInlineMarkdownBreak() {
        val result = parseInlineMarkdown("Linia 1<br>Linia 2<br/>Linia 3")
        assertEquals("Linia 1\nLinia 2\nLinia 3", result.text)
    }
}
