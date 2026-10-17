package dev.arny.githubmanager.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RepositoryManagerTest {
    private fun repo(
        private: Boolean = false,
        admin: Boolean = true,
        fork: Boolean = false,
        archived: Boolean = false
    ) = GitHubRepository(
        id = 42, name = "example", fullName = "owner/example",
        owner = RepoOwner("owner"), private = private,
        visibility = if (private) "private" else "public",
        htmlUrl = "https://github.com/owner/example",
        fork = fork, archived = archived,
        permissions = RepoPermissions(admin = admin)
    )

    private class FakeGitHub(var remote: GitHubRepository) : GitHubService {
        var writes = 0
        var reads = 0
        override suspend fun currentUser(token: String) = GitHubUser(1, "owner")
        override suspend fun listRepositories(token: String, page: Int, forceRefresh: Boolean) = RepoPage(listOf(remote), false)
        override suspend fun getRepository(token: String, owner: String, name: String, forceRefresh: Boolean): GitHubRepository {
            reads++
            return remote
        }
        override suspend fun updateVisibility(token: String, owner: String, name: String, makePrivate: Boolean) {
            writes++
            remote = remote.copy(private = makePrivate, visibility = if (makePrivate) "private" else "public")
        }
        override suspend fun getContent(token: String, owner: String, name: String, path: String, forceRefresh: Boolean) =
            DirectoryResult.Directory(emptyList())
    }

    @Test fun adminCanChangeNonFork() {
        assertTrue(canChangeVisibility(repo()))
        assertFalse(canChangeVisibility(repo(admin = false)))
        assertFalse(canChangeVisibility(repo(fork = true)))
        assertFalse(canChangeVisibility(repo(archived = true)))
        assertFalse(canChangeVisibility(repo().copy(permissions = null)))
        assertFalse(canChangeVisibility(repo().copy(visibility = "internal")))
    }

    @Test fun optimisticCheckAndServerVerification() = runBlocking {
        val fake = FakeGitHub(repo())
        val result = RepositoryManager(fake).setPrivate("test_token", repo(), true)
        assertTrue(result.private)
        assertEquals(1, fake.writes)
        assertEquals(2, fake.reads)
    }

    @Test fun refusesStaleStateWithoutMutation() = runBlocking {
        val fake = FakeGitHub(repo(private = true))
        try {
            RepositoryManager(fake).setPrivate("test_token", repo(), true)
            fail("Expected conflict")
        } catch (_: VisibilityConflict) {
            assertEquals(0, fake.writes)
        }
    }

    @Test fun refusesNoAdminWithoutNetworkRequest() = runBlocking {
        val fake = FakeGitHub(repo(admin = false))
        try {
            RepositoryManager(fake).setPrivate("test_token", repo(admin = false), true)
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, fake.writes)
            assertEquals(0, fake.reads)
        }
    }

    @Test fun filtersRespectPrivacyAndCase() {
        val repos = listOf(repo(), repo(private = true).copy(id = 43, name = "Notes", fullName = "owner/Notes"))
        assertEquals(2, filterRepositories(repos, "owner", VisibilityFilter.ALL).size)
        assertEquals(1, filterRepositories(repos, "NOTES", VisibilityFilter.PRIVATE).size)
        assertEquals(1, filterRepositories(repos, "", VisibilityFilter.PUBLIC).size)
    }
}
