package com.antigravity.mesh.ui

import com.antigravity.mesh.ui.components.parseInlineMarkdown
import com.antigravity.mesh.ui.components.splitMarkdownTableCells
import com.antigravity.mesh.ui.components.isMarkdownTableSeparatorRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
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
        assertEquals("∫[-∞, ∞]  e⁻ˣ² dx = √π", prettyIntegral)

        val stats = """\mathbb{E}[X] = \mu, \quad \operatorname{Var}(X) = \sigma^2"""
        val prettyStats = com.antigravity.mesh.ui.components.prettifyMath(stats)
        assertEquals("𝔼[X] = μ,  Var(X) = σ²", prettyStats)
    }

    @Test
    fun testPrettifyMathMatrix() {
        val raw = """R(\theta) = \begin{bmatrix} \cos\theta & -\sin\theta \\ \sin\theta & \cos\theta \end{bmatrix}"""
        val pretty = com.antigravity.mesh.ui.components.prettifyMath(raw)
        assertEquals("R(θ) = [  cosθ  -sinθ  ;  sinθ  cosθ  ]", pretty)
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

    @Test
    fun testTableSeparatorRegex() {
        val sep1 = "|---|---|"
        val sep2 = "| :--- | :---: | ---: |"
        val sep3 = "---|---"
        val dataRow = "| Opis z kreską | N/A --- brak |"

        val regex = Regex("""^\|?\s*:?-+:?\s*(\|?\s*:?-+:?\s*)*\|?$""")
        assertTrue(regex.matches(sep1))
        assertTrue(regex.matches(sep2))
        assertTrue(regex.matches(sep3))
        assertFalse(regex.matches(dataRow))
    }

    @Test
    fun testSplitMarkdownTableCells() {
        val row = "| Identyfikator węzła | Protokół | Adres IP w sieci Mesh | Status połączenia | Przesłano |"
        val cells = splitMarkdownTableCells(row)
        assertEquals(5, cells.size)
        assertEquals("Identyfikator węzła", cells[0])
        assertEquals("Protokół", cells[1])
        assertEquals("Adres IP w sieci Mesh", cells[2])
        assertEquals("Status połączenia", cells[3])
        assertEquals("Przesłano", cells[4])

        val rowEscaped = "| Kolumna A \\| B | Kolumna C |"
        val escapedCells = splitMarkdownTableCells(rowEscaped)
        assertEquals(2, escapedCells.size)
        assertEquals("Kolumna A | B", escapedCells[0])
        assertEquals("Kolumna C", escapedCells[1])
    }

    @Test
    fun testIsMarkdownTableSeparatorRow() {
        assertTrue(isMarkdownTableSeparatorRow("| :--- | :---: | :---: | :---: | ---: |"))
        assertTrue(isMarkdownTableSeparatorRow("|---|---|"))
        assertTrue(isMarkdownTableSeparatorRow(":- | -:"))
        assertFalse(isMarkdownTableSeparatorRow("| Zwykły tekst | Inna kolumna |"))
        assertFalse(isMarkdownTableSeparatorRow("find . | grep test | sort"))
    }

    @Test
    fun testCleanMermaidCode() {
        val raw1 = "```mermaid\ngraph TD\n  A --> B\n```"
        assertEquals("graph TD\n  A --> B", com.antigravity.mesh.ui.components.cleanMermaidCode(raw1))

        val raw2 = "```\nsequenceDiagram\n  Alice->>Bob: Hello\n```"
        assertEquals("sequenceDiagram\n  Alice->>Bob: Hello", com.antigravity.mesh.ui.components.cleanMermaidCode(raw2))

        val raw3 = "   flowchart LR\n A --> B   "
        assertEquals("flowchart LR\n A --> B", com.antigravity.mesh.ui.components.cleanMermaidCode(raw3))
    }

    @Test
    fun testEscapeMermaidHtml() {
        val raw = """graph TD; A["Text & <Tag> 'Quote'"] --> B;"""
        val escaped = com.antigravity.mesh.ui.components.escapeMermaidHtml(raw)
        assertTrue(escaped.contains("&amp;"))
        assertTrue(escaped.contains("&lt;Tag&gt;"))
        assertTrue(escaped.contains("&quot;"))
        assertTrue(escaped.contains("&#39;"))
    }

    @Test
    fun testBuildMermaidHtmlStructure() {
        val code = "graph TD\n  A --> B"
        val html = com.antigravity.mesh.ui.components.buildMermaidHtml(code, isFullscreen = false)

        // Must use the WebViewAssetLoader virtual HTTPS domain — NOT file:///android_asset/
        // which is blocked by Android 9+ WebView security sandboxing
        assertTrue(
            "Musi używać wirtualnej domeny appassets (WebViewAssetLoader), nie file:// URL",
            html.contains("https://appassets.androidplatform.net/assets/mermaid/mermaid.min.js")
        )
        assertFalse(
            "NIE może używać file:///android_asset/ — blokowane przez Android 9+ WebView",
            html.contains("file:///android_asset/mermaid/mermaid.min.js")
        )
        assertTrue("Musi zawierać rezerwowy fallback CDN jsdelivr", html.contains("cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"))
        assertTrue("Musi zawierać kontener pre class=mermaid ze źródłem diagramu", html.contains("""<pre class="mermaid">"""))
        assertTrue("Musi zawierać oczyszczony i zabezpieczony kod diagramu", html.contains("graph TD") && html.contains("A --&gt; B"))
        assertTrue("Musi zawierać wywołanie mermaid.run()", html.contains("mermaid.run()"))
        assertTrue("Musi zawierać weryfikację gotowości DOM readyState", html.contains("document.readyState === 'loading'"))
        assertTrue("Musi zawierać mostek AndroidMermaidBridge", html.contains("window.AndroidMermaidBridge.onRendered()"))
    }

    @Test
    fun testKatexHtmlStructureAndPadding() {
        val formula = "\\sum_{i=1}^n x_i"
        val html = com.antigravity.mesh.ui.components.buildMathHtml(formula)

        assertTrue("Musi zawierać arkusz katex.min.css", html.contains("katex.min.css"))
        assertTrue("Musi zawierać skrypt katex.min.js", html.contains("katex.min.js"))
        assertTrue("Musi zawierać dolny padding 22px w #math-container dla indeksów dolnych i ułamków", html.contains("padding: 12px 48px 22px 18px;"))
        assertTrue("Musi zawierać padding-bottom 6px w #math-scroll dla paska przewijania", html.contains("padding-bottom: 6px;"))
        assertTrue("Musi zawierać bufor wysokości h + 16 w wywołaniu AndroidMathBridge", html.contains("AndroidMathBridge.onHeight(h + 16)"))
    }

    @Test
    fun testMermaidAssetIntegrity() {
        // Sprawdzenie obecności pliku mermaid.min.js w assetach projektu Android
        val candidates = listOf(
            java.io.File("src/main/assets/mermaid/mermaid.min.js"),
            java.io.File("apps/android/app/src/main/assets/mermaid/mermaid.min.js"),
            java.io.File("../app/src/main/assets/mermaid/mermaid.min.js")
        )
        val assetFile = candidates.firstOrNull { it.exists() }
        assertTrue("Plik mermaid.min.js musi istnieć w folderze assets", assetFile != null && assetFile.exists())
        assertTrue("Plik mermaid.min.js musi mieć rozmiar > 1 MB (pełny bundle)", assetFile!!.length() > 1_000_000)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val stream = context.assets.open("mermaid/mermaid.min.js")
        org.junit.Assert.assertNotNull("Stream nie może być null", stream)
        val bytes = stream.readBytes()
        stream.close()
        assertTrue("Musi przeczytać ponad 1MB z assets.open", bytes.size > 1_000_000)
    }

    @Test
    fun testMermaidScriptHolderLoadsScript() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val script = com.antigravity.mesh.ui.components.MermaidScriptHolder.getScript(context)
        assertTrue("MermaidScriptHolder musi załadować skrypt z assetów", script.isNotBlank())
        assertTrue("Skrypt Mermaid musi mieć ponad 1MB", script.length > 1_000_000)
        // Powtórne wywołanie powinno zwrócić tę samą instancję z cache
        val cached = com.antigravity.mesh.ui.components.MermaidScriptHolder.getScript(context)
        assertSame(script, cached)
    }

    @Test
    fun testParseInlineMarkdownBoldCodeInsideLink() {
        var clickedTarget: String? = null
        val markdown = "• 🧠 [**`00_fundamenty/`**](data/00_fundamenty/00_indeks.md) — Profil"
        val result = parseInlineMarkdown(markdown, onLinkClick = { clickedTarget = it })

        // Ensure asterisks and backticks are parsed and removed from raw text
        assertEquals("• 🧠 00_fundamenty/ — Profil", result.text)
        assertTrue("Powinny być zaaplikowane style pogrubienia i kodu", result.spanStyles.isNotEmpty())
    }

    @Test
    fun testResolveRelativeFilePath() {
        val baseMac = "/Volumes/MAC_STORAGE_APFS/Developer/GitHub/antigravity-mesh/README.md"
        val relTarget = "data/00_fundamenty/00_indeks.md"
        val resolved = com.antigravity.mesh.ui.components.resolveRelativeFilePath(baseMac, relTarget)
        assertEquals("/Volumes/MAC_STORAGE_APFS/Developer/GitHub/antigravity-mesh/data/00_fundamenty/00_indeks.md", resolved)

        val subFile = "/Volumes/MAC_STORAGE_APFS/Developer/GitHub/antigravity-mesh/data/00_fundamenty/00_indeks.md"
        val parentTarget = "../01_zdrowie/01_indeks.md"
        val resolvedParent = com.antigravity.mesh.ui.components.resolveRelativeFilePath(subFile, parentTarget)
        assertEquals("/Volumes/MAC_STORAGE_APFS/Developer/GitHub/antigravity-mesh/data/01_zdrowie/01_indeks.md", resolvedParent)

        val dotSlashTarget = "./subfolder/doc.md"
        val resolvedDot = com.antigravity.mesh.ui.components.resolveRelativeFilePath(baseMac, dotSlashTarget)
        assertEquals("/Volumes/MAC_STORAGE_APFS/Developer/GitHub/antigravity-mesh/subfolder/doc.md", resolvedDot)

        val absTarget = "/etc/hosts"
        val resolvedAbs = com.antigravity.mesh.ui.components.resolveRelativeFilePath(baseMac, absTarget)
        assertEquals("/etc/hosts", resolvedAbs)

        // Past root traversal should not crash or go out of bounds
        val overRoot = "../../../../../../root.md"
        val resolvedOverRoot = com.antigravity.mesh.ui.components.resolveRelativeFilePath(baseMac, overRoot)
        assertTrue(resolvedOverRoot.endsWith("root.md"))

        // Windows path resolution simulation
        val winPath = """C:\Users\kacper\Developer\antigravity-mesh\README.md"""
        val winRelTarget = """data\00_fundamenty\00_indeks.md"""
        val winResolved = com.antigravity.mesh.ui.components.resolveRelativeFilePath(winPath, winRelTarget)
        assertEquals("""C:\Users\kacper\Developer\antigravity-mesh\data\00_fundamenty\00_indeks.md""", winResolved)

        val winParentTarget = """..\01_zdrowie\01_indeks.md"""
        val winSubFile = """C:\Users\kacper\Developer\antigravity-mesh\data\00_fundamenty\00_indeks.md"""
        val winResolvedParent = com.antigravity.mesh.ui.components.resolveRelativeFilePath(winSubFile, winParentTarget)
        assertEquals("""C:\Users\kacper\Developer\antigravity-mesh\data\01_zdrowie\01_indeks.md""", winResolvedParent)
    }

    @Test
    fun testParseInlineMarkdownUrlWithBalancedParentheses() {
        val markdown = "[Funkcja matematyczna](https://pl.wikipedia.org/wiki/Funkcja_(matematyka)) opis"
        val result = parseInlineMarkdown(markdown)
        assertEquals("Funkcja matematyczna opis", result.text)
    }

    @Test
    fun testParseInlineMarkdownBareUrlStripsTrailingPeriod() {
        val markdown = "Sprawdź stronę https://antigravity.mesh."
        val result = parseInlineMarkdown(markdown)
        assertEquals("Sprawdź stronę https://antigravity.mesh.", result.text)
    }

    @Test
    fun testParseInlineMarkdownNestedBracketsInLabel() {
        val markdown = "[[WAŻNE] Dokumentacja API](docs/api.md)"
        val result = parseInlineMarkdown(markdown)
        assertEquals("[WAŻNE] Dokumentacja API", result.text)
    }

    @Test
    fun testParseInlineMarkdownStrikethroughWithNestedBold() {
        val markdown = "Cena: ~~**150 zł**~~ 99 zł"
        val result = parseInlineMarkdown(markdown)
        assertEquals("Cena: 150 zł 99 zł", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownMultipleLinksOnSingleLine() {
        val markdown = "Odwiedź [Strona 1](https://one.com) oraz [Strona 2](https://two.com) dzisiaj."
        val result = parseInlineMarkdown(markdown)
        assertEquals("Odwiedź Strona 1 oraz Strona 2 dzisiaj.", result.text)
    }

    @Test
    fun testParseInlineMarkdownDeepRecursionAndNesting() {
        val markdown = "Styl: ***~~mocno sformatowany tekst~~*** koniec"
        val result = parseInlineMarkdown(markdown)
        assertEquals("Styl: mocno sformatowany tekst koniec", result.text)
        assertTrue("Musi zawierać style", result.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineMarkdownUnclosedTagsDoNotCrashOrLoop() {
        val unclosed1 = "To jest **niezamknięty pogrubiony tekst"
        val res1 = parseInlineMarkdown(unclosed1)
        assertNotNull(res1.text)

        val unclosed2 = "To jest ~~niezamknięte przekreślenie"
        val res2 = parseInlineMarkdown(unclosed2)
        assertNotNull(res2.text)

        val unclosed3 = "Niepełny link [Opis bez adresu"
        val res3 = parseInlineMarkdown(unclosed3)
        assertEquals("Niepełny link [Opis bez adresu", res3.text)
    }

    @Test
    fun testParseInlineMarkdownUrlWithComplexQueryParamsAndFragment() {
        var clickedUrl: String? = null
        val markdown = "Zapytanie: [Dokumentacja](https://mesh.internal:8888/v2/query?filter=all&sort=desc&tag=%23main#section-3) szczegóły"
        val result = parseInlineMarkdown(markdown, onLinkClick = { clickedUrl = it })
        assertEquals("Zapytanie: Dokumentacja szczegóły", result.text)
        val linkAnnotation = result.getLinkAnnotations(0, result.text.length).firstOrNull()
        assertNotNull("Musi zawierać LinkAnnotation dla linku", linkAnnotation)
        val clickable = linkAnnotation?.item as? androidx.compose.ui.text.LinkAnnotation.Clickable
        assertNotNull("LinkAnnotation musi być typu Clickable", clickable)
        assertEquals("https://mesh.internal:8888/v2/query?filter=all&sort=desc&tag=%23main#section-3", clickable?.tag)
    }

    @Test(timeout = 2000)
    fun testParseInlineMarkdownStressLargeDocument() {
        val sb = StringBuilder()
        for (i in 1..1000) {
            sb.append("Linia $i: **Pogrubienie**, *kursywa*, `kod inline`, ~~przekreślenie~~, [Link](https://site$i.com).\n")
        }
        val result = parseInlineMarkdown(sb.toString())
        assertTrue(result.text.length > 30000)
    }
}

