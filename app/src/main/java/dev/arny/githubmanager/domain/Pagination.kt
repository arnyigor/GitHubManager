package dev.arny.githubmanager.domain

/**
 * GitHub pagination is described by the `Link` header, not by the item count:
 * a page can be exactly full and still be the last one.
 */
private val LINK_ENTRY = Regex("<([^>]+)>([^<]*)")

fun nextPageUrl(linkHeader: String?): String? {
    if (linkHeader.isNullOrBlank()) return null
    LINK_ENTRY.findAll(linkHeader).forEach { entry ->
        val url = entry.groupValues[1].trim()
        val params = entry.groupValues[2]
        val rel = Regex("rel\\s*=\\s*\"?([^\";]+)").find(params)?.groupValues?.get(1)?.trim()
        val isNext = rel != null && rel.split(' ').any { it.equals("next", ignoreCase = true) }
        if (isNext && url.isNotEmpty()) return url
    }
    return null
}

/**
 * `linkHeader == null` means the server sent no `Link` at all; only then the old
 * "full page means more" heuristic is used, and only as a fallback.
 */
fun hasNextPage(linkHeader: String?, itemCount: Int, perPage: Int): Boolean {
    if (linkHeader == null) return itemCount >= perPage
    return nextPageUrl(linkHeader) != null
}
