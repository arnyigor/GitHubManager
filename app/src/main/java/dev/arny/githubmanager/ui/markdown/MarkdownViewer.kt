package dev.arny.githubmanager.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/** Native Compose GFM Markdown (headings, tables, lists, links, fenced blocks, quotes). */
@Composable
fun MarkdownViewer(source: String, modifier: Modifier = Modifier, scrollable: Boolean = true) {
    val scrollModifier = if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier
    val base = MaterialTheme.typography
    val mono = base.bodySmall.copy(fontFamily = FontFamily.Monospace)
    // Default H1/H2 in the renderer are mobile-hostile (28/24sp) and look identical; keep a readable scale.
    val compact = markdownTypography(
        h1 = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        h2 = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        h3 = base.titleSmall.copy(fontWeight = FontWeight.Bold),
        h4 = base.titleSmall,
        h5 = base.labelLarge,
        h6 = base.labelLarge,
        text = base.bodyMedium,
        code = mono,
        inlineCode = mono
    )
    Column(scrollModifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Markdown(content = source, typography = compact)
    }
}
