package com.antigravity.mesh.ui

import com.antigravity.mesh.ui.components.SyntaxHighlighter
import com.antigravity.mesh.ui.components.SyntaxPalette
import com.antigravity.mesh.ui.components.highlightCode
import org.junit.Assert.*
import org.junit.Test

class SyntaxHighlighterTest {

    @Test
    fun testPythonSyntax() {
        val pyCode = """
            # A python test comment
            @dataclass
            def calculate_total(items, count=10):
                ${"\"\"\""}Docstring description${"\"\"\""}
                with open("file.txt") as f:
                    pass
                return None
        """.trimIndent()

        val highlighted = highlightCode(pyCode, "python")
        assertNotNull(highlighted)
        assertEquals(pyCode, highlighted.text)

        // Check that styles were applied
        val spans = highlighted.spanStyles
        assertTrue("Expected multiple styles to be applied to Python code", spans.isNotEmpty())

        // Check that the comment is styled with SyntaxPalette.Comment
        val commentSpan = spans.find {
            it.end <= "# A python test comment".length && it.item.color == SyntaxPalette.Comment
        }
        assertNotNull("Expected comment span to be found", commentSpan)
        assertEquals(SyntaxPalette.Comment, commentSpan?.item?.color)
    }

    @Test
    fun testRustSyntax() {
        val rsCode = """
            // Rust test
            pub unsafe fn init_device(id: u32) -> Option<String> {
                println!("Device: {}", id);
                let mut x = 42;
                Some("ready".to_string())
            }
        """.trimIndent()

        val highlighted = highlightCode(rsCode, "rust")
        assertNotNull(highlighted)
        assertEquals(rsCode, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testGoSyntax() {
        val goCode = """
            package main
            func processQueue(ch chan int) {
                defer close(ch)
                items := make([]int, 0)
                go worker()
            }
        """.trimIndent()

        val highlighted = highlightCode(goCode, "go")
        assertNotNull(highlighted)
        assertEquals(goCode, highlighted.text)

        val spans = highlighted.spanStyles
        assertTrue(spans.isNotEmpty())
    }

    @Test
    fun testSqlCaseInsensitive() {
        val sqlCode = """
            -- SQL query
            SELECT id, name, COUNT(*) 
            FROM users 
            WHERE active = TRUE AND created_at > '2026-01-01'
            GROUP BY id, name
            ORDER BY id DESC;
        """.trimIndent()

        val highlighted = highlightCode(sqlCode, "sql")
        assertNotNull(highlighted)
        assertEquals(sqlCode, highlighted.text)

        // Find SELECT keyword style
        val selectSpan = highlighted.spanStyles.find {
            val text = sqlCode.substring(it.start, it.end)
            text == "SELECT"
        }
        assertNotNull("SELECT keyword should be styled", selectSpan)
        assertEquals(SyntaxPalette.Keyword, selectSpan?.item?.color)
    }

    @Test
    fun testHtmlSyntax() {
        val htmlCode = """<div class="card" id="main"><span title="info">Hello</span></div>"""
        val highlighted = highlightCode(htmlCode, "html")
        assertNotNull(highlighted)
        assertEquals(htmlCode, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())

        // Find tag style
        val tagSpan = highlighted.spanStyles.find {
            val text = htmlCode.substring(it.start, it.end)
            text == "<div"
        }
        assertNotNull("Tag <div should be styled", tagSpan)
        assertEquals(SyntaxPalette.Tag, tagSpan?.item?.color)
    }

    @Test
    fun testJsonSyntax() {
        val jsonCode = """
            {
                "status": "success",
                "count": 42,
                "active": true
            }
        """.trimIndent()

        val highlighted = highlightCode(jsonCode, "json")
        assertNotNull(highlighted)
        assertEquals(jsonCode, highlighted.text)

        // "status": should be styled as property
        val propSpan = highlighted.spanStyles.find {
            val text = jsonCode.substring(it.start, it.end)
            text.contains("status") && it.item.color == SyntaxPalette.Property
        }
        assertNotNull("JSON key should be styled as property", propSpan)
        assertEquals(SyntaxPalette.Property, propSpan?.item?.color)
    }

    @Test
    fun testStringLiteralCollisionSafety() {
        // String literal contains keywords: "while (true) do return"
        val code = """val message = "while true return";"""
        val highlighted = highlightCode(code, "kotlin")

        // The string literal should be styled as String, not subdivided by while/true/return
        val stringSpan = highlighted.spanStyles.find {
            val text = code.substring(it.start, it.end)
            text == "\"while true return\""
        }
        assertNotNull("Full string literal should be styled once", stringSpan)
        assertEquals(SyntaxPalette.String, stringSpan?.item?.color)
    }
}
