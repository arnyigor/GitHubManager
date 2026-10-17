package dev.arny.githubmanager.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown

/** Native Compose GFM Markdown (headings, tables, lists, links, fenced blocks, quotes). */
@Composable
fun MarkdownViewer(source: String, modifier: Modifier = Modifier, scrollable: Boolean = true) {
    val scrollModifier = if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier
    Column(scrollModifier.padding(14.dp)) {
        Markdown(content = source)
    }
}
