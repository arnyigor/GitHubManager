package dev.arny.githubmanager.ui.markdown

/**
 * A Markdown document is stored relative to its directory, but the renderer opens links as URLs.
 * Relative links and images are rewritten to absolute GitHub URLs so a tap actually works.
 */
data class MarkdownBase(
    val owner: String,
    val repo: String,
    val ref: String,
    val directory: String
) {
    fun blobUrl(path: String): String = "https://github.com/$owner/$repo/blob/$ref/$path"
    fun rawUrl(path: String): String = "https://raw.githubusercontent.com/$owner/$repo/$ref/$path"
}

private val INLINE_LINK = Regex("\\]\\(([^)\\s]+)(\\s+\"[^\"]*\")?\\)")

private fun isExternal(target: String): Boolean =
    target.startsWith("http://") || target.startsWith("https://") || target.startsWith("mailto:") ||
        target.startsWith("#") || target.startsWith("//")

/** `a/./b`, `a/../c` and leading `./` are normalised; the result stays inside the repository. */
internal fun normalizeRepoPath(directory: String, target: String): String? {
    val clean = target.substringBefore('#').substringBefore('?')
    if (clean.isEmpty()) return null
    // `+` binds tighter than `if`, so the prefix is built separately.
    val prefix = if (clean.startsWith("/")) emptyList() else directory.split('/').filter { it.isNotEmpty() }
    val parts = (prefix + clean.trimStart('/').split('/')).toMutableList()
    val result = ArrayList<String>()
    parts.forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (result.isEmpty()) return null else result.removeAt(result.lastIndex)
            else -> result.add(part)
        }
    }
    return result.joinToString("/").ifEmpty { null }
}

/**
 * Rewrites relative link and image destinations. Absolute URLs, anchors and reference-style
 * links are left untouched. `image` targets go to raw.githubusercontent.com, the rest to /blob/.
 */
fun resolveRelativeLinks(source: String, base: MarkdownBase): String =
    INLINE_LINK.replace(source) { match ->
        val target = match.groupValues[1]
        val suffix = match.groupValues[2]
        if (isExternal(target)) return@replace match.value
        val path = normalizeRepoPath(base.directory, target) ?: return@replace match.value
        // match starts at "]", so scan back to "[" and check the marker before it: ![alt](img.png).
        val bracket = source.lastIndexOf('[', match.range.first).takeIf { it >= 0 } ?: match.range.first
        val isImage = bracket > 0 && source[bracket - 1] == '!'
        val url = if (isImage) base.rawUrl(path) else base.blobUrl(path)
        "]($url$suffix)"
    }
