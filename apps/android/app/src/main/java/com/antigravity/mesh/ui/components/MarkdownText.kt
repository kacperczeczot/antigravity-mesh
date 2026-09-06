package com.antigravity.mesh.ui.components

import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.ui.theme.*

private enum class CalloutType(
    val title: String,
    val icon: ImageVector,
    val color: Color
) {
    NOTE("NOTE", Icons.Default.Info, AccentCyan),
    TIP("TIP", Icons.Default.Lightbulb, AccentGreen),
    IMPORTANT("IMPORTANT", Icons.Default.PriorityHigh, AccentViolet),
    WARNING("WARNING", Icons.Default.Warning, AccentAmber),
    CAUTION("CAUTION", Icons.Default.ErrorOutline, AccentRed)
}

// ─── Code Syntax Highlighting Palette ───────────────────────────────────────
private val SyntaxKeyword = Color(0xFFA78BFA)    // Violet / Purple
private val SyntaxString = Color(0xFF34D399)     // Soft Emerald Green
private val SyntaxNumber = Color(0xFFFBBF24)     // Warm Amber
private val SyntaxComment = Color(0xFF64748B)    // Muted Slate Gray (Italic)
private val SyntaxType = Color(0xFF38BDF8)       // Sky Cyan (Classes, Types, Components)
private val SyntaxAnnotation = Color(0xFFF472B6) // Rose Pink (@Composable, @Test)
private val SyntaxConstant = Color(0xFFF43F5E)   // Rose Red (true/false/null)
private val SyntaxPlain = Color(0xFFE2E8F0)      // Off-White Default Code

internal fun highlightCode(code: String, language: String? = null): AnnotatedString {
    val lang = language?.lowercase()?.trim() ?: ""
    val builder = AnnotatedString.Builder(code)
    val len = code.length
    if (len == 0) return builder.toAnnotatedString()

    builder.addStyle(SpanStyle(color = SyntaxPlain), 0, len)
    val occupied = BooleanArray(len)

    // 1. Comments
    val commentRegexes = mutableListOf<Regex>()
    if (lang in listOf("python", "py", "sh", "bash", "yaml", "yml")) {
        commentRegexes.add(Regex("""#.*$""", RegexOption.MULTILINE))
    } else if (lang == "sql") {
        commentRegexes.add(Regex("""--.*$""", RegexOption.MULTILINE))
        commentRegexes.add(Regex("""/\*[\s\S]*?\*/"""))
    } else if (lang == "mermaid") {
        commentRegexes.add(Regex("""%%.*$""", RegexOption.MULTILINE))
    } else {
        commentRegexes.add(Regex("""//.*$""", RegexOption.MULTILINE))
        commentRegexes.add(Regex("""/\*[\s\S]*?\*/"""))
        if (lang.isEmpty()) {
            commentRegexes.add(Regex("""#.*$""", RegexOption.MULTILINE))
        }
    }

    for (regex in commentRegexes) {
        for (match in regex.findAll(code)) {
            val range = match.range
            builder.addStyle(SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic), range.first, range.last + 1)
            for (idx in range) {
                if (idx < len) occupied[idx] = true
            }
        }
    }

    // 2. Strings ("...", '...', `...`)
    val stringRegex = Regex(""""([^"\\]|\\.)*"|'([^'\\]|\\.)*'|`([^`\\]|\\.)*`""")
    for (match in stringRegex.findAll(code)) {
        val range = match.range
        if (range.first < len && !occupied[range.first]) {
            builder.addStyle(SpanStyle(color = SyntaxString), range.first, range.last + 1)
            for (idx in range) {
                if (idx < len) occupied[idx] = true
            }
        }
    }

    // Helper for non-overlapping token styling
    fun styleMatches(regex: Regex, style: SpanStyle) {
        for (match in regex.findAll(code)) {
            val range = match.range
            val start = range.first
            val end = range.last + 1
            if (start < len && !occupied[start]) {
                var canStyle = true
                for (idx in start until end) {
                    if (idx < len && occupied[idx]) { canStyle = false; break }
                }
                if (canStyle) {
                    builder.addStyle(style, start, end)
                    for (idx in start until end) {
                        if (idx < len) occupied[idx] = true
                    }
                }
            }
        }
    }

    // 3. Annotations (@Composable, @Test, @Override)
    styleMatches(Regex("""@[A-Za-z0-9_]+"""), SpanStyle(color = SyntaxAnnotation, fontWeight = FontWeight.SemiBold))

    // 4. Numbers
    styleMatches(Regex("""\b(0x[0-9a-fA-F]+|\d+(\.\d+)?([eE][+-]?\d+)?)\b"""), SpanStyle(color = SyntaxNumber))

    // 5. Booleans / Null / Constants
    styleMatches(Regex("""\b(true|false|True|False|null|None|nil|undefined|NaN)\b"""), SpanStyle(color = SyntaxConstant, fontWeight = FontWeight.SemiBold))

    // 6. Mermaid specific keywords & arrows
    if (lang == "mermaid") {
        styleMatches(
            Regex("""\b(sequenceDiagram|flowchart|graph|subgraph|end|participant|actor|autonumber|note|over|loop|alt|opt|par|critical|break|rect|activate|deactivate|classDiagram|stateDiagram|erDiagram)\b"""),
            SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold)
        )
        styleMatches(
            Regex("""(-->|->>|->|--|\=\=>|-\.->|x--|--x|o--|--o|\|)"""),
            SpanStyle(color = SyntaxNumber, fontWeight = FontWeight.Bold)
        )
    }

    // 7. Language Keywords
    styleMatches(
        Regex("""\b(val|var|fun|fn|def|function|class|struct|enum|interface|trait|impl|type|object|package|import|from|export|default|return|if|elif|else|when|switch|case|for|while|loop|do|in|as|is|match|break|continue|yield|async|await|try|catch|finally|throw|raise|override|suspend|private|public|protected|internal|mut|pub|use|mod|let|const|static|extern|where|self|this|super|new|typeof|instanceof|select|where|insert|update|delete|join|group|order|limit|echo|set|exit)\b"""),
        SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold)
    )

    // 8. Types, Classes & Components (PascalCase or primitives)
    styleMatches(
        Regex("""\b([A-Z][A-Za-z0-9_]*|bool|i32|i64|u32|u64|usize|isize|f32|f64|str|void|int|char|float|double|number|string|boolean|any|dict|tuple)\b"""),
        SpanStyle(color = SyntaxType)
    )

    return builder.toAnnotatedString()
}

