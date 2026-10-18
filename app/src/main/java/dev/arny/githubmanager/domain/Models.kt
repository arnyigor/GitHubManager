package dev.arny.githubmanager.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val id: Long,
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
data class RepoOwner(
    val login: String,
    val type: String = "User"
)

@Serializable
data class RepoPermissions(
    val admin: Boolean = false,
    val push: Boolean = false,
    val pull: Boolean = false
)

@Serializable
data class GitHubRepository(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: RepoOwner,
    val private: Boolean,
    val visibility: String? = null,
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val language: String? = null,
    val fork: Boolean = false,
    val archived: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val permissions: RepoPermissions? = null
)

@Serializable
data class GitHubContent(
    val type: String,
    val name: String,
    val path: String,
    val size: Long = 0,
    val encoding: String? = null,
    val content: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

/**
 * Where the shown page came from. `STALE_OFFLINE` is a cache entry older than the TTL shown
 * because the network failed; the UI must label it instead of pretending it is current.
 */
enum class CacheSource { NETWORK, VALIDATED_304, FRESH_CACHE, STALE_OFFLINE }

data class RepoPage(
    val entries: List<GitHubRepository>,
    val hasMore: Boolean,
    val source: CacheSource = CacheSource.NETWORK
)

sealed interface DirectoryResult {
    data class Directory(val entries: List<GitHubContent>) : DirectoryResult
    data class File(val file: GitHubContent) : DirectoryResult
}


@Serializable
data class GitHubRelease(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String = "application/octet-stream",
    @SerialName("download_count") val downloadCount: Long = 0,
    val digest: String? = null
)

@Serializable
data class CommitInfo(
    val message: String = "",
    val author: CommitAuthor = CommitAuthor()
)

@Serializable
data class CommitAuthor(val name: String = "", val date: String? = null)

@Serializable
data class GitHubCommit(
    val sha: String,
    val commit: CommitInfo = CommitInfo(),
    val author: RepoOwner? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class CommitFile(
    val filename: String,
    val status: String = "modified",
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String? = null
)

@Serializable
data class CommitDetails(val files: List<CommitFile> = emptyList())

@Serializable
data class GitHubLabel(val name: String, val color: String = "ededed")

@Serializable
data class GitHubIssue(
    val number: Int,
    val title: String,
    val state: String = "open",
    val body: String? = null,
    val user: RepoOwner? = null,
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val labels: List<GitHubLabel> = emptyList(),
    /** Present only for pull requests: /issues returns both kinds. */
    @SerialName("pull_request") val pullRequest: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
data class GitHubWorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val status: String = "queued",
    val conclusion: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    val event: String? = null,
    @SerialName("run_number") val runNumber: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class WorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList()
)

data class RunPage(
    val entries: List<GitHubWorkflowRun>,
    val hasMore: Boolean,
    val source: CacheSource = CacheSource.NETWORK
)

data class IssuePage(
    val entries: List<GitHubIssue>,
    val hasMore: Boolean,
    val source: CacheSource = CacheSource.NETWORK
)

data class CommitPage(
    val entries: List<GitHubCommit>,
    val hasMore: Boolean,
    val source: CacheSource = CacheSource.NETWORK
)

data class ReleasePage(
    val entries: List<GitHubRelease>,
    val hasMore: Boolean,
    val source: CacheSource = CacheSource.NETWORK
)
