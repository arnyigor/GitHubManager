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
    suspend fun listReleases(token: String, owner: String, name: String, page: Int = 1, forceRefresh: Boolean = false): ReleasePage =
        throw UnsupportedOperationException("Releases not available")
    suspend fun downloadAsset(
        token: String, owner: String, name: String, asset: GitHubReleaseAsset,
        target: OutputStream, onProgress: (Long, Long) -> Unit
    ) { throw UnsupportedOperationException("Download not available") }
    fun clearCache() {}
}
