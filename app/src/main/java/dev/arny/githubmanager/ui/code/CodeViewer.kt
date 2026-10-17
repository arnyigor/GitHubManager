package dev.arny.githubmanager.ui.code

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hossain.highlight.engine.HighlightLanguage
import dev.hossain.highlight.ui.rememberHighlightedCode

/** Read-only code viewport; page size stays bounded even for 200 KB GitHub API files. */
@Composable
fun CodeViewer(fileName: String, filePath: String, code: String, url: String?, showPath: Boolean = true) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val fullPath = if (filePath.isEmpty()) fileName else "$filePath/$fileName"
    val language = remember(fileName) {
        val special = when (fileName.lowercase()) {
            "dockerfile" -> "dockerfile"
            "makefile" -> "makefile"
            ".gitignore", ".gitattributes" -> "plaintext"
            else -> null
        }
        special ?: HighlightLanguage.fromExtension(fileName.substringAfterLast('.', "")) ?: "plaintext"
    }
    val pages = remember(code) { codePages(code) }
    var pageIndex by rememberSaveable(fullPath) { mutableIntStateOf(0) }
    val safePageIndex = pageIndex.coerceIn(0, pages.lastIndex)
    val current = pages[safePageIndex]
    val text = remember(code, safePageIndex) { current.text }
    // Exceptionally long minified lines can overwhelm tokenizers: render plain text instead.
    val enableHighlight = text.length <= 40_000 && language != "plaintext"
    val highlighted by rememberHighlightedCode(
        code = if (enableHighlight) text else "", language = language
    )
    val display = if (enableHighlight) highlighted ?: AnnotatedString(text) else AnnotatedString(text)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                if (showPath) Text(fullPath, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium)
                Text("$language · ${code.length} символов · ${pages.last().lastLine} строк",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(fullPath)) }) {
                Icon(Icons.Default.ContentCopy, "Копировать путь")
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Icon(Icons.Default.FileCopy, "Копировать весь код")
            }
            if (url?.startsWith("https://github.com/") == true) {
                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Icon(Icons.Default.OpenInBrowser, "Открыть на GitHub")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (pages.size > 1) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { pageIndex = (safePageIndex - 1).coerceAtLeast(0) }, enabled = safePageIndex > 0) {
                    Icon(Icons.Default.ChevronLeft, "Предыдущая часть")
                }
                Text("${current.firstLine}–${current.lastLine} из ${pages.last().lastLine}",
                    style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { pageIndex = (safePageIndex + 1).coerceAtMost(pages.lastIndex) },
                    enabled = safePageIndex < pages.lastIndex) {
                    Icon(Icons.Default.ChevronRight, "Следующая часть")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (!enableHighlight && language != "plaintext") {
            Text("Подсветка отключена для очень длинной строки или страницы.",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
        // The gutter is independent of horizontal scrolling, so line numbers remain visible.
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
            val codeStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace,
                fontSize = 12.sp, lineHeight = 19.sp)
            Text(
                text = (current.firstLine..current.lastLine).joinToString("\n"),
                modifier = Modifier.padding(start = 10.dp, end = 12.dp),
                style = codeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.weight(1f)) {
                SelectionContainer {
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        Text(display, modifier = Modifier.padding(end = 18.dp), style = codeStyle,
                            softWrap = false, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
