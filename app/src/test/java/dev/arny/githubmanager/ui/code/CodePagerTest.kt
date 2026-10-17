package dev.arny.githubmanager.ui.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodePagerTest {
    @Test fun allLinesPreserved() {
        val input = (1..999).joinToString("\n") { "val row$it = $it" }
        val pages = codePages(input, maxLines = 75, maxChars = 500)
        assertTrue(pages.size > 10)
        assertEquals(input, pages.flatMap { it.lines }.joinToString("\n"))
        assertEquals(1, pages.first().firstLine)
        assertEquals(999, pages.last().lastLine)
        pages.zipWithNext().forEach { (a, b) -> assertEquals(a.lastLine + 1, b.firstLine) }
    }

    @Test fun emptyAndTrailingLinesPreserved() {
        listOf("", "a\n", "\n", "a\n\nb").forEach { original ->
            val pages = codePages(original, maxLines = 1, maxChars = 1)
            assertEquals(original, pages.flatMap { it.lines }.joinToString("\n"))
        }
    }

    @Test fun exceptionallyLongLineIsNotLost() {
        val input = "x".repeat(80_000) + "\nend"
        val pages = codePages(input)
        assertEquals(input, pages.flatMap { it.lines }.joinToString("\n"))
        assertEquals(2, pages.last().lastLine)
    }

    @Test(expected = IllegalArgumentException::class)
    fun badPageSizeRejected() { codePages("a", maxLines = 0) }
}
