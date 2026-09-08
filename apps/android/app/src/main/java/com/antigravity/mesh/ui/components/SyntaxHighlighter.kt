package com.antigravity.mesh.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * VS Code / Modern Dark syntax theme palette.
 */
object SyntaxPalette {
    val Keyword = Color(0xFFA78BFA)       // Violet / Purple (bold)
    val Function = Color(0xFF38BDF8)      // Sky Blue (methods and function calls)
    val Type = Color(0xFFFBBF24)          // Warm Amber (Classes, Interfaces, Structs, Primitives)
    val String = Color(0xFF4ADE80)        // Emerald Green (String and char literals)
    val Number = Color(0xFFFB923C)        // Warm Orange (Integers, hex, floats)
    val Comment = Color(0xFF64748B)       // Muted Slate (Italic)
    val Annotation = Color(0xFFF472B6)    // Rose Pink (@Annotation, decorators)
    val Constant = Color(0xFFF43F5E)      // Rose Red (true, false, null, None, nil)
    val Property = Color(0xFF67E8F9)      // Light Cyan (JSON keys, object properties)
    val Tag = Color(0xFFF87171)           // Coral Red (<tag>, </tag>)
    val Attribute = Color(0xFFA5B4FC)     // Indigo / Lavender (HTML/XML attributes)
    val Variable = Color(0xFF2DD4BF)      // Teal ($var, ${var})
    val Operator = Color(0xFF94A3B8)      // Slate (->, =>, pipes, arrows)
    val Plain = Color(0xFFE2E8F0)         // Off-White Default Code
}

/**
 * Token rule with a target style and a precompiled regex.
 * If [group] is specified (> 0), only that capturing group is styled.
 */
data class TokenRule(
    val regex: Regex,
    val style: SpanStyle,
    val group: Int = 0
)

/**
 * Per-language grammar definition mimicking the Prism.js tokenizer architecture.
 */
data class LanguageGrammar(
    val rules: List<TokenRule>
)

/**
 * High-performance, zero-dependency Prism-grade syntax highlighter for Android Jetpack Compose.
 */
object SyntaxHighlighter {

    // ─── Common Regex Patterns ──────────────────────────────────────────────
    private val SlashComment = Regex("""//.*$""", RegexOption.MULTILINE)
    private val BlockComment = Regex("""/\*[\s\S]*?\*/""")
    private val HashComment = Regex("""#.*$""", RegexOption.MULTILINE)
    private val SqlLineComment = Regex("""--.*$""", RegexOption.MULTILINE)
    private val MermaidComment = Regex("""%%.*$""", RegexOption.MULTILINE)
    private val HtmlComment = Regex("""<!--[\s\S]*?-->""")

    private val StandardString = Regex(""""([^"\\]|\\.)*"|'([^'\\]|\\.)*'""")
    private val BacktickString = Regex("""`([^`\\]|\\.)*`""")
    private val TripleDoubleString = Regex("\"\"\"[\\s\\S]*?\"\"\"")
    private val TripleSingleString = Regex("'''[\\s\\S]*?'''")

    private val StandardNumber = Regex("""\b(0x[0-9a-fA-F]+|0b[01]+|\d+(\.\d+)?([eE][+-]?\d+)?)\b""")
    private val StandardBooleanNull = Regex("""\b(true|false|null|nil|None|True|False|undefined|NaN)\b""")
    private val GenericFunctionCall = Regex("""\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\s*\()""")
    private val GenericPascalType = Regex("""\b[A-Z][a-zA-Z0-9_]*\b""")

    // ─── Language-specific Grammars ─────────────────────────────────────────

