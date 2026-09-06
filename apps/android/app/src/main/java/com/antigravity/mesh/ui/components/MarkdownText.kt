package com.antigravity.mesh.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mesh.ui.theme.*

@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    val lines = markdown.split("\n")
    var inCodeBlock = false
    var currentLanguage: String? = null
    val currentCodeBlock = StringBuilder()
    val currentTableLines = mutableListOf<String>()

    fun isTableLine(l: String): Boolean {
        val t = l.trim()
        return t.startsWith("|") || (t.count { it == '|' } >= 2)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in lines) {
            val trimmed = line.trimEnd()

            if (trimmed.startsWith("```")) {
                if (currentTableLines.isNotEmpty()) {
                    MarkdownTable(lines = currentTableLines.toList(), onLinkClick = onLinkClick)
                    currentTableLines.clear()
                }
                if (inCodeBlock) {
                    CodeBlock(code = currentCodeBlock.toString().trimEnd(), language = currentLanguage)
                    currentCodeBlock.clear()
                    currentLanguage = null
                    inCodeBlock = false
                } else {
                    currentLanguage = trimmed.removePrefix("```").trim().ifBlank { null }
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                if (currentCodeBlock.isNotEmpty()) currentCodeBlock.append("\n")
                currentCodeBlock.append(line)
                continue
            }

            if (isTableLine(trimmed)) {
                currentTableLines.add(trimmed)
                continue
            } else if (currentTableLines.isNotEmpty()) {
                MarkdownTable(lines = currentTableLines.toList(), onLinkClick = onLinkClick)
                currentTableLines.clear()
            }

            if (trimmed.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                continue
            }

            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# "), onLinkClick),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## "), onLinkClick),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### "), onLinkClick),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 4.dp)) {
                        Text(text = "• ", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                        Text(
                            text = parseInlineMarkdown(trimmed.substring(2), onLinkClick),
                            fontSize = 14.sp,
                            color = textColor
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed, onLinkClick),
                        fontSize = 14.sp,
                        color = textColor,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        if (currentTableLines.isNotEmpty()) {
            MarkdownTable(lines = currentTableLines.toList(), onLinkClick = onLinkClick)
        }

        if (inCodeBlock && currentCodeBlock.isNotEmpty()) {
            CodeBlock(code = currentCodeBlock.toString().trimEnd(), language = currentLanguage)
        }
    }
}

@Composable
private fun MarkdownTable(
    lines: List<String>,
    onLinkClick: ((String) -> Unit)? = null
) {
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
                    cells.forEach { cellText ->
                        Text(
                            text = parseInlineMarkdown(cellText, onLinkClick),
                            fontSize = 12.sp,
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHeader) AccentCyan else TextPrimary,
                            modifier = Modifier.widthIn(min = 80.dp, max = 220.dp)
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

@Composable
private fun CodeBlock(code: String, language: String? = null) {
    val scrollState = rememberScrollState()
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
                text = code,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = AccentCyan,
                lineHeight = 16.sp
            )
        }
    }
}

private fun parseInlineMarkdown(
    text: String,
    onLinkClick: ((String) -> Unit)? = null
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            // Markdown Link: [label](target)
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

            // Bare URL auto-link: http:// or https://
            if (text.startsWith("http://", i, ignoreCase = true) || text.startsWith("https://", i, ignoreCase = true)) {
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

            // Bold (**text**)
            if (i + 1 < len && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // Inline Code (`code`)
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = SurfaceVariantDark,
                            color = AccentCyan
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            // Italic (*text*)
            if (text[i] == '*') {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            append(text[i])
            i++
        }
    }
}
