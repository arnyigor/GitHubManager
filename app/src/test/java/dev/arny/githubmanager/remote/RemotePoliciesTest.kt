package dev.arny.githubmanager.remote

import org.junit.Assert.*
import org.junit.Test

class RemotePoliciesTest {
    @Test fun rejectsUnsafePaths() {
        listOf("../secret", "/root/file", "a/../b", "foo//bar", "foo/.git/config", "a\\b", "a/")
            .forEach { path ->
                try { validateGitPath(path); fail("Accepted invalid path: $path") }
                catch (_: IllegalArgumentException) { }
            }
        assertEquals("src/main.kt", validateGitPath("src/main.kt"))
    }

    @Test fun deleteRequiresOriginalSha() {
        try {
            validateChanges(listOf(RemoteChange("x.txt", ChangeType.DELETE)))
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun duplicatedPathsFail() {
        try {
            validateChanges(listOf(RemoteChange("x", ChangeType.UPSERT, "first"),
                RemoteChange("x", ChangeType.UPSERT, "second")))
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun replacingOneDraftKeepsOthers() {
        val old = listOf(RemoteChange("a", ChangeType.UPSERT, "old"), RemoteChange("b", ChangeType.UPSERT, "keep"))
        val next = applyDraftChanges(old, listOf(RemoteChange("a", ChangeType.UPSERT, "new")))
        assertEquals("new", next.first { it.path == "a" }.content)
        assertEquals("keep", next.first { it.path == "b" }.content)
    }

    @Test fun diffPreservesChangedLines() {
        assertEquals(listOf(' ', '-', '+'), previewDiff("a\nb", "a\nc").map { it.prefix })
    }

    @Test fun newAndDeletedFilesHaveNoPhantomLine() {
        assertEquals(listOf('+', '+'), previewDiff("", "first\nsecond").map { it.prefix })
        assertEquals(listOf('-', '-'), previewDiff("first\nsecond", "").map { it.prefix })
        assertEquals(emptyList<DiffLine>(), previewDiff("", ""))
    }

    @Test fun untouchedEditorIsNotDirty() {
        // Guard used by saveDraft: an untouched editor must not stage a change.
        val edit = EditBuffer("a.txt", "sha", "text", "a.txt", "text")
        assertFalse(edit.dirty)
        assertTrue(edit.copy(text = "text2").dirty)
        assertTrue(edit.copy(path = "b.txt").dirty)
    }
}