    private val KotlinJavaGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(TripleDoubleString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""@[A-Za-z0-9_]+"""), SpanStyle(color = SyntaxPalette.Annotation, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(package|import|class|interface|object|enum|fun|val|var|data|sealed|abstract|open|override|final|private|protected|public|internal|companion|inline|noinline|crossinline|reified|tailrec|operator|infix|suspend|coroutine|by|lazy|lateinit|it|this|super|return|if|else|when|for|while|do|break|continue|try|catch|finally|throw|throws|as|is|in|out|typealias|constructor|init|get|set|field|const|actual|expect|native|synchronized|volatile|transient|extends|implements|new|instanceof|default|switch|case|static|void|strictfp)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(StandardBooleanNull, SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(Boolean|Byte|Short|Int|Long|Float|Double|Char|String|Unit|Nothing|Any|Array|List|Map|Set|Flow|StateFlow|SharedFlow|LiveData|CoroutineScope|Job|Deferred|ViewModel|Modifier|Composable)\b"""),
                    SpanStyle(color = SyntaxPalette.Type)
                ),
                TokenRule(GenericFunctionCall, SpanStyle(color = SyntaxPalette.Function)),
                TokenRule(GenericPascalType, SpanStyle(color = SyntaxPalette.Type)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val RustGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(Regex("""r(#*)"[\s\S]*?"\1"""), SpanStyle(color = SyntaxPalette.String)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""#!?\[[\s\S]*?\]"""), SpanStyle(color = SyntaxPalette.Annotation, fontWeight = FontWeight.SemiBold)),
                TokenRule(Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*!(?=\s*[\(\{\[])"""), SpanStyle(color = SyntaxPalette.Function, fontWeight = FontWeight.Bold)),
                TokenRule(Regex("""'[a-zA-Z_][a-zA-Z0-9_]*\b"""), SpanStyle(color = SyntaxPalette.Variable)),
                TokenRule(
                    Regex("""\b(fn|let|mut|pub|crate|use|mod|struct|enum|trait|impl|type|where|for|in|while|loop|if|else|match|return|break|continue|yield|async|await|move|ref|unsafe|dyn|extern|static|const|as|self|Self|super)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(Regex("""\b(true|false|Some|None|Ok|Err)\b"""), SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize|f32|f64|bool|char|str|String|Vec|Option|Result|Box|Rc|Arc|Cell|RefCell|Mutex|RwLock|HashMap|HashSet|Pin|Poll|Future)\b"""),
                    SpanStyle(color = SyntaxPalette.Type)
                ),
                TokenRule(GenericFunctionCall, SpanStyle(color = SyntaxPalette.Function)),
                TokenRule(GenericPascalType, SpanStyle(color = SyntaxPalette.Type)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val PythonGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(TripleDoubleString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(TripleSingleString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(HashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(Regex("""[rfbRFB]?"([^"\\]|\\.)*"|[rfbRFB]?'([^'\\]|\\.)*'"""), SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""@[A-Za-z0-9_.]+"""), SpanStyle(color = SyntaxPalette.Annotation, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(def|class|lambda|with|as|if|elif|else|for|while|try|except|finally|raise|return|yield|from|import|in|is|not|and|or|pass|break|continue|global|nonlocal|del|assert|async|await)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(Regex("""\b(True|False|None|self|cls)\b"""), SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(print|len|range|enumerate|zip|map|filter|int|str|float|bool|list|dict|set|tuple|bytes|bytearray|super|isinstance|issubclass|type|id|hash|repr|dir|help|open|any|all|min|max|sum|abs|round)\b"""),
                    SpanStyle(color = SyntaxPalette.Function)
                ),
                TokenRule(GenericFunctionCall, SpanStyle(color = SyntaxPalette.Function)),
                TokenRule(GenericPascalType, SpanStyle(color = SyntaxPalette.Type)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val JavaScriptTypeScriptGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BacktickString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""@[A-Za-z0-9_]+"""), SpanStyle(color = SyntaxPalette.Annotation, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(function|const|let|var|class|interface|type|enum|namespace|declare|module|export|import|from|as|default|return|if|else|switch|case|for|while|do|break|continue|try|catch|finally|throw|new|typeof|instanceof|in|of|void|delete|await|async|yield|this|super|extends|implements|abstract|readonly|keyof|never|any|unknown|debugger)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(Regex("""\b(true|false|null|undefined|NaN|Infinity)\b"""), SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(string|number|boolean|symbol|bigint|object|Array|Promise|Record|Partial|Required|Readonly|Pick|Omit|Exclude|Extract)\b"""),
                    SpanStyle(color = SyntaxPalette.Type)
                ),
                TokenRule(GenericFunctionCall, SpanStyle(color = SyntaxPalette.Function)),
                TokenRule(GenericPascalType, SpanStyle(color = SyntaxPalette.Type)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val GoGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BacktickString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(
                    Regex("""\b(func|package|import|var|const|type|struct|interface|map|chan|go|defer|select|case|default|if|else|switch|for|range|break|continue|return|fallthrough|goto)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(Regex("""\b(true|false|nil|iota)\b"""), SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(make|new|len|cap|append|copy|close|delete|panic|recover|print|println)\b"""),
                    SpanStyle(color = SyntaxPalette.Function)
                ),
                TokenRule(
                    Regex("""\b(string|bool|byte|rune|int|int8|int16|int32|int64|uint|uint8|uint16|uint32|uint64|uintptr|float32|float64|complex64|complex128|error|any)\b"""),
                    SpanStyle(color = SyntaxPalette.Type)
                ),
                TokenRule(GenericFunctionCall, SpanStyle(color = SyntaxPalette.Function)),
                TokenRule(GenericPascalType, SpanStyle(color = SyntaxPalette.Type)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val SqlGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SqlLineComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(
                    Regex(
                        """\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|JOIN|INNER|LEFT|RIGHT|FULL|OUTER|CROSS|ON|GROUP|BY|ORDER|ASC|DESC|HAVING|LIMIT|OFFSET|UNION|ALL|CREATE|TABLE|DATABASE|SCHEMA|VIEW|INDEX|DROP|ALTER|ADD|CONSTRAINT|PRIMARY|KEY|FOREIGN|REFERENCES|CHECK|UNIQUE|DEFAULT|NOT|NULL|IS|LIKE|ILIKE|IN|BETWEEN|AND|OR|AS|DISTINCT|CASE|WHEN|THEN|ELSE|END|EXISTS|WITH|RECURSIVE|TRANSACTION|COMMIT|ROLLBACK|PRAGMA)\b""",
                        RegexOption.IGNORE_CASE
                    ),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(
                    Regex("""\b(TRUE|FALSE|NULL)\b""", RegexOption.IGNORE_CASE),
                    SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)
                ),
                TokenRule(
                    Regex(
                        """\b(COUNT|SUM|AVG|MIN|MAX|COALESCE|NOW|DATE|CONCAT|SUBSTRING|ROUND|CAST|LOWER|UPPER|LENGTH|TRIM)\b""",
                        RegexOption.IGNORE_CASE
                    ),
                    SpanStyle(color = SyntaxPalette.Function)
                ),
                TokenRule(
                    Regex(
                        """\b(INT|INTEGER|BIGINT|SMALLINT|VARCHAR|CHAR|TEXT|BOOLEAN|DATE|TIME|TIMESTAMP|NUMERIC|DECIMAL|FLOAT|REAL|BLOB|JSON|JSONB|SERIAL)\b""",
                        RegexOption.IGNORE_CASE
                    ),
                    SpanStyle(color = SyntaxPalette.Type)
                ),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val HtmlXmlGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(HtmlComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""<!DOCTYPE[\s\S]*?>""", RegexOption.IGNORE_CASE), SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)),
                TokenRule(Regex("""</?[a-zA-Z0-9:-]+"""), SpanStyle(color = SyntaxPalette.Tag, fontWeight = FontWeight.Bold)),
                TokenRule(Regex("""/?>"""), SpanStyle(color = SyntaxPalette.Tag, fontWeight = FontWeight.Bold)),
                TokenRule(Regex("""\b[a-zA-Z0-9_:-]+(?=\=)"""), SpanStyle(color = SyntaxPalette.Attribute)),
                TokenRule(Regex("""&[a-zA-Z0-9#]+;"""), SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val CssGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""@[a-zA-Z0-9_-]+"""), SpanStyle(color = SyntaxPalette.Annotation, fontWeight = FontWeight.Bold)),
                TokenRule(Regex("""#[0-9a-fA-F]{3,8}\b"""), SpanStyle(color = SyntaxPalette.Number)),
                TokenRule(Regex("""\b\d+(\.\d+)?(px|rem|em|%|vh|vw|s|ms|deg|fr|pt)\b"""), SpanStyle(color = SyntaxPalette.Number)),
                TokenRule(Regex("""[a-zA-Z0-9_-]+(?=\s*:)"""), SpanStyle(color = SyntaxPalette.Property)),
                TokenRule(Regex(""":[a-zA-Z0-9_-]+"""), SpanStyle(color = SyntaxPalette.Function))
            )
        )
    }

    private val JsonGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(Regex(""""([^"\\]|\\.)*"(?=\s*:)"""), SpanStyle(color = SyntaxPalette.Property, fontWeight = FontWeight.SemiBold)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number)),
                TokenRule(Regex("""\b(true|false|null)\b"""), SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold))
            )
        )
    }

    private val BashGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(HashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""\$[a-zA-Z_][a-zA-Z0-9_]*|\$\{[^}]+\}|\$[0-9?*#@]"""), SpanStyle(color = SyntaxPalette.Variable)),
                TokenRule(Regex("""(?<=\s)-{1,2}[a-zA-Z0-9_-]+"""), SpanStyle(color = SyntaxPalette.Attribute)),
                TokenRule(
                    Regex("""\b(if|then|else|elif|fi|for|in|while|until|do|done|case|esac|function|select|time|source|alias|export|local|unset|readonly|shift|return|exit|sudo|echo|printf|cd|mkdir|rm|cp|mv|cat|grep|sed|awk|curl|wget)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(Regex("""(\|{1,2}|&&|>>?|<<?)"""), SpanStyle(color = SyntaxPalette.Operator, fontWeight = FontWeight.Bold)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val CFamilyGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(Regex("""#\s*(include|define|undef|if|ifdef|ifndef|elif|else|endif|pragma|error|warning).*$""", RegexOption.MULTILINE), SpanStyle(color = SyntaxPalette.Annotation, fontWeight = FontWeight.SemiBold)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(
                    Regex("""\b(auto|break|case|char|const|continue|default|do|double|else|enum|extern|float|for|goto|if|int|long|register|return|short|signed|sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while|class|namespace|using|public|private|protected|virtual|override|friend|inline|template|typename|explicit|constexpr|nullptr|new|delete|try|catch|throw|this|operator|static_cast|dynamic_cast|reinterpret_cast|const_cast)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(Regex("""\b(true|false|nullptr|NULL)\b"""), SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(string|vector|map|set|pair|unique_ptr|shared_ptr|size_t|uint32_t|int32_t|uint64_t|int64_t|bool)\b"""),
                    SpanStyle(color = SyntaxPalette.Type)
                ),
                TokenRule(GenericFunctionCall, SpanStyle(color = SyntaxPalette.Function)),
                TokenRule(GenericPascalType, SpanStyle(color = SyntaxPalette.Type)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val SwiftGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""@[A-Za-z0-9_]+"""), SpanStyle(color = SyntaxPalette.Annotation, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(func|let|var|class|struct|enum|protocol|extension|init|deinit|subscript|typealias|associatedtype|actor|public|private|fileprivate|internal|open|mutating|nonmutating|override|final|static|lazy|weak|unowned|if|else|guard|switch|case|default|for|while|repeat|break|continue|fallthrough|return|throw|throws|rethrows|try|catch|defer|async|await|some|any|self|Self|super|in|where|as|is)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(Regex("""\b(true|false|nil)\b"""), SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(String|Int|Double|Float|Bool|Array|Dictionary|Set|Optional|Result|Task)\b"""),
                    SpanStyle(color = SyntaxPalette.Type)
                ),
                TokenRule(GenericFunctionCall, SpanStyle(color = SyntaxPalette.Function)),
                TokenRule(GenericPascalType, SpanStyle(color = SyntaxPalette.Type)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val MermaidGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(MermaidComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(
                    Regex("""\b(sequenceDiagram|flowchart|graph|subgraph|end|participant|actor|autonumber|note|over|loop|alt|opt|par|critical|break|rect|activate|deactivate|classDiagram|stateDiagram|erDiagram)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(
                    Regex("""(-->|->>|->|--|\=\=>|-\.->|x--|--x|o--|--o|\|)"""),
                    SpanStyle(color = SyntaxPalette.Operator, fontWeight = FontWeight.Bold)
                ),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    private val GenericGrammar by lazy {
        LanguageGrammar(
            listOf(
                TokenRule(SlashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(BlockComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(HashComment, SpanStyle(color = SyntaxPalette.Comment, fontStyle = FontStyle.Italic)),
                TokenRule(StandardString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(BacktickString, SpanStyle(color = SyntaxPalette.String)),
                TokenRule(Regex("""@[A-Za-z0-9_]+"""), SpanStyle(color = SyntaxPalette.Annotation, fontWeight = FontWeight.SemiBold)),
                TokenRule(
                    Regex("""\b(val|var|fun|fn|def|function|func|class|struct|enum|interface|trait|impl|type|object|package|import|from|export|default|return|if|elif|else|when|switch|case|for|while|loop|do|in|as|is|match|break|continue|yield|async|await|try|catch|finally|throw|raise|override|suspend|private|public|protected|internal|mut|pub|use|mod|let|const|static|extern|where|self|this|super|new|typeof|instanceof|select|where|insert|update|delete|join|group|order|limit|echo|set|exit)\b"""),
                    SpanStyle(color = SyntaxPalette.Keyword, fontWeight = FontWeight.Bold)
                ),
                TokenRule(StandardBooleanNull, SpanStyle(color = SyntaxPalette.Constant, fontWeight = FontWeight.SemiBold)),
                TokenRule(GenericFunctionCall, SpanStyle(color = SyntaxPalette.Function)),
                TokenRule(GenericPascalType, SpanStyle(color = SyntaxPalette.Type)),
                TokenRule(StandardNumber, SpanStyle(color = SyntaxPalette.Number))
            )
        )
    }

    /**
     * Resolves the appropriate grammar for a given language tag or file extension.
     */
    fun getGrammar(language: String?): LanguageGrammar {
        val normalized = language?.lowercase()?.trim() ?: ""
        return when (normalized) {
            "kotlin", "kt", "kts", "java" -> KotlinJavaGrammar
            "rust", "rs" -> RustGrammar
            "python", "py", "python3" -> PythonGrammar
            "javascript", "js", "mjs", "cjs", "jsx",
            "typescript", "ts", "tsx" -> JavaScriptTypeScriptGrammar
            "go", "golang" -> GoGrammar
            "sql" -> SqlGrammar
            "html", "htm", "xml", "svg", "plist" -> HtmlXmlGrammar
            "css", "scss", "sass", "less" -> CssGrammar
            "json", "jsonc" -> JsonGrammar
            "sh", "bash", "zsh", "shell", "fish" -> BashGrammar
            "c", "h", "cpp", "hpp", "cc", "cxx", "cs", "csharp" -> CFamilyGrammar
            "swift" -> SwiftGrammar
            "mermaid", "mmd" -> MermaidGrammar
            else -> GenericGrammar
        }
    }

    /**
     * Highlights code using prioritized non-overlapping tokenization.
     */
    fun highlight(code: String, language: String? = null): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        val len = code.length
        if (len == 0) return builder.toAnnotatedString()

        // Base text color
        builder.addStyle(SpanStyle(color = SyntaxPalette.Plain), 0, len)
        val occupied = BooleanArray(len)
        val grammar = getGrammar(language)

        for (rule in grammar.rules) {
            for (match in rule.regex.findAll(code)) {
                val targetGroup = if (rule.group > 0 && rule.group <= match.groups.size) {
                    match.groups[rule.group]
                } else {
                    match.groups[0]
                } ?: continue

                val range = targetGroup.range
                val start = range.first
                val end = range.last + 1

                if (start < len && !occupied[start]) {
                    var canApply = true
                    for (idx in start until end) {
                        if (idx < len && occupied[idx]) {
                            canApply = false
                            break
                        }
                    }
                    if (canApply) {
                        builder.addStyle(rule.style, start, end)
                        for (idx in start until end) {
                            if (idx < len) occupied[idx] = true
                        }
                    }
                }
            }
        }

        return builder.toAnnotatedString()
    }
}

/**
 * Top-level convenience function matching the previous API signature,
 * now powered by the Prism-grade SyntaxHighlighter engine.
 */
fun highlightCode(code: String, language: String? = null): AnnotatedString {
    return SyntaxHighlighter.highlight(code, language)
}
