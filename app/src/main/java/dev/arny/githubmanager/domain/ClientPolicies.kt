package dev.arny.githubmanager.domain

/** Pure helpers: unit-testable without Android/Compose dependencies. */
fun isFreshCacheEntry(savedAt: Long, now: Long, ttl: Long = 600_000L): Boolean =
    now >= savedAt && now - savedAt <= ttl

fun isAllowedAssetRedirectHost(host: String): Boolean {
    val normalized = host.lowercase(java.util.Locale.ROOT)
    return normalized == "github.com" || normalized.endsWith(".githubusercontent.com")
}

fun isMarkdownFile(name: String): Boolean = name.lowercase(java.util.Locale.ROOT).let {
    it.endsWith(".md") || it.endsWith(".markdown") || it.endsWith(".mdown")
}

fun digestMatches(expected: String?, actualSha256: String): Boolean {
    if (expected == null) return true
    if (!expected.startsWith("sha256:", ignoreCase = true)) return true // Unsupported algo: no validation claim.
    return expected.substringAfter(':').equals(actualSha256, ignoreCase = true)
}