/**
 * Rich, complete Jetpack Compose Markdown renderer supporting:
 * - Headers H1-H6
 * - Text styling: bold (** and __), italic (* and _), bold-italic (*** and ___), strikethrough (~~)
 * - Tags: <kbd>, <sub>, <sup>, <br>
 * - GFM Callout Alerts: [!NOTE], [!TIP], [!IMPORTANT], [!WARNING], [!CAUTION]
 * - Interactive Task Lists: - [ ] and - [x]
 * - Ordered (numbered) lists: 1., 2.
 * - Unordered bullet lists: - and *
 * - Horizontal rules: ---, ***, ___
 * - Tables with column alignments (:---, :---:, ---:)
 * - Interactive HTML details/summary accordions (<details><summary>)
 * - Code blocks with copy button & language pill
 * - LaTeX / Math blocks ($$ ... $$) and inline math ($...$)
 * - Links: markdown links [text](url) and bare URLs
 */
@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    val lines = markdown.split("\n")

    // Block accumulation states
    var inCodeBlock = false
    var currentLanguage: String? = null
    val currentCodeBlock = StringBuilder()

    var inMathBlock = false
    val currentMathBlock = StringBuilder()

    val currentTableLines = mutableListOf<String>()

    var activeCalloutType: CalloutType? = null
    val currentCalloutLines = mutableListOf<String>()

    val currentBlockquoteLines = mutableListOf<String>()

    var inDetailsBlock = false
    var inSummaryTag = false
    var detailsSummary: String? = null
    val currentSummaryContent = StringBuilder()
    val currentDetailsContent = StringBuilder()

    fun isTableLine(l: String): Boolean {
        val t = l.trim()
        return t.startsWith("|") || (t.count { it == '|' } >= 2)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in lines) {
            val trimmedLine = line.trim()
            val trimmedEnd = line.trimEnd()

            // 0. HTML <details> and <summary> — MUST take priority so inner code blocks belong to details
            if (inDetailsBlock) {
                if (trimmedLine.contains("</details>", ignoreCase = true)) {
                    val beforeClose = trimmedLine.substringBefore("</details>", "")
                    if (beforeClose.isNotEmpty()) {
                        if (inSummaryTag) {
                            val beforeSumClose = beforeClose.substringBefore("</summary>", "")
                            currentSummaryContent.append(beforeSumClose)
                            detailsSummary = currentSummaryContent.toString().trim()
                            val afterSumClose = beforeClose.substringAfter("</summary>", "").trim()
                            if (afterSumClose.isNotEmpty()) {
                                if (currentDetailsContent.isNotEmpty()) currentDetailsContent.append("\n")
                                currentDetailsContent.append(afterSumClose)
                            }
                        } else {
                            if (currentDetailsContent.isNotEmpty()) currentDetailsContent.append("\n")
                            currentDetailsContent.append(beforeClose)
                        }
                    }

                    val finalSummary = detailsSummary?.ifBlank { null }
                        ?: currentSummaryContent.toString().trim().ifEmpty { "Szczegóły" }

                    ExpandableDetailsBlock(
                        summary = finalSummary,
                        content = currentDetailsContent.toString().trim(),
                        onLinkClick = onLinkClick
                    )

                    inDetailsBlock = false
                    inSummaryTag = false
                    detailsSummary = null
                    currentDetailsContent.clear()
                    currentSummaryContent.clear()
                    continue
                }

                if (inSummaryTag) {
                    if (trimmedLine.contains("</summary>", ignoreCase = true)) {
                        val beforeClose = line.substringBefore("</summary>", "")
                        currentSummaryContent.append(beforeClose)
                        detailsSummary = currentSummaryContent.toString().trim()
                        inSummaryTag = false
                        val afterClose = line.substringAfter("</summary>", "").trim()
                        if (afterClose.isNotEmpty()) {
                            if (currentDetailsContent.isNotEmpty()) currentDetailsContent.append("\n")
                            currentDetailsContent.append(afterClose)
                        }
                    } else {
                        if (currentSummaryContent.isNotEmpty()) currentSummaryContent.append("\n")
                        currentSummaryContent.append(line)
                    }
                    continue
                }

                if (trimmedLine.contains("<summary", ignoreCase = true)) {
                    val afterOpen = line.substringAfter(">", "")
                    if (afterOpen.contains("</summary>", ignoreCase = true)) {
                        detailsSummary = afterOpen.substringBefore("</summary>", "").trim()
                        val afterClose = afterOpen.substringAfter("</summary>", "").trim()
                        if (afterClose.isNotEmpty()) {
                            if (currentDetailsContent.isNotEmpty()) currentDetailsContent.append("\n")
                            currentDetailsContent.append(afterClose)
                        }
                    } else {
                        inSummaryTag = true
                        currentSummaryContent.append(afterOpen)
                    }
                    continue
                }

                if (currentDetailsContent.isNotEmpty()) currentDetailsContent.append("\n")
                currentDetailsContent.append(line)
                continue
            }

            // Open <details> block: must not be a heading (#) or inline code (`<details`)
            val isDetailsOpenTag = !trimmedLine.startsWith("#") &&
                !trimmedLine.contains("`<details") &&
                (trimmedLine.startsWith("<details", ignoreCase = true) ||
                 Regex("""(?i)^\s*<details(\s+[^>]*)?>""").containsMatchIn(trimmedLine))

            if (isDetailsOpenTag) {
                if (currentTableLines.isNotEmpty()) {
                    MarkdownTable(lines = currentTableLines.toList(), onLinkClick = onLinkClick)
                    currentTableLines.clear()
                }
                if (activeCalloutType != null) {
                    CalloutAlertCard(type = activeCalloutType!!, body = currentCalloutLines.joinToString("\n"), onLinkClick = onLinkClick)
                    activeCalloutType = null
                    currentCalloutLines.clear()
                }
                if (currentBlockquoteLines.isNotEmpty()) {
                    BlockquoteCard(quoteText = currentBlockquoteLines.joinToString("\n"), onLinkClick = onLinkClick)
                    currentBlockquoteLines.clear()
                }

                inDetailsBlock = true
                inSummaryTag = false
                detailsSummary = null
                currentDetailsContent.clear()
                currentSummaryContent.clear()

                val afterDetails = line.substringAfter(">", "")
                if (afterDetails.contains("<summary", ignoreCase = true)) {
                    val afterOpen = afterDetails.substringAfter(">", "")
                    if (afterOpen.contains("</summary>", ignoreCase = true)) {
                        detailsSummary = afterOpen.substringBefore("</summary>", "").trim()
                        val afterClose = afterOpen.substringAfter("</summary>", "").trim()
                        if (afterClose.isNotEmpty()) {
                            currentDetailsContent.append(afterClose)
                        }
                    } else {
                        inSummaryTag = true
                        currentSummaryContent.append(afterOpen)
                    }
                } else if (afterDetails.trim().isNotEmpty()) {
                    currentDetailsContent.append(afterDetails.trim())
                }
                continue
            }

            // 1. Multi-line Code Block Handling (```)
            if (trimmedLine.startsWith("```")) {
                // Flush other pending blocks
                if (currentTableLines.isNotEmpty()) {
                    MarkdownTable(lines = currentTableLines.toList(), onLinkClick = onLinkClick)
                    currentTableLines.clear()
                }
                if (activeCalloutType != null) {
                    CalloutAlertCard(type = activeCalloutType!!, body = currentCalloutLines.joinToString("\n"), onLinkClick = onLinkClick)
                    activeCalloutType = null
                    currentCalloutLines.clear()
                }
                if (currentBlockquoteLines.isNotEmpty()) {
                    BlockquoteCard(quoteText = currentBlockquoteLines.joinToString("\n"), onLinkClick = onLinkClick)
                    currentBlockquoteLines.clear()
                }

                if (inCodeBlock) {
                    val codeContent = currentCodeBlock.toString().trimIndent()
                    if (currentLanguage?.lowercase()?.trim() == "mermaid") {
                        MermaidDiagramCard(code = codeContent)
                    } else {
                        CodeBlock(code = codeContent, language = currentLanguage)
                    }
                    currentCodeBlock.clear()
                    currentLanguage = null
                    inCodeBlock = false
                } else {
                    val afterOpen = trimmedLine.removePrefix("```")
                    val closeIdx = afterOpen.lastIndexOf("```")
                    if (closeIdx != -1) {
                        // Single-line code block: ```lang code```
                        val firstSpace = afterOpen.indexOfAny(charArrayOf(' ', '\t'))
                        val (lang, code) = if (firstSpace != -1 && firstSpace < closeIdx) {
                            val l = afterOpen.substring(0, firstSpace).trim().ifBlank { null }
                            val c = afterOpen.substring(firstSpace, closeIdx).trim()
                            l to c
                        } else {
                            null to afterOpen.substring(0, closeIdx).trim()
                        }
                        if (lang?.lowercase()?.trim() == "mermaid") {
                            MermaidDiagramCard(code = code)
                        } else {
                            CodeBlock(code = code, language = lang)
                        }
                    } else {
                        currentLanguage = afterOpen.trim().ifBlank { null }
                        inCodeBlock = true
                    }
                }
                continue
            }

            if (inCodeBlock) {
                if (currentCodeBlock.isNotEmpty()) currentCodeBlock.append("\n")
                currentCodeBlock.append(line)
                continue
            }

            // 2. Math Block ($$ ... $$)
            if (trimmedLine == "$$" || (trimmedLine.startsWith("$$") && !trimmedLine.endsWith("$$"))) {
                if (inMathBlock) {
                    MathBlock(currentMathBlock.toString().trim())
                    currentMathBlock.clear()
                    inMathBlock = false
                } else {
                    inMathBlock = true
                    val rem = trimmedLine.removePrefix("$$").trim()
                    if (rem.isNotEmpty()) currentMathBlock.append(rem)
                }
                continue
            }

            if (inMathBlock) {
                if (trimmedLine.endsWith("$$")) {
                    val rem = trimmedLine.removeSuffix("$$").trim()
                    if (rem.isNotEmpty()) {
                        if (currentMathBlock.isNotEmpty()) currentMathBlock.append("\n")
                        currentMathBlock.append(rem)
                    }
                    MathBlock(currentMathBlock.toString().trim())
                    currentMathBlock.clear()
                    inMathBlock = false
                } else {
                    if (currentMathBlock.isNotEmpty()) currentMathBlock.append("\n")
                    currentMathBlock.append(line)
                }
                continue
            }

            // Standalone single-line math block: $$E = mc^2$$
            if (trimmedLine.startsWith("$$") && trimmedLine.endsWith("$$") && trimmedLine.length > 4) {
                MathBlock(trimmedLine.removePrefix("$$").removeSuffix("$$").trim())
                continue
            }



            // 4. Tables
            if (isTableLine(trimmedEnd)) {
                currentTableLines.add(trimmedEnd)
                continue
            } else if (currentTableLines.isNotEmpty()) {
                MarkdownTable(lines = currentTableLines.toList(), onLinkClick = onLinkClick)
                currentTableLines.clear()
            }

            // 5. GFM Callout Alert or Blockquote
            if (trimmedLine.startsWith(">")) {
                val quoteBody = trimmedLine.removePrefix(">").trimStart()
                val calloutMatch = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*(.*)$""", RegexOption.IGNORE_CASE).find(quoteBody)

                if (calloutMatch != null && activeCalloutType == null) {
                    val typeStr = calloutMatch.groupValues[1].uppercase()
                    val rest = calloutMatch.groupValues[2].trim()
                    activeCalloutType = CalloutType.values().find { it.name == typeStr } ?: CalloutType.NOTE
                    if (rest.isNotEmpty()) currentCalloutLines.add(rest)
                    continue
                } else if (activeCalloutType != null) {
                    currentCalloutLines.add(quoteBody)
                    continue
                } else {
                    currentBlockquoteLines.add(quoteBody)
                    continue
                }
            } else {
                if (activeCalloutType != null) {
                    CalloutAlertCard(type = activeCalloutType!!, body = currentCalloutLines.joinToString("\n"), onLinkClick = onLinkClick)
                    activeCalloutType = null
                    currentCalloutLines.clear()
                }
                if (currentBlockquoteLines.isNotEmpty()) {
                    BlockquoteCard(quoteText = currentBlockquoteLines.joinToString("\n"), onLinkClick = onLinkClick)
                    currentBlockquoteLines.clear()
                }
            }

            // Blank line
            if (trimmedLine.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                continue
            }

            val leadingSpaces = trimmedEnd.length - trimmedEnd.trimStart().length
            val indentPadding = if (leadingSpaces > 0) (4 + (leadingSpaces / 2) * 8).dp else 4.dp
            val stripped = trimmedLine

            // 6. Horizontal Rule (---, ***, ___)
            if (stripped == "---" || stripped == "***" || stripped == "___" || stripped.matches(Regex("""^[-*_]{3,}$"""))) {
                HorizontalDivider(
                    color = BorderDark,
                    thickness = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                continue
            }

            // 7. Task Lists (- [ ] or - [x])
            val taskMatch = Regex("""^(\s*)[-\*]\s+\[([ xX])\]\s*(.*)$""").find(line)
            if (taskMatch != null) {
                val leadingSpaceCount = taskMatch.groupValues[1].length
                val isChecked = taskMatch.groupValues[2].equals("x", ignoreCase = true)
                val taskText = taskMatch.groupValues[3]
                val itemIndent = if (leadingSpaceCount > 0) (4 + (leadingSpaceCount / 2) * 8).dp else 4.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = itemIndent, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (isChecked) "Wykonane" else "Do zrobienia",
                        tint = if (isChecked) AccentCyan else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = parseInlineMarkdown(taskText, onLinkClick),
                        fontSize = 14.sp,
                        color = if (isChecked) TextMuted else textColor,
                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                continue
            }

            // 8. Numbered Lists (1., 2., etc.)
            val numMatch = Regex("""^(\s*)(\d+)[\.\)]\s*(.*)$""").find(line)
            if (numMatch != null) {
                val leadingSpaceCount = numMatch.groupValues[1].length
                val number = numMatch.groupValues[2]
                val itemText = numMatch.groupValues[3]
                val itemIndent = if (leadingSpaceCount > 0) (4 + (leadingSpaceCount / 2) * 8).dp else 4.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = itemIndent, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "$number.",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AccentCyan,
                        modifier = Modifier.widthIn(min = 24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = parseInlineMarkdown(itemText, onLinkClick),
                        fontSize = 14.sp,
                        color = textColor,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                continue
            }

            // 9. Headings H1 - H6
            when {
                stripped.startsWith("###### ") -> {
                    Text(
                        text = parseInlineMarkdown(stripped.removePrefix("###### "), onLinkClick),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                stripped.startsWith("##### ") -> {
                    Text(
                        text = parseInlineMarkdown(stripped.removePrefix("##### "), onLinkClick),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                stripped.startsWith("#### ") -> {
                    Text(
                        text = parseInlineMarkdown(stripped.removePrefix("#### "), onLinkClick),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                stripped.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(stripped.removePrefix("### "), onLinkClick),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                stripped.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(stripped.removePrefix("## "), onLinkClick),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                stripped.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(stripped.removePrefix("# "), onLinkClick),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                stripped.startsWith("- ") || stripped.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.padding(start = indentPadding, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "• ", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                        Text(
                            text = parseInlineMarkdown(stripped.substring(2), onLinkClick),
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmedEnd, onLinkClick),
                        fontSize = 14.sp,
                        color = textColor,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Flush remaining buffers after loop
        if (currentTableLines.isNotEmpty()) {
            MarkdownTable(lines = currentTableLines.toList(), onLinkClick = onLinkClick)
        }
        if (activeCalloutType != null) {
            CalloutAlertCard(type = activeCalloutType!!, body = currentCalloutLines.joinToString("\n"), onLinkClick = onLinkClick)
        }
        if (currentBlockquoteLines.isNotEmpty()) {
            BlockquoteCard(quoteText = currentBlockquoteLines.joinToString("\n"), onLinkClick = onLinkClick)
        }
        if (inCodeBlock && currentCodeBlock.isNotEmpty()) {
            CodeBlock(code = currentCodeBlock.toString().trimIndent(), language = currentLanguage)
        }
        if (inMathBlock && currentMathBlock.isNotEmpty()) {
            MathBlock(formula = currentMathBlock.toString().trim())
        }
        if (inDetailsBlock && currentDetailsContent.isNotEmpty()) {
            ExpandableDetailsBlock(
                summary = detailsSummary ?: "Szczegóły",
                content = currentDetailsContent.toString().trim(),
                onLinkClick = onLinkClick
            )
        }
    }
}

/**
 * GFM Callout Alert Card ([!NOTE], [!TIP], [!IMPORTANT], [!WARNING], [!CAUTION])
 */
@Composable
private fun CalloutAlertCard(
    type: CalloutType,
    body: String,
    onLinkClick: ((String) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, type.color.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = type.color.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(type.color)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        tint = type.color,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = type.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = type.color
                    )
                }
                if (body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = parseInlineMarkdown(body, onLinkClick),
                        fontSize = 13.sp,
                        color = TextPrimary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Styled Blockquote Card for markdown `> quote`
 */
@Composable
private fun BlockquoteCard(
    quoteText: String,
    onLinkClick: ((String) -> Unit)? = null
) {
    val lines = quoteText.lines()
    val hasNested = lines.any { it.trimStart().startsWith(">") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
            .background(SurfaceVariantDark.copy(alpha = 0.5f))
            .padding(end = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .fillMaxHeight()
                .background(AccentCyan.copy(alpha = 0.6f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!hasNested) {
                Text(
                    text = parseInlineMarkdown(quoteText, onLinkClick),
                    fontSize = 13.5.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )
            } else {
                val segments = mutableListOf<Pair<List<String>, Boolean>>()
                val currentGroup = mutableListOf<String>()
                var currentIsNested = false

                for (l in lines) {
                    val isLNested = l.trimStart().startsWith(">")
                    val cleaned = if (isLNested) l.trimStart().removePrefix(">").trimStart() else l
                    if (isLNested != currentIsNested) {
                        if (currentGroup.isNotEmpty()) {
                            segments.add(currentGroup.toList() to currentIsNested)
                            currentGroup.clear()
                        }
                        currentIsNested = isLNested
                    }
                    currentGroup.add(cleaned)
                }
                if (currentGroup.isNotEmpty()) {
                    segments.add(currentGroup.toList() to currentIsNested)
                }

                for ((group, isNested) in segments) {
                    val text = group.joinToString("\n")
                    if (isNested) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(SurfaceDark.copy(alpha = 0.7f))
                                .padding(end = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.5.dp)
                                    .fillMaxHeight()
                                    .background(AccentCyan.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parseInlineMarkdown(text, onLinkClick),
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = TextSecondary,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = parseInlineMarkdown(text, onLinkClick),
                            fontSize = 13.5.sp,
                            fontStyle = FontStyle.Italic,
                            color = TextSecondary,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Centered LaTeX / Mathematical formula block
 */
@Composable
private fun MathBlock(formula: String) {
    val scrollState = rememberScrollState()
    val pretty = remember(formula) { prettifyMath(formula) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .background(SurfaceVariantDark)
            .horizontalScroll(scrollState)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = pretty,
            fontFamily = FontFamily.Monospace,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AccentCyan,
            softWrap = false,
            modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
        )
    }
}

/**
 * Expandable HTML <details><summary> section
 */
@Composable
private fun ExpandableDetailsBlock(
    summary: String,
    content: String,
    onLinkClick: ((String) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val cleanSummary = remember(summary) {
        summary.trimStart('▶', '►', '▸', '▼', '▾', '▲', '▴', '>', ' ').trim()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .background(SurfaceDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .background(SurfaceVariantDark)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Zwiń" else "Rozwiń",
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp).padding(top = 1.dp)
                )
                Text(
                    text = parseInlineMarkdown(cleanSummary, onLinkClick),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }
        AnimatedVisibility(visible = isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                if (content.isNotBlank()) {
                    MarkdownText(
                        markdown = content,
                        onLinkClick = onLinkClick
                    )
                } else {
                    Text(
                        text = "Brak dodatkowej zawartości.",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Markdown Table with horizontal scrolling and column alignments
 */
@Composable
private fun MarkdownTable(
    lines: List<String>,
    onLinkClick: ((String) -> Unit)? = null
) {
    // Determine column alignments from separator row (line containing dashes)
    val alignments = lines.getOrNull(1)?.let { sepLine ->
        val t = sepLine.trim()
        if (t.contains("---") || t.contains(":-") || t.contains("-:")) {
            t.trim('|').split("|").map { cell ->
                val c = cell.trim()
                when {
                    c.startsWith(":") && c.endsWith(":") -> TextAlign.Center
                    c.endsWith(":") -> TextAlign.End
                    else -> TextAlign.Start
                }
            }
        } else null
    } ?: emptyList()

    val cleanRows = lines.filterNot { l ->
        val t = l.trim()
        t.contains("---") || t.contains(":-") || t.contains("-:")
    }.map { row ->
        row.trim()
            .trim('|')
            .split("|")
            .map { it.trim() }
    }

    if (cleanRows.isEmpty()) return

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
            .background(SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(8.dp)
        ) {
            cleanRows.forEachIndexed { rowIndex, cells ->
                val isHeader = rowIndex == 0
                Row(
                    modifier = Modifier
                        .background(
                            if (isHeader) SurfaceVariantDark else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    cells.forEachIndexed { colIndex, cellText ->
                        val align = alignments.getOrElse(colIndex) { TextAlign.Start }
                        Text(
                            text = parseInlineMarkdown(cellText, onLinkClick),
                            fontSize = 12.sp,
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHeader) AccentCyan else TextPrimary,
                            textAlign = align,
                            modifier = Modifier.widthIn(min = 90.dp)
                        )
                    }
                }
                if (rowIndex < cleanRows.size - 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

/**
 * Interactive visual Mermaid diagram renderer with toggle to source code
 */
@Composable
private fun MermaidDiagramCard(code: String) {
    var showVisual by remember { mutableStateOf(true) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "MERMAID",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentCyan
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Toggle Mode (Wizualizacja / Kod)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showVisual) AccentCyan.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, if (showVisual) AccentCyan else BorderDark, RoundedCornerShape(4.dp))
                        .clickable { showVisual = !showVisual }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (showVisual) "Wizualizacja" else "Kod",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showVisual) AccentCyan else TextSecondary
                    )
                }

                // Copy button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            Toast.makeText(context, "Skopiowano kod Mermaid do schowka", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Kopiuj kod",
                        tint = TextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Kopiuj",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (showVisual) {
            MermaidWebView(code = code)
        } else {
            CodeBlock(code = code, language = "mermaid")
        }
    }
}

@Composable
private fun MermaidWebView(
    code: String,
    modifier: Modifier = Modifier
) {
    val escapedCode = remember(code) {
        code.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    val htmlContent = remember(escapedCode) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            <style>
                * { box-sizing: border-box; }
                body {
                    margin: 0;
                    padding: 14px;
                    background-color: #0F172A;
                    color: #E2E8F0;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    overflow: auto;
                    min-height: 140px;
                }
                .mermaid {
                    width: 100%;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                }
                svg {
                    max-width: 100% !important;
                    height: auto !important;
                }
                #loading {
                    color: #94A3B8;
                    font-size: 11px;
                    text-align: center;
                    font-family: sans-serif;
                    padding: 24px;
                }
                #error {
                    display: none;
                    color: #F87171;
                    font-size: 11px;
                    font-family: monospace;
                    padding: 8px;
                    background: #1E293B;
                    border-radius: 4px;
                    border: 1px solid #7F1D1D;
                    white-space: pre-wrap;
                    word-break: break-all;
                }
            </style>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
            <script>
                function renderDiagram() {
                    try {
                        if (typeof mermaid === 'undefined') {
                            setTimeout(renderDiagram, 200);
                            return;
                        }
                        const loader = document.getElementById('loading');
                        if (loader) loader.style.display = 'none';

                        mermaid.initialize({
                            startOnLoad: false,
                            theme: 'dark',
                            themeVariables: {
                                darkMode: true,
                                background: '#0F172A',
                                primaryColor: '#6366F1',
                                primaryTextColor: '#F8FAFC',
                                primaryBorderColor: '#818CF8',
                                lineColor: '#38BDF8',
                                secondaryColor: '#1E293B',
                                tertiaryColor: '#0F172A',
                                mainBkg: '#1E293B',
                                nodeBorder: '#818CF8',
                                clusterBkg: '#1E293B'
                            }
                        });
                        mermaid.run().catch(err => {
                            const errDiv = document.getElementById('error');
                            if (errDiv) {
                                errDiv.style.display = 'block';
                                errDiv.innerText = 'Błąd składni diagramu Mermaid: ' + err.message;
                            }
                        });
                    } catch (e) {
                        const errDiv = document.getElementById('error');
                        if (errDiv) {
                            errDiv.style.display = 'block';
                            errDiv.innerText = 'Błąd: ' + e.message;
                        }
                    }
                }
                window.addEventListener('DOMContentLoaded', renderDiagram);
            </script>
        </head>
        <body>
            <div id="loading">Generowanie diagramu Mermaid...</div>
            <div id="error"></div>
            <pre class="mermaid">
$escapedCode
            </pre>
        </body>
        </html>
        """.trimIndent()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 360.dp)
            .background(Color(0xFF0F172A))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.setSupportZoom(true)
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL("https://cdn.jsdelivr.net", htmlContent, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", htmlContent, "text/html", "UTF-8", null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 360.dp)
        )
    }
}

