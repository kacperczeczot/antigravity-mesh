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
        val result = parseInlineMarkdown("Indeksy: C<sub>2</sub>H<sub>5</sub>OH oraz 2<sup>16</sup> = 65536")
        assertEquals("Indeksy: C₂H₅OH oraz 2¹⁶ = 65536", result.text)
    }

    @Test
    fun testParseInlineMarkdownMath() {
        val result = parseInlineMarkdown("Wzór \$E=mc^2\$ Einsteina oraz \$O(N \\log N)\$")
        assertEquals("Wzór E=mc² Einsteina oraz O(N log N)", result.text)
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
        assertEquals("𝔼[X] = ∑ᵢ₌₁ⁿ xᵢ P(X = xᵢ)", pretty)
    }

    @Test
    fun testParseInlineMarkdownBreak() {
        val result = parseInlineMarkdown("Linia 1<br>Linia 2<br/>Linia 3")
        assertEquals("Linia 1\nLinia 2\nLinia 3", result.text)
    }

    @Test
    fun testSummaryArrowTrimming() {
        val raw = "▶ Kliknij, aby zobaczyć pełną zawartość"
        val trimmed = raw.trimStart('▶', '►', '▸', '▼', '▾', '▲', '▴', '>', ' ').trim()
        assertEquals("Kliknij, aby zobaczyć pełną zawartość", trimmed)
    }

    @Test
    fun testPrettifyMathNoBraces() {
        val raw = """\sum_i=1^n x_i P(X = x_i)"""
        val pretty = com.antigravity.mesh.ui.components.prettifyMath(raw)
        assertEquals("∑ᵢ=1ⁿ xᵢ P(X = xᵢ)", pretty)
    }

    @Test
    fun testPrettifyMathGaussianIntegralAndVariance() {
        val circle = "x^2 + y^2 = 1"
        assertEquals("x² + y² = 1", com.antigravity.mesh.ui.components.prettifyMath(circle))

        val integral = """\int_{-\infty}^{\infty} e^{-x^2} dx = \sqrt{\pi}"""
        val prettyIntegral = com.antigravity.mesh.ui.components.prettifyMath(integral)
        assertEquals("∫[-∞, ∞] e⁻ˣ² dx = √π", prettyIntegral)

        val stats = """\mathbb{E}[X] = \mu, \quad \operatorname{Var}(X) = \sigma^2"""
        val prettyStats = com.antigravity.mesh.ui.components.prettifyMath(stats)
        assertEquals("𝔼[X] = μ,  Var(X) = σ²", prettyStats)
    }

    @Test
    fun testPrettifyMathMatrix() {
        val raw = """R(\theta) = \begin{bmatrix} \cos\theta & -\sin\theta \\ \sin\theta & \cos\theta \end{bmatrix}"""
        val pretty = com.antigravity.mesh.ui.components.prettifyMath(raw)
        assertEquals("R(θ) = [ cosθ   -sinθ  ;  sinθ   cosθ ]", pretty)
    }

    @Test
    fun testHighlightCode() {
        val code = """
            // Komentarz
            fun calculate(val name: String): Int {
                val count = 42
                return count
            }
        """.trimIndent()
        val highlighted = com.antigravity.mesh.ui.components.highlightCode(code, "kotlin")
        assertEquals(code, highlighted.text)
        assertTrue("Powinny być zaaplikowane style składni", highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testDetailsTagNotTriggeredByHeading() {
        val headingLine = "## 9. Sekcje zwijane (`<details>` / `<summary>`)"
        val trimmedLine = headingLine.trim()
        val isDetailsOpenTag = !trimmedLine.startsWith("#") &&
            !trimmedLine.contains("`<details") &&
            (trimmedLine.startsWith("<details", ignoreCase = true) ||
             Regex("""(?i)^\s*<details(\s+[^>]*)?>""").containsMatchIn(trimmedLine))
        assertFalse("Nagłówek zawierający backticki `<details>` nie powinien być traktowany jako blok details", isDetailsOpenTag)

        val realDetailsLine = "<details>"
        val isRealDetails = !realDetailsLine.startsWith("#") &&
            !realDetailsLine.contains("`<details") &&
            (realDetailsLine.startsWith("<details", ignoreCase = true) ||
             Regex("""(?i)^\s*<details(\s+[^>]*)?>""").containsMatchIn(realDetailsLine))
        assertTrue("Prawdziwy tag <details> musi być poprawnie wykrywany", isRealDetails)
    }
}
