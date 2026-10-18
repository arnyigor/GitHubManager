package dev.arny.githubmanager.domain

/** `/issues` returns pull requests too; the issues screen must not lose them silently. */
fun onlyIssues(entries: List<GitHubIssue>): List<GitHubIssue> = entries.filter { it.pullRequest == null }

fun issueTitle(issue: GitHubIssue): String = "#${issue.number} · ${issue.title}"

fun issueStateLabel(state: String): String = if (state == "open") "Открыт" else "Закрыт"

fun issueMeta(issue: GitHubIssue): String = buildString {
    append(issue.user?.login ?: "—")
    append(" · ")
    append(issue.updatedAt?.take(10) ?: issue.createdAt?.take(10) ?: "—")
    if (issue.comments > 0) {
        append(" · ")
        append(issue.comments)
        append(if (issue.comments == 1) " комментарий" else " комментариев")
    }
}
