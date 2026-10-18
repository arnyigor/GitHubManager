package dev.arny.githubmanager.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RunsParsingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    private val payload = """
        {
          "total_count": 3,
          "workflow_runs": [
            {
              "id": 39065419716,
              "name": "Android CI",
              "display_title": "Code review: commit history with per-file diff",
              "status": "completed",
              "conclusion": "success",
              "head_branch": "main",
              "event": "push",
              "run_number": 17,
              "created_at": "2026-10-18T20:43:00Z",
              "updated_at": "2026-10-18T20:44:30Z",
              "html_url": "https://github.com/arnyigor/GitHubManager/actions/runs/39065419716"
            },
            {
              "id": 39065000000,
              "name": "Android CI",
              "display_title": "CI: use current toolchain",
              "status": "completed",
              "conclusion": "failure",
              "head_branch": "main",
              "event": "push",
              "run_number": 12,
              "updated_at": "2026-10-18T16:20:00Z",
              "html_url": "https://github.com/arnyigor/GitHubManager/actions/runs/39065000000"
            },
            {
              "id": 39066000000,
              "name": "Android CI",
              "status": "in_progress",
              "conclusion": null,
              "head_branch": "main",
              "event": "workflow_dispatch",
              "run_number": 18,
              "updated_at": "2026-10-19T03:00:00Z"
            }
          ]
        }
    """.trimIndent()

    @Test fun workflowRunsPayloadMapsToDto() {
        val parsed = json.decodeFromString<WorkflowRunsResponse>(payload)
        assertEquals(3, parsed.totalCount)
        val runs = parsed.workflowRuns
        assertEquals(3, runs.size)
        assertEquals("Code review: commit history with per-file diff", runs[0].displayTitle)
        assertEquals("main", runs[0].headBranch)
        assertEquals(17, runs[0].runNumber)
        assertNull(runs[2].conclusion)
    }

    @Test fun statusAndConclusionAreDistinguished() {
        val runs = json.decodeFromString<WorkflowRunsResponse>(payload).workflowRuns
        assertEquals("Успешно", runStatusLabel(runs[0]))
        assertEquals("Ошибка", runStatusLabel(runs[1]))
        // A pending run must not be described as finished.
        assertEquals("Выполняется", runStatusLabel(runs[2]))
        assertEquals(RunKind.PENDING, runKind(runs[2]))
    }

    @Test fun kindsDriveTheIconColour() {
        val runs = json.decodeFromString<WorkflowRunsResponse>(payload).workflowRuns
        assertEquals(RunKind.SUCCESS, runKind(runs[0]))
        assertEquals(RunKind.FAILURE, runKind(runs[1]))
        assertEquals(RunKind.FAILURE, runKind(runs[1].copy(conclusion = "timed_out")))
        assertEquals(RunKind.MUTED, runKind(runs[0].copy(conclusion = "cancelled")))
    }

    @Test fun titleFallsBackToWorkflowName() {
        val runs = json.decodeFromString<WorkflowRunsResponse>(payload).workflowRuns
        assertEquals("Android CI", runTitle(runs[2]))
        assertEquals("Android CI #18 · main · workflow_dispatch · 2026-10-19", runMeta(runs[2]))
    }
}
