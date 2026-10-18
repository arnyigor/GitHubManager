package dev.arny.githubmanager.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class IssuesParsingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    private val payload = """
        [
          {
            "url": "https://api.github.com/repos/arnyigor/GitHubManager/issues/3",
            "number": 3,
            "title": "Добавить просмотр Actions",
            "state": "open",
            "body": "Хочу видеть статусы workflow прямо в приложении.\n\n```yaml\non: push\n```",
            "user": { "login": "arnyigor", "type": "User" },
            "labels": [ { "name": "enhancement", "color": "a2eeef" } ],
            "comments": 2,
            "created_at": "2026-10-18T10:00:00Z",
            "updated_at": "2026-10-18T12:30:00Z",
            "html_url": "https://github.com/arnyigor/GitHubManager/issues/3"
          },
          {
            "number": 4,
            "title": "Docs: fence and relative links",
            "state": "open",
            "body": null,
            "user": { "login": "arnyigor", "type": "User" },
            "comments": 0,
            "updated_at": "2026-10-18T09:00:00Z",
            "html_url": "https://github.com/arnyigor/GitHubManager/pull/4",
            "pull_request": { "url": "https://api.github.com/repos/arnyigor/GitHubManager/pulls/4" }
          }
        ]
    """.trimIndent()

    @Test fun issuePayloadMapsToDto() {
        val all = json.decodeFromString<List<GitHubIssue>>(payload)
        assertEquals(2, all.size)
        val issue = all[0]
        assertEquals(3, issue.number)
        assertEquals("open", issue.state)
        assertEquals("arnyigor", issue.user?.login)
        assertEquals(2, issue.comments)
        assertEquals("enhancement", issue.labels.single().name)
        assertEquals("2026-10-18", issue.updatedAt?.take(10))
        assertNull(issue.pullRequest)
    }

    @Test fun pullRequestsAreRecognisedAndFiltered() {
        val all = json.decodeFromString<List<GitHubIssue>>(payload)
        assertNotNull(all[1].pullRequest)
        val issues = onlyIssues(all)
        assertEquals(1, issues.size)
        assertEquals(3, issues.single().number)
    }

    @Test fun labelsAndMetaAreRendered() {
        val issue = json.decodeFromString<List<GitHubIssue>>(payload)[0]
        assertEquals("#3 · Добавить просмотр Actions", issueTitle(issue))
        assertEquals("Открыт", issueStateLabel(issue.state))
        assertEquals("arnyigor · 2026-10-18 · 2 комментариев", issueMeta(issue))
        assertEquals("Закрыт", issueStateLabel("closed"))
    }

    @Test fun missingBodyMeansNothingToExpand() {
        val pr = json.decodeFromString<List<GitHubIssue>>(payload)[1]
        assertNull(pr.body)
        // Without comments the meta line has no comment segment at all.
        assertEquals("arnyigor · 2026-10-18", issueMeta(pr))
    }
}