/**
 * Code Block with copy button and language identifier
 */
@Composable
private fun CodeBlock(code: String, language: String? = null) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val highlighted = remember(code, language) {
        highlightCode(code, language)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (language?.ifBlank { null } ?: "KOD").uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = AccentCyan
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(code))
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Toast.makeText(context, "Skopiowano kod do schowka", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Kopiuj kod",
                    tint = TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Kopiuj",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(10.dp)
        ) {
            Text(
                text = highlighted,
                modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp,
                softWrap = false
            )
        }
    }
}

/**
 * Parser for inline Markdown elements:
 * - Markdown links: [label](target)
 * - Bare URLs: http://, https://, file://
 * - Bold + Italic: ***text*** and ___text___
 * - Bold: **text** and __text__
 * - Italic: *text* and _text_
 * - Strikethrough: ~~text~~
 * - Inline Code: `code`, ``code``, ```code```
 * - Inline Math: $formula$
 * - HTML tags: <kbd>key</kbd>, <sub>sub</sub>, <sup>sup</sup>, <br>
 */
internal fun parseInlineMarkdown(
    text: String,
    onLinkClick: ((String) -> Unit)? = null
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            // 1. Markdown Link: [label](target)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val rawLabel = text.substring(i + 1, closeBracket)
                        val target = text.substring(closeBracket + 2, closeParen).trim()
                        val displayLabel = rawLabel.removeSurrounding("`")

                        if (onLinkClick != null && target.isNotEmpty()) {
                            val linkAnnotation = LinkAnnotation.Clickable(
                                tag = target,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = AccentCyan,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ),
                                linkInteractionListener = { _ ->
                                    onLinkClick(target)
                                }
                            )
                            pushLink(linkAnnotation)
                            append(displayLabel)
                            pop()
                        } else {
                            withStyle(
                                SpanStyle(
                                    color = AccentCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append(displayLabel)
                            }
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // 2. Bare URL auto-link: http://, https://, or file://
            if (text.startsWith("http://", i, ignoreCase = true) ||
                text.startsWith("https://", i, ignoreCase = true) ||
                text.startsWith("file://", i, ignoreCase = true)) {
                var end = i
                while (end < len && !text[end].isWhitespace() && text[end] != ')' && text[end] != ']' && text[end] != '>' && text[end] != '"') {
                    end++
                }
                val url = text.substring(i, end)
                if (onLinkClick != null) {
                    val linkAnnotation = LinkAnnotation.Clickable(
                        tag = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = AccentCyan,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { _ -> onLinkClick(url) }
                    )
                    pushLink(linkAnnotation)
                    append(url)
                    pop()
                } else {
                    withStyle(SpanStyle(color = AccentCyan, textDecoration = TextDecoration.Underline)) {
                        append(url)
                    }
                }
                i = end
                continue
            }

            // 3. Bold + Italic: ***text*** or ___text___
            if (i + 2 < len && (text.substring(i, i + 3) == "***" || text.substring(i, i + 3) == "___")) {
                val delim = text.substring(i, i + 3)
                val end = text.indexOf(delim, i + 3)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 3
                    continue
                }
            }

            // 4. Bold: **text** or __text__
            if (i + 1 < len && (text.substring(i, i + 2) == "**" || text.substring(i, i + 2) == "__")) {
                val delim = text.substring(i, i + 2)
                val end = text.indexOf(delim, i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // 5. Strikethrough: ~~text~~
            if (i + 1 < len && text[i] == '~' && text[i + 1] == '~') {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.LineThrough,
                            color = TextMuted
                        )
                    ) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // 6. Inline Code (`code`, ``code``, or ```code```)
            if (text[i] == '`') {
                var tickCount = 0
                while (i + tickCount < len && text[i + tickCount] == '`') {
                    tickCount++
                }
                val delimiter = "`".repeat(tickCount)
                val end = text.indexOf(delimiter, i + tickCount)
                if (end != -1) {
                    val codeContent = text.substring(i + tickCount, end)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = SurfaceVariantDark,
                            color = AccentCyan
                        )
                    ) {
                        append(codeContent)
                    }
                    i = end + tickCount
                    continue
                }
            }

            // 7. Inline Math ($formula$)
            if (text[i] == '$' && i + 1 < len && text[i + 1] != ' ' && text[i + 1] != '$') {
                val end = text.indexOf('$', i + 1)
                if (end != -1 && text[end - 1] != ' ' && (end + 1 == len || text[end + 1] != '$')) {
                    val mathExpr = text.substring(i + 1, end)
                    val pretty = prettifyMath(mathExpr)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            color = AccentCyan,
                            background = SurfaceVariantDark
                        )
                    ) {
                        append(" $pretty ")
                    }
                    i = end + 1
                    continue
                }
            }

            // 8. HTML Tags: <code>, <kbd>, <sub>, <sup>, <br>
            if (text.startsWith("<code>", i, ignoreCase = true)) {
                val closeTag = text.indexOf("</code>", i + 6, ignoreCase = true)
                if (closeTag != -1) {
                    val codeContent = text.substring(i + 6, closeTag)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            background = SurfaceVariantDark,
                            color = AccentCyan
                        )
                    ) {
                        append(" $codeContent ")
                    }
                    i = closeTag + 7
                    continue
                }
            }

            if (text.startsWith("<kbd>", i, ignoreCase = true)) {
                val closeTag = text.indexOf("</kbd>", i + 5, ignoreCase = true)
                if (closeTag != -1) {
                    val keyText = text.substring(i + 5, closeTag)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            background = SurfaceElevated,
                            color = AccentCyan
                        )
                    ) {
                        append(" $keyText ")
                    }
                    i = closeTag + 6
                    continue
                }
            }

            if (text.startsWith("<sub>", i, ignoreCase = true)) {
                val closeTag = text.indexOf("</sub>", i + 5, ignoreCase = true)
                if (closeTag != -1) {
                    val subText = text.substring(i + 5, closeTag)
                    withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    ) {
                        append(subText)
                    }
                    i = closeTag + 6
                    continue
                }
            }

            if (text.startsWith("<sup>", i, ignoreCase = true)) {
                val closeTag = text.indexOf("</sup>", i + 5, ignoreCase = true)
                if (closeTag != -1) {
                    val supText = text.substring(i + 5, closeTag)
                    withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    ) {
                        append(supText)
                    }
                    i = closeTag + 6
                    continue
                }
            }

            if (text.startsWith("<br>", i, ignoreCase = true)) {
                append("\n")
                i += 4
                continue
            }
            if (text.startsWith("<br/>", i, ignoreCase = true)) {
                append("\n")
                i += 5
                continue
            }

            // 9. Italic (*text* or _text_)
            if (text[i] == '*' || text[i] == '_') {
                val delim = text[i]
                val isIntraWord = delim == '_' && i > 0 && text[i - 1].isLetterOrDigit()
                if (!isIntraWord) {
                    val end = text.indexOf(delim, i + 1)
                    if (end != -1 && end > i + 1 && (delim != '_' || end + 1 == len || !text[end + 1].isLetterOrDigit())) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                        continue
                    }
                }
            }

            append(text[i])
            i++
        }
    }
}

