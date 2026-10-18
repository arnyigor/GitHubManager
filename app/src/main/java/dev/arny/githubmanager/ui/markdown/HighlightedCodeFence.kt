package dev.arny.githubmanager.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel

val CodeCommentColor = Color(0xFF8A969F)
val CodeStringColor = Color(0xFF8CC79C)
val CodeKeywordColor = Color(0xFF7DB6F8)

/**
 * Fenced blocks are rendered by their own component in the library, so the annotator never sees them.
 * Highlighting is applied here: the fence text is tokenised and coloured, the fence markers are dropped.
 */
@Composable
fun HighlightedCodeFence(model: MarkdownComponentModel) {
    // `model.content` is the whole document; the fence node bounds give this block only.
    val node = model.content.substring(
        model.node.startOffset.coerceAtMost(model.content.length),
        model.node.endOffset.coerceAtMost(model.content.length)
    )
    val text = codeBlockBody(node)
    val language = node.lineSequence().firstOrNull().orEmpty().trim()
        .removePrefix("```").removePrefix("~~~").trim().substringBefore(' ').lowercase()
    val annotated = remember(text, language) {
        buildAnnotatedString {
            append(text)
            codeTokens(text, language).forEach { token ->
                val color = when (token.kind) {
                    CodeTokenKind.COMMENT -> CodeCommentColor
                    CodeTokenKind.STRING -> CodeStringColor
                    CodeTokenKind.KEYWORD -> CodeKeywordColor
                }
                val style = if (token.kind == CodeTokenKind.KEYWORD) {
                    SpanStyle(color = color, fontWeight = FontWeight.SemiBold)
                } else SpanStyle(color = color)
                addStyle(style, token.range.first, token.range.last + 1)
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = annotated,
                style = model.typography.code,
                softWrap = false,
                overflow = TextOverflow.Visible,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Strips the ``` fences lines so only the code body is shown. */
internal fun codeBlockBody(content: String): String {
    val lines = content.lines()
    if (lines.size >= 2 && (lines.first().trimStart().startsWith("```") || lines.first().trimStart().startsWith("~~~"))) {
        val body = lines.subList(1, lines.size)
        val end = body.indexOfLast { it.trimStart().startsWith("```") || it.trimStart().startsWith("~~~") }
        val inner = if (end >= 0) body.subList(0, end) else body
        return inner.joinToString("\n").trimEnd('\n')
    }
    return content
}
