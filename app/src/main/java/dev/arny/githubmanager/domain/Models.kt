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

data class ReleasePage(
    val entries: List<GitHubRelease>,
    val hasMore: Boolean,
    val source: CacheSource = CacheSource.NETWORK
)