/**
 * Lightweight LaTeX / math beautifier translating common TeX macros and symbols into Unicode representations.
 */
private val SUPERSCRIPT_MAP = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
    'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
    'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
    'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
    'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
    'A' to 'ᴬ', 'B' to 'ᴮ', 'D' to 'ᴰ', 'E' to 'ᴱ', 'G' to 'ᴳ',
    'H' to 'ᴴ', 'I' to 'ᴵ', 'J' to 'ᴶ', 'K' to 'ᴷ', 'L' to 'ᴸ',
    'M' to 'ᴹ', 'N' to 'ᴺ', 'O' to 'ᴼ', 'P' to 'ᴾ', 'R' to 'ᴿ',
    'T' to 'ᵀ', 'U' to 'ᵁ', 'W' to 'ᵂ',
    '⁰' to '⁰', '¹' to '¹', '²' to '²', '³' to '³', '⁴' to '⁴',
    '⁵' to '⁵', '⁶' to '⁶', '⁷' to '⁷', '⁸' to '⁸', '⁹' to '⁹'
)

private val SUBSCRIPT_MAP = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
    'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
    'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
    'v' to 'ᵥ', 'x' to 'ₓ'
)

private fun toSuperscript(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
        val mapped = SUPERSCRIPT_MAP[ch]
        if (mapped != null) {
            sb.append(mapped)
        } else {
            return "^$s"
        }
    }
    return sb.toString()
}

