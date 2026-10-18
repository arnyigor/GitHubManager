package dev.arny.githubmanager.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/** Native Compose GFM Markdown (headings, tables, lists, links, fenced blocks, quotes). */
@Composable
fun MarkdownViewer(
    source: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    base: MarkdownBase? = null
) {
    val scrollModifier = if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier
    val baseTypography = MaterialTheme.typography
    val mono = baseTypography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    // Default H1/H2 in the renderer are mobile-hostile (28/24sp) and look identical; keep a readable scale.
    val compact = markdownTypography(
        h1 = baseTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
        h2 = baseTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
        h3 = baseTypography.titleSmall.copy(fontWeight = FontWeight.Bold),
        h4 = baseTypography.titleSmall,
        h5 = baseTypography.labelLarge,
        h6 = baseTypography.labelLarge,
        text = baseTypography.bodyMedium,
        code = mono,
        inlineCode = mono
    )
    // Relative links inside a repository README need the repository coordinates.
    val content = remember(source, base) { if (base == null) source else resolveRelativeLinks(source, base) }
    // Code fences get their own component: the library renders them outside the annotator pass.
    val components = remember { markdownComponents(codeFence = { model -> HighlightedCodeFence(model) }) }
    Column(scrollModifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Markdown(content = content, typography = compact, components = components)
    }
}
