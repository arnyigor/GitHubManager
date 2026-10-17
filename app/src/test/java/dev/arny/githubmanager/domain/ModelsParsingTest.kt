package dev.arny.githubmanager.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses trimmed but real GitHub REST payloads (captured 2026-10-17 from
 * api.github.com/repos/octocat/Hello-World and /contents/) with the same Json
 * configuration the app uses. Guards DTO field mappings without needing a token.
 */
class ModelsParsingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    @Test
    fun repositoryPayloadMapsToDto() {
        val payload = """
            {
              "id": 1296269,
              "node_id": "MDEwOlJlcG9zaXRvcnkxMjk2MjY5",
              "name": "Hello-World",
              "full_name": "octocat/Hello-World",
              "private": false,
              "owner": { "login": "octocat", "id": 583231, "type": "User", "site_admin": false },
              "html_url": "https://github.com/octocat/Hello-World",
              "description": "My first repository on GitHub!",
              "fork": false,
              "archived": false,
              "visibility": "public",
              "language": null,
              "updated_at": "2026-10-17T00:09:28Z",
              "default_branch": "master",
              "stargazers_count": 2800
            }
        """.trimIndent()

        val repo = json.decodeFromString<GitHubRepository>(payload)

        assertEquals(1296269L, repo.id)
        assertEquals("octocat/Hello-World", repo.fullName)
        assertEquals("octocat", repo.owner.login)
        assertFalse(repo.private)
        assertEquals("public", repo.visibility)
        assertEquals("https://github.com/octocat/Hello-World", repo.htmlUrl)
        assertEquals("2026-10-17T00:09:28Z", repo.updatedAt)
        assertNull(repo.language)
        // Anonymous payloads carry no permissions object: mutation must stay disabled.
        assertNull(repo.permissions)
        assertFalse(canChangeVisibility(repo))
    }

    @Test
    fun directoryListingAndFilePayloadMapToDto() {
        val listing = """
            [
              { "type": "file", "name": "README", "path": "README", "size": 13,
                "sha": "abc", "url": "https://api.github.com/repos/octocat/Hello-World/contents/README",
                "html_url": "https://github.com/octocat/Hello-World/blob/master/README",
                "download_url": "https://raw.githubusercontent.com/octocat/Hello-World/master/README" }
            ]
        """.trimIndent()
        val entries = json.decodeFromString<List<GitHubContent>>(listing)
        assertEquals(1, entries.size)
        assertEquals("file", entries[0].type)
        assertEquals(13L, entries[0].size)
        assertNull(entries[0].content)

        val file = """
            { "type": "file", "name": "README", "path": "README", "size": 13,
              "encoding": "base64", "content": "SGVsbG8gV29ybGQhCg==" }
        """.trimIndent()
        val decoded = json.decodeFromString<GitHubContent>(file)
        assertEquals("base64", decoded.encoding)
        assertEquals("Hello World!\n", String(java.util.Base64.getMimeDecoder().decode(decoded.content), Charsets.UTF_8))
    }

    @Test
    fun userPayloadMapsToDto() {
        val user = json.decodeFromString<GitHubUser>(
            """{ "login": "octocat", "id": 583231, "avatar_url": "https://avatars.githubusercontent.com/u/583231?v=4",
                 "type": "User", "name": "The Octocat" }"""
        )
        assertEquals(583231L, user.id)
        assertEquals("octocat", user.login)
        assertTrue(user.avatarUrl.endsWith("v=4"))
    }
}
