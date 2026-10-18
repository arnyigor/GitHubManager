package dev.arny.githubmanager.ui.markdown

/** Minimal tag-less tokenizer for fenced code inside Markdown; not a full parser. */
enum class CodeTokenKind { KEYWORD, STRING, COMMENT }

data class CodeToken(val range: IntRange, val kind: CodeTokenKind)

private val KEYWORDS = mapOf(
    "kt" to "fun|val|var|class|interface|object|data|sealed|enum|private|internal|protected|public|override|" +
        "return|if|else|when|for|while|suspend|import|package|true|false|null|this|super|companion|const",
    "kts" to "fun|val|var|class|object|plugins|repositories|dependencies|implementation|apply|id|version",
    "java" to "class|interface|enum|extends|implements|public|private|protected|static|final|void|return|" +
        "if|else|for|while|new|import|package|true|false|null|this|super",
    "py" to "def|class|import|from|return|if|elif|else|async|await|True|False|None|for|while|in|not|and|or|lambda",
    "js" to "const|let|var|function|class|return|if|else|async|await|export|import|true|false|null|undefined",
    "ts" to "const|let|var|function|class|interface|type|return|if|else|async|await|export|import|true|false|null",
    "json" to "true|false|null",
    "yml" to "true|false|null",
    "yaml" to "true|false|null",
    "sh" to "if|then|else|fi|for|in|do|done|echo|export|cd|set",
    "bash" to "if|then|else|fi|for|in|do|done|echo|export|cd|set",
    "toml" to "true|false"
)

private const val COMMENT_LINE = "(?m)(^|\\s)(//[^\\n]*|#[^\\n]*)"
private const val STRING_LITERAL = "\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'"

/**
 * Comments and strings win over keywords, so `// fun` is not painted as code.
 * Ranges are absolute offsets inside [code].
 */
fun codeTokens(code: String, language: String): List<CodeToken> {
    if (code.isEmpty()) return emptyList()
    val taken = ArrayList<IntRange>()
    val tokens = ArrayList<CodeToken>()

    fun add(range: IntRange, kind: CodeTokenKind) {
        if (range.isEmpty()) return
        if (taken.any { it.first <= range.last && range.first <= it.last }) return
        taken.add(range)
        tokens.add(CodeToken(range, kind))
    }

    Regex(COMMENT_LINE).findAll(code).forEach { match ->
        val group = match.groups[2] ?: return@forEach
        add(group.range, CodeTokenKind.COMMENT)
    }
    Regex(STRING_LITERAL).findAll(code).forEach { add(it.range, CodeTokenKind.STRING) }

    val words = KEYWORDS[language.lowercase()] ?: return tokens.sortedBy { it.range.first }
    Regex("\\b(?:$words)\\b").findAll(code).forEach { add(it.range, CodeTokenKind.KEYWORD) }
    return tokens.sortedBy { it.range.first }
}