private fun toSubscript(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
        val mapped = SUBSCRIPT_MAP[ch]
        if (mapped != null) {
            sb.append(mapped)
        } else {
            return "_$s"
        }
    }
    return sb.toString()
}

/**
 * Lightweight LaTeX / math beautifier translating common TeX macros and symbols into Unicode representations.
 */
internal fun prettifyMath(raw: String): String {
    var s = raw.trim()
    // Strip surrounding math delimiters if present
    if (s.startsWith("$$") && s.endsWith("$$") && s.length >= 4) {
        s = s.substring(2, s.length - 2).trim()
    } else if (s.startsWith("$") && s.endsWith("$") && s.length >= 2) {
        s = s.substring(1, s.length - 1).trim()
    } else if (s.startsWith("\\[") && s.endsWith("\\]") && s.length >= 4) {
        s = s.substring(2, s.length - 2).trim()
    } else if (s.startsWith("\\(") && s.endsWith("\\)") && s.length >= 4) {
        s = s.substring(2, s.length - 2).trim()
    }

    // Matrices & Environments
    s = s.replace(Regex("""\\begin\{(?:b|p|v|V|small)?matrix\}"""), "[ ")
    s = s.replace(Regex("""\\end\{(?:b|p|v|V|small)?matrix\}"""), " ]")
    s = s.replace(Regex("""\\\\"""), " ; ")
    s = s.replace("&", "  ")

    // Operator and Function declarations
    s = s.replace(Regex("""\\operatorname\*?\{([^{}]+)\}"""), "$1")
    s = s.replace(Regex("""\\operatorname\*?\s+([a-zA-Z]+)"""), "$1")
    s = s.replace(Regex("""\\DeclareMathOperator\*?\{[^}]+\}\{[^}]+\}"""), "")
    s = s.replace(Regex("""\\Var(?![a-zA-Z])"""), "Var")
    s = s.replace(Regex("""\\Cov(?![a-zA-Z])"""), "Cov")
    s = s.replace(Regex("""\\Pr(?![a-zA-Z])"""), "Pr")

    // Standard Math Functions
    s = s.replace(Regex("""\\cos(?![a-zA-Z])"""), "cos")
    s = s.replace(Regex("""\\sin(?![a-zA-Z])"""), "sin")
    s = s.replace(Regex("""\\tan(?![a-zA-Z])"""), "tan")
    s = s.replace(Regex("""\\cot(?![a-zA-Z])"""), "cot")
    s = s.replace(Regex("""\\sec(?![a-zA-Z])"""), "sec")
    s = s.replace(Regex("""\\csc(?![a-zA-Z])"""), "csc")
    s = s.replace(Regex("""\\ln(?![a-zA-Z])"""), "ln")
    s = s.replace(Regex("""\\log(?![a-zA-Z])"""), "log")
    s = s.replace(Regex("""\\exp(?![a-zA-Z])"""), "exp")
    s = s.replace(Regex("""\\det(?![a-zA-Z])"""), "det")
    s = s.replace(Regex("""\\dim(?![a-zA-Z])"""), "dim")
    s = s.replace(Regex("""\\ker(?![a-zA-Z])"""), "ker")
    s = s.replace(Regex("""\\lim(?![a-zA-Z])"""), "lim")
    s = s.replace(Regex("""\\max(?![a-zA-Z])"""), "max")
    s = s.replace(Regex("""\\min(?![a-zA-Z])"""), "min")
    s = s.replace(Regex("""\\inf(?![a-zA-Z])"""), "inf")
    s = s.replace(Regex("""\\sup(?![a-zA-Z])"""), "sup")
    s = s.replace(Regex("""\\arg(?![a-zA-Z])"""), "arg")
    s = s.replace(Regex("""\\deg(?![a-zA-Z])"""), "deg")
    s = s.replace(Regex("""\\gcd(?![a-zA-Z])"""), "gcd")

    // Scaled Delimiters & Spacing
    s = s.replace(Regex("""\\left\s*([(\[{|])"""), "$1")
    s = s.replace(Regex("""\\right\s*([)\]}|])"""), "$1")
    s = s.replace(Regex("""\\left\."""), "")
    s = s.replace(Regex("""\\right\."""), "")
    s = s.replace(Regex("""\\quad\b"""), "  ")
    s = s.replace(Regex("""\\qquad\b"""), "    ")
    s = s.replace(Regex("""\\[,;:]"""), " ")
    s = s.replace(Regex("""\\!"""), "")

    // Greek uppercase
    s = s.replace(Regex("""\\Gamma(?![a-zA-Z])"""), "Γ")
    s = s.replace(Regex("""\\Delta(?![a-zA-Z])"""), "Δ")
    s = s.replace(Regex("""\\Theta(?![a-zA-Z])"""), "Θ")
    s = s.replace(Regex("""\\Lambda(?![a-zA-Z])"""), "Λ")
    s = s.replace(Regex("""\\Xi(?![a-zA-Z])"""), "Ξ")
    s = s.replace(Regex("""\\Pi(?![a-zA-Z])"""), "Π")
    s = s.replace(Regex("""\\Sigma(?![a-zA-Z])"""), "Σ")
    s = s.replace(Regex("""\\Upsilon(?![a-zA-Z])"""), "Υ")
    s = s.replace(Regex("""\\Phi(?![a-zA-Z])"""), "Φ")
    s = s.replace(Regex("""\\Psi(?![a-zA-Z])"""), "Ψ")
    s = s.replace(Regex("""\\Omega(?![a-zA-Z])"""), "Ω")

    // Greek lowercase (using (?![a-zA-Z]) so \alpha_1, \theta_0 match)
    s = s.replace(Regex("""\\alpha(?![a-zA-Z])"""), "α")
    s = s.replace(Regex("""\\beta(?![a-zA-Z])"""), "β")
    s = s.replace(Regex("""\\gamma(?![a-zA-Z])"""), "γ")
    s = s.replace(Regex("""\\delta(?![a-zA-Z])"""), "δ")
    s = s.replace(Regex("""\\epsilon(?![a-zA-Z])|\\varepsilon(?![a-zA-Z])"""), "ε")
    s = s.replace(Regex("""\\zeta(?![a-zA-Z])"""), "ζ")
    s = s.replace(Regex("""\\eta(?![a-zA-Z])"""), "η")
    s = s.replace(Regex("""\\theta(?![a-zA-Z])|\\vartheta(?![a-zA-Z])"""), "θ")
    s = s.replace(Regex("""\\iota(?![a-zA-Z])"""), "ι")
    s = s.replace(Regex("""\\kappa(?![a-zA-Z])"""), "κ")
    s = s.replace(Regex("""\\lambda(?![a-zA-Z])"""), "λ")
    s = s.replace(Regex("""\\mu(?![a-zA-Z])"""), "μ")
    s = s.replace(Regex("""\\nu(?![a-zA-Z])"""), "ν")
    s = s.replace(Regex("""\\xi(?![a-zA-Z])"""), "ξ")
    s = s.replace(Regex("""\\pi(?![a-zA-Z])"""), "π")
    s = s.replace(Regex("""\\rho(?![a-zA-Z])"""), "ρ")
    s = s.replace(Regex("""\\sigma(?![a-zA-Z])"""), "σ")
    s = s.replace(Regex("""\\tau(?![a-zA-Z])"""), "τ")
    s = s.replace(Regex("""\\phi(?![a-zA-Z])|\\varphi(?![a-zA-Z])"""), "φ")
    s = s.replace(Regex("""\\chi(?![a-zA-Z])"""), "χ")
    s = s.replace(Regex("""\\psi(?![a-zA-Z])"""), "ψ")
    s = s.replace(Regex("""\\omega(?![a-zA-Z])"""), "ω")

    // Blackboard bold (Sets & Probability)
    s = s.replace(Regex("""\\mathbb\{E\}"""), "𝔼")
    s = s.replace(Regex("""\\mathbb\{R\}"""), "ℝ")
    s = s.replace(Regex("""\\mathbb\{N\}"""), "ℕ")
    s = s.replace(Regex("""\\mathbb\{Z\}"""), "ℤ")
    s = s.replace(Regex("""\\mathbb\{C\}"""), "ℂ")
    s = s.replace(Regex("""\\mathbb\{Q\}"""), "ℚ")
    s = s.replace(Regex("""\\mathbb\{P\}"""), "ℙ")
    s = s.replace(Regex("""\\mathbb\{([A-Za-z])\}"""), "$1")

    // Font styles
    s = s.replace(Regex("""\\(?:mathbf|mathit|mathrm|text|textbf|textit|texttt|boldsymbol)\{([^{}]+)\}"""), "$1")

    // Mathematical operators & symbols
    s = s.replace(Regex("""\\sum(?![a-zA-Z])"""), "∑")
    s = s.replace(Regex("""\\prod(?![a-zA-Z])"""), "∏")
    s = s.replace(Regex("""\\iint(?![a-zA-Z])"""), "∬")
    s = s.replace(Regex("""\\iiint(?![a-zA-Z])"""), "∭")
    s = s.replace(Regex("""\\oint(?![a-zA-Z])"""), "∮")
    s = s.replace(Regex("""\\int(?![a-zA-Z])"""), "∫")
    s = s.replace(Regex("""\\partial(?![a-zA-Z])"""), "∂")
    s = s.replace(Regex("""\\nabla(?![a-zA-Z])"""), "∇")
    s = s.replace(Regex("""\\cdot(?![a-zA-Z])"""), "·")
    s = s.replace(Regex("""\\times(?![a-zA-Z])"""), "×")
    s = s.replace(Regex("""\\pm(?![a-zA-Z])"""), "±")
    s = s.replace(Regex("""\\mp(?![a-zA-Z])"""), "∓")
    s = s.replace(Regex("""\\infty(?![a-zA-Z])"""), "∞")
    s = s.replace(Regex("""\\approx(?![a-zA-Z])"""), "≈")
    s = s.replace(Regex("""\\neq(?![a-zA-Z])"""), "≠")
    s = s.replace(Regex("""\\leq?(?![a-zA-Z])"""), "≤")
    s = s.replace(Regex("""\\geq?(?![a-zA-Z])"""), "≥")
    s = s.replace(Regex("""\\ll(?![a-zA-Z])"""), "≪")
    s = s.replace(Regex("""\\gg(?![a-zA-Z])"""), "≫")
    s = s.replace(Regex("""\\in(?![a-zA-Z])"""), "∈")
    s = s.replace(Regex("""\\notin(?![a-zA-Z])"""), "∉")
    s = s.replace(Regex("""\\subset(?![a-zA-Z])"""), "⊂")
    s = s.replace(Regex("""\\subseteq(?![a-zA-Z])"""), "⊆")
    s = s.replace(Regex("""\\cup(?![a-zA-Z])"""), "∪")
    s = s.replace(Regex("""\\cap(?![a-zA-Z])"""), "∩")
    s = s.replace(Regex("""\\forall(?![a-zA-Z])"""), "∀")
    s = s.replace(Regex("""\\exists(?![a-zA-Z])"""), "∃")
    s = s.replace(Regex("""\\nexists(?![a-zA-Z])"""), "∄")
    s = s.replace(Regex("""\\to(?![a-zA-Z])|\\rightarrow(?![a-zA-Z])"""), "→")
    s = s.replace(Regex("""\\leftarrow(?![a-zA-Z])"""), "←")
    s = s.replace(Regex("""\\Rightarrow(?![a-zA-Z])"""), "⇒")
    s = s.replace(Regex("""\\iff(?![a-zA-Z])|\\Leftrightarrow(?![a-zA-Z])"""), "⇔")
    s = s.replace(Regex("""\\mapsto(?![a-zA-Z])"""), "↦")
    s = s.replace(Regex("""\\circ(?![a-zA-Z])"""), "∘")
    s = s.replace(Regex("""\\dots(?![a-zA-Z])|\\cdots(?![a-zA-Z])|\\ldots(?![a-zA-Z])"""), "…")
    s = s.replace(Regex("""\\prime(?![a-zA-Z])"""), "′")

    // Simple fractions: \frac{a}{b} -> (a / b)
    s = s.replace(Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}"""), "($1 / $2)")

    // Roots: \sqrt[n]{x} -> ⁿ√(x), \sqrt{x} -> √(x) or √x
    s = s.replace(Regex("""\\sqrt\[([^{}]+)\]\{([^{}]+)\}""")) { match ->
        "${toSuperscript(match.groupValues[1])}√(${match.groupValues[2]})"
    }
    s = s.replace(Regex("""\\sqrt\{([^{}]+)\}""")) { match ->
        val inner = match.groupValues[1].trim()
        if (inner.length == 1) "√$inner" else "√($inner)"
    }

    // Integral bounds cleanup: \int_{-\infty}^{\infty} -> ∫[-∞, ∞]
    s = s.replace(Regex("""∫\s*_\{?(-?∞|[^^\s{}]+)\}?\s*\^\{?(-?∞|[^^\s{}]+)\}?""")) { match ->
        val lower = match.groupValues[1]
        val upper = match.groupValues[2]
        "∫[$lower, $upper] "
    }

    // Superscripts: inner carets e.g. x^2 -> x²
    s = s.replace(Regex("""\^([0-9a-zA-Z+\-=()])""")) { match ->
        toSuperscript(match.groupValues[1])
    }
    s = s.replace(Regex("""\^\{([^{}]+)\}""")) { match ->
        toSuperscript(match.groupValues[1])
    }

    // Subscripts: x_i -> xᵢ, _{i=1} -> ᵢ₌₁
    s = s.replace(Regex("""_([0-9a-zA-Z+\-=()])""")) { match ->
        toSubscript(match.groupValues[1])
    }
    s = s.replace(Regex("""_\{([^{}]+)\}""")) { match ->
        toSubscript(match.groupValues[1])
    }

    // Clean up residual carets/underscores if any were left unmapped
    s = s.replace(Regex("""\^\{([^{}]+)\}"""), "^$1")
    s = s.replace(Regex("""_\{([^{}]+)\}"""), "_$1")

    // Normalize multiple consecutive spaces
    s = s.replace(Regex("""[ \t]{3,}"""), "  ")

    return s
}
