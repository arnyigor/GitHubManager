package dev.arny.githubmanager.ui.code

/** A bounded code viewport: no entire 200 KB file is composed as one gigantic text node. */
data class CodePage(val firstLine: Int, val lines: List<String>) {
    val lastLine: Int get() = firstLine + lines.size - 1
    val text: String get() = lines.joinToString("\n")
}

fun codePages(code: String, maxLines: Int = 200, maxChars: Int = 24_000): List<CodePage> {
    require(maxLines > 0 && maxChars > 0)
    val lines = code.split('\n')
    val pages = ArrayList<CodePage>()
    var batch = ArrayList<String>()
    var batchSize = 0
    var startLine = 1
    lines.forEachIndexed { index, line ->
        if (batch.isNotEmpty() && (batch.size >= maxLines || batchSize + line.length + 1 > maxChars)) {
            pages.add(CodePage(startLine, batch))
            batch = ArrayList()
            batchSize = 0
            startLine = index + 1
        }
        batch.add(line)
        batchSize += line.length + 1
    }
    if (batch.isNotEmpty()) pages.add(CodePage(startLine, batch))
    return pages
}
