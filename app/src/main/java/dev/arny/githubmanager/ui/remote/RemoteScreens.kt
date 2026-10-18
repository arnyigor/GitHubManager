package dev.arny.githubmanager.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arny.githubmanager.remote.*

/** Lightweight Compose text editor with syntax coloring; no third-party editor SDK required. */
private class SyntaxColors(private val filename: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        if (source.length > 100_000) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(source)
        // Regexp based minimal highlighting; preserve offsets and selections.
        val extension = filename.substringAfterLast('.', "").lowercase()
        val keywords = when (extension) {
            "kt", "kts", "java" -> "fun|val|var|class|interface|object|data|sealed|private|public|override|return|if|else|when|suspend|import|package|true|false|null"
            "py" -> "def|class|import|from|return|if|elif|else|async|await|True|False|None|for|while"
            "js", "ts", "jsx", "tsx" -> "const|let|var|function|class|return|if|else|async|await|export|import|true|false|null"
            else -> ""
        }
        if (keywords.isNotEmpty()) Regex("\\b(?:$keywords)\\b").findAll(source).take(3000).forEach {
            builder.addStyle(SpanStyle(color = Color(0xFF7DB6F8), fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
        }
        Regex("\\\"(?:[^\\\"\\\\]|\\\\.)*\\\"").findAll(source).take(2500).forEach {
            builder.addStyle(SpanStyle(color = Color(0xFF8CC79C)), it.range.first, it.range.last + 1)
        }
        Regex("(?m)//[^\\n]*|#[^\\n]*").findAll(source).take(2000).forEach {
            builder.addStyle(SpanStyle(color = Color(0xFF8A969F)), it.range.first, it.range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun RemoteEditorScreen(state: RemoteState, workspace: RemoteWorkspace) {
    val editor = state.editor ?: return
    var confirmDelete by remember(editor.originalPath) { mutableStateOf(false) }
    var confirmDiscard by remember(editor.originalPath) { mutableStateOf(false) }
    val colors = remember(editor.path) { SyntaxColors(editor.path) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (editor.dirty) confirmDiscard = true else workspace.closeEditor() }) {
                Icon(Icons.Default.Close, "Закрыть")
            }
            Text("Редактирование · ${state.branch}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = workspace::saveDraft) { Text("Сохранить черновик") }
        }
        OutlinedTextField(value = editor.path, onValueChange = { workspace.updateEditor(it, editor.text) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), label = { Text("Путь файла") }, singleLine = true)
        Text("Черновик не создаёт коммит. Изменения отправляются только на экране «Изменения».",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                BasicTextField(value = editor.text, onValueChange = { workspace.updateEditor(editor.path, it) },
                    modifier = Modifier.widthIn(min = 300.dp).fillMaxWidth(),
                    visualTransformation = colors,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 19.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
            }
        }
        if (editor.originalPath != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(8.dp)) {
            Text("Пометить файл для удаления", color = MaterialTheme.colorScheme.error)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить файл?") }, text = { Text("Удаление будет добавлено в черновики. Отправка произойдёт только при коммите.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; workspace.stageDelete() }) { Text("Добавить удаление") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Закрыть без сохранения?") }, text = { Text("Несохранённые изменения редактора будут потеряны.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; workspace.closeEditor() }) { Text("Закрыть") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Продолжить редактирование") } })
}

@Composable
fun RemoteChangesScreen(state: RemoteState, workspace: RemoteWorkspace) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var commitMessage by remember(state.branch) { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Черновики · ${state.branch}", style = MaterialTheme.typography.titleLarge)
        Text("Один атомарный коммит на GitHub. Это не локальный staging и не push.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.changes.isEmpty()) item { Text("Изменений нет") }
            items(state.changes, key = { it.path }) { change ->
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(change.path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text(if (change.type == ChangeType.DELETE) "Удаление" else if (change.originalSha == null) "Создание" else "Изменение",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { workspace.discardDraft(change.path) }) { Icon(Icons.Default.Close, "Убрать из черновиков") }
                        IconButton(onClick = { expanded = if (expanded == change.path) null else change.path }) {
                            Icon(Icons.Default.Difference, "Показать diff")
                        }
                    }
                    if (expanded == change.path) {
                        val before = change.originalText ?: ""
                        val after = if (change.type == ChangeType.DELETE) "" else change.content ?: ""
                        val diff = remember(before, after) { previewDiff(before, after) }
                        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            diff.forEach { line ->
                                Text("${line.prefix} ${line.content}", fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = when(line.prefix) {
                                        '+' -> Color(0xFF3EA873)
                                        '-' -> Color(0xFFD76868)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    })
                            }
                        }
                    }
                    }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedTextField(value = commitMessage, onValueChange = { commitMessage = it },
            label = { Text("Сообщение коммита") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { confirm = true }, enabled = state.changes.isNotEmpty() && commitMessage.isNotBlank() && !state.busy,
            modifier = Modifier.fillMaxWidth()) {
            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Создать коммит на GitHub (${state.changes.size})")
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Подтвердить запись в ${state.branch}") },
        text = { Text("Будет создан один серверный Git-коммит из ${state.changes.size} изменений. При конфликте черновики сохранятся.") },
        confirmButton = { TextButton(onClick = { confirm = false; workspace.commit(commitMessage) }) { Text("Создать коммит") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
}
