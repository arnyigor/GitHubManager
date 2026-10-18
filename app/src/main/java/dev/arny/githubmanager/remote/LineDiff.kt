package dev.arny.githubmanager.remote

data class DiffLine(val prefix: Char, val content: String)

/** Bounded LCS-based preview. This is UI review, not a Git patch application algorithm. */
fun previewDiff(before: String, after: String, maxLines: Int = 350): List<DiffLine> {
    // An empty string is zero lines, not one empty line: otherwise new/deleted files get a phantom ""/"+" row.
    val a = if (before.isEmpty()) emptyList() else before.split('\n')
    val b = if (after.isEmpty()) emptyList() else after.split('\n')
    if (a.size > maxLines || b.size > maxLines) return listOf(DiffLine('!', "Файл слишком большой для inline diff — откройте редактор"))
    val n = a.size
    val m = b.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
        dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
    }
    val output = ArrayList<DiffLine>()
    var i = 0; var j = 0
    while (i < n || j < m) {
        when {
            i < n && j < m && a[i] == b[j] -> { output.add(DiffLine(' ', a[i])); i++; j++ }
            j < m && (i == n || dp[i][j + 1] > dp[i + 1][j]) -> { output.add(DiffLine('+', b[j])); j++ }
            else -> { output.add(DiffLine('-', a[i])); i++ }
        }
    }
    return output
}
