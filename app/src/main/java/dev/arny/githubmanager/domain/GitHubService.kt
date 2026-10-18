package dev.arny.githubmanager.domain

import java.io.IOException
import java.io.OutputStream

class GitHubApiException(val statusCode: Int, message: String) : IOException(message)

interface GitHubService {
    suspend fun currentUser(token: String): GitHubUser
    suspend fun listRepositories(token: String, page: Int, forceRefresh: Boolean = false): RepoPage
    suspend fun getRepository(token: String, owner: String, name: String, forceRefresh: Boolean = false): GitHubRepository
    suspend fun updateVisibility(token: String, owner: String, name: String, makePrivate: Boolean)
    suspend fun getContent(token: String, owner: String, name: String, path: String, forceRefresh: Boolean = false): DirectoryResult
    /** Raw bytes of a repository file; used when the JSON `content` field is truncated or absent. */
    suspend fun getRawContent(
        token: String, owner: String, name: String, path: String, maxBytes: Long
    ): ByteArray = throw UnsupportedOperationException("Raw content not available")
    suspend fun getContentAtRef(token: String, owner: String, name: String, path: String,
        ref: String, forceRefresh: Boolean = false): DirectoryResult =
        getContent(token, owner, name, path, forceRefresh)

    suspend fun getRawContentAtRef(token: String, owner: String, name: String, path: String,
        ref: String, maxBytes: Long): ByteArray = getRawContent(token, owner, name, path, maxBytes)

    suspend fun listCommits(token: String, owner: String, name: String, ref: String,
        page: Int = 1, forceRefresh: Boolean = false): CommitPage =
        throw UnsupportedOperationException("Commits not available")

    suspend fun getCommit(token: String, owner: String, name: String, sha: String,
        forceRefresh: Boolean = false): CommitDetails =
        throw UnsupportedOperationException("Commit details not available")

    suspend fun listIssues(token: String, owner: String, name: String, state: String,
        page: Int = 1, forceRefresh: Boolean = false): IssuePage =
        throw UnsupportedOperationException("Issues not available")

    suspend fun listReleases(token: String, owner: String, name: String, page: Int = 1, forceRefresh: Boolean = false): ReleasePage =
        throw UnsupportedOperationException("Releases not available")
    suspend fun downloadAsset(
        token: String, owner: String, name: String, asset: GitHubReleaseAsset,
        target: OutputStream, onProgress: (Long, Long) -> Unit
    ) { throw UnsupportedOperationException("Download not available") }
    fun clearCache() {}
}
