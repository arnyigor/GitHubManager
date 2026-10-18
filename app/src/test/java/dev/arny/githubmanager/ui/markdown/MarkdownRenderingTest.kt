package dev.arny.githubmanager.ui.markdown

import org.junit.Assert.*
import org.junit.Test

class MarkdownLinksTest {
    private val base = MarkdownBase("arnyigor", "GitHubManager", "main", "docs")

    @Test fun relativeFileLinkBecomesBlobUrl() {
        assertEquals("[план](https://github.com/arnyigor/GitHubManager/blob/main/docs/ROADMAP.md)",
            resolveRelativeLinks("[план](ROADMAP.md)", base))
    }

    @Test fun parentSegmentsAreNormalised() {
        assertEquals("[код](https://github.com/arnyigor/GitHubManager/blob/main/app/Main.kt)",
            resolveRelativeLinks("[код](../app/Main.kt)", base))
        assertEquals("[код](https://github.com/arnyigor/GitHubManager/blob/main/README.md)",
            resolveRelativeLinks("[код](/README.md)", base))
    }

    @Test fun imagesGoToRawContent() {
        assertEquals("![скрин](https://raw.githubusercontent.com/arnyigor/GitHubManager/main/docs/shot.png)",
            resolveRelativeLinks("![скрин](shot.png)", base))
    }

    @Test fun anchorsAndExternalUrlsAreUntouched() {
        val source = "[якорь](#section) [gh](https://github.com/x/y) [mail](mailto:a@b.c)"
        assertEquals(source, resolveRelativeLinks(source, base))
    }

    @Test fun escapingTheRepositoryIsRejected() {
        val source = "[win](../../../etc/passwd)"
        assertEquals(source, resolveRelativeLinks(source, base))
    }

    @Test fun titleSuffixIsPreserved() {
        assertEquals("[a](https://github.com/arnyigor/GitHubManager/blob/main/docs/b.md \"Заголовок\")",
            resolveRelativeLinks("[a](b.md \"Заголовок\")", base))
    }

    @Test fun rootDirectoryKeepsPathShort() {
        val root = base.copy(directory = "")
        assertEquals("[x](https://github.com/arnyigor/GitHubManager/blob/main/src/x.kt)",
            resolveRelativeLinks("[x](src/x.kt)", root))
    }
}

class CodeHighlightTest {
    @Test fun keywordsStringsAndCommentsAreFound() {
        val code = "// comment\nval name = \"text\"\nfun main() {}\n"
        val kinds = codeTokens(code, "kt").map { it.kind }
        assertEquals(2, kinds.count { it == CodeTokenKind.KEYWORD })   // val, fun
        assertEquals(1, kinds.count { it == CodeTokenKind.STRING })
        assertEquals(1, kinds.count { it == CodeTokenKind.COMMENT })
    }

    @Test fun keywordInsideAStringIsNotAToken() {
        val tokens = codeTokens("val s = \"fun main\"", "kt")
        assertEquals(1, tokens.count { it.kind == CodeTokenKind.KEYWORD })
        assertTrue(tokens.any { it.kind == CodeTokenKind.STRING })
    }

    @Test fun keywordInsideACommentIsNotAToken() {
        val tokens = codeTokens("// fun val class", "kt")
        assertEquals(1, tokens.size)
        assertEquals(CodeTokenKind.COMMENT, tokens.first().kind)
    }

    @Test fun unknownLanguageStillHighlightsStringsAndComments() {
        val tokens = codeTokens("x = \"s\" # note", "whatever")
        assertEquals(2, tokens.size)
    }

    @Test fun emptyCodeHasNoTokens() {
        assertTrue(codeTokens("", "kt").isEmpty())
    }
}
