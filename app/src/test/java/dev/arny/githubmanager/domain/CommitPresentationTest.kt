package dev.arny.githubmanager.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CommitPresentationTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    @Test fun commitListPayloadMapsToDto() {
        val payload = """
            [
              {
                "sha": "d078da43b8561d0b3d1c6d1b2a4d1e6f7a8b9c0d",
                "node_id": "C_kw",
                "commit": {
                  "message": "feat: create new-file.txt from remote workspace\n\nbody line",
                  "author": { "name": "Arny Igor", "email": "a@b.c", "date": "2026-10-18T07:44:10Z" }
                },
                "author": { "login": "arnyigor", "type": "User" },
                "html_url": "https://github.com/arnyigor/GHM-RemoteTest/commit/d078da43",
                "parents": [ { "sha": "48cd0bdc" } ]
              }
            ]
        """.trimIndent()

        val commits = json.decodeFromString<List<GitHubCommit>>(payload)
        assertEquals(1, commits.size)
        assertEquals("Arny Igor", commits[0].commit.author.name)
        assertEquals("2026-10-18T07:44:10Z", commits[0].commit.author.date)
        assertEquals("arnyigor", commits[0].author?.login)
        assertEquals("feat: create new-file.txt from remote workspace", commitTitle(commits[0].commit.message))
        assertEquals("2026-10-18", commitDate(commits[0].commit.author.date))
    }

    @Test fun commitDetailsPayloadMapsFilesAndPatch() {
        val payload = """
            {
              "sha": "48cd0bdc",
              "commit": { "message": "rename" },
              "files": [
                {
                  "filename": "second.txt",
                  "status": "removed",
                  "additions": 0,
                  "deletions": 3,
                  "patch": "@@ -1,3 +0,0 @@\n-hello world\n-second line\n-created as second.txt"
                },
                {
                  "filename": "second-renamed.txt",
                  "status": "added",
                  "additions": 4,
                  "deletions": 0
                }
              ]
            }
        """.trimIndent()

        val details = json.decodeFromString<CommitDetails>(payload)
        assertEquals(2, details.files.size)
        assertEquals("removed", details.files[0].status)
        assertEquals(4, patchLines(details.files[0].patch).size)
        assertTrue(patchLines(details.files[1].patch).isEmpty())
        assertEquals("2 файла · +4 −3", commitStats(details.files))
        assertEquals("Удалён", statusLabel("removed"))
        assertEquals("Переименован", statusLabel("renamed"))
    }

    @Test fun statsWordFormsAreRussian() {
        fun file() = CommitFile("x")
        assertEquals("1 файл · +0 −0", commitStats(listOf(file())))
        assertEquals("3 файла · +0 −0", commitStats(List(3) { file() }))
        assertEquals("5 файлов · +0 −0", commitStats(List(5) { file() }))
        assertEquals("11 файлов · +0 −0", commitStats(List(11) { file() }))
        assertEquals("21 файл · +0 −0", commitStats(List(21) { file() }))
        assertEquals("Изменённых файлов нет", commitStats(emptyList()))
    }

    @Test fun emptyMessageFallsBack() {
        assertEquals("(без сообщения)", commitTitle(""))
        val message = listOf("  trimmed title", "body").joinToString(separator = "\n")
        assertEquals("trimmed title", commitTitle(message))
        assertEquals("(без сообщения)", commitTitle("   \n"))
    }
}
