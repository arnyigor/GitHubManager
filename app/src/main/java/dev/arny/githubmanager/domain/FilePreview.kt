package dev.arny.githubmanager.domain

/** How a repository file can be presented; keeps preview decisions testable without Android. */
enum class FilePreviewKind { MARKDOWN, IMAGE, VECTOR, TEXT, BINARY }

private val markdownExtensions = setOf("md", "markdown", "mdown")
private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "heic", "heif", "avif")
private val vectorExtensions = setOf("svg", "svgz")

fun previewKindFor(name: String, bytes: ByteArray): FilePreviewKind {
    val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    return when {
        extension in markdownExtensions -> FilePreviewKind.MARKDOWN
        extension in imageExtensions -> FilePreviewKind.IMAGE
        extension in vectorExtensions -> FilePreviewKind.VECTOR
        looksBinary(bytes) -> FilePreviewKind.BINARY
        else -> FilePreviewKind.TEXT
    }
}

/** Text files have no NUL bytes in their head; binary content almost always does. */
fun looksBinary(bytes: ByteArray, probe: Int = 8_000): Boolean {
    if (bytes.isEmpty()) return false
    val limit = minOf(probe, bytes.size)
    for (index in 0 until limit) if (bytes[index] == 0.toByte()) return true
    return false
}

/** Hex + ASCII preview in the classic `xxd` layout; large files are cut by [maxBytes]. */
fun hexDump(bytes: ByteArray, maxBytes: Int = 4_096, bytesPerRow: Int = 16): String {
    require(bytesPerRow > 0)
    if (bytes.isEmpty()) return ""
    val limit = minOf(maxBytes, bytes.size)
    val builder = StringBuilder()
    var offset = 0
    while (offset < limit) {
        val end = minOf(offset + bytesPerRow, limit)
        builder.append(offset.toString(16).padStart(8, '0')).append("  ")
        for (index in offset until end) {
            builder.append("%02x ".format(bytes[index].toInt() and 0xff))
            if (index == offset + bytesPerRow / 2 - 1) builder.append(' ')
        }
        // Keep the ASCII column aligned when the last row is short.
        repeat(bytesPerRow - (end - offset)) { builder.append("   ") }
        builder.append(" |")
        for (index in offset until end) {
            val value = bytes[index].toInt() and 0xff
            builder.append(if (value in 32..126) value.toChar() else '.')
        }
        builder.append("|\n")
        offset = end
    }
    if (limit < bytes.size) {
        builder.append("… ещё ").append(bytes.size - limit).append(" байт не показано")
    }
    return builder.toString().trimEnd('\n')
}
