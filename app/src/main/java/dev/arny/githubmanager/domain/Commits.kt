package dev.arny.githubmanager.domain

/** One-line commit title: GitHub messages often carry a body after a blank line. */
fun commitTitle(message: String): String =
    message.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { "(без сообщения)" }

fun commitDate(date: String?): String = date?.take(10) ?: "—"

/** "3 файла · +12 −4" — the same numbers GitHub shows on a commit page. */
fun commitStats(files: List<CommitFile>): String {
    if (files.isEmpty()) return "Изменённых файлов нет"
    val word = when {
        files.size % 10 == 1 && files.size % 100 != 11 -> "файл"
        files.size % 10 in 2..4 && files.size % 100 !in 12..14 -> "файла"
        else -> "файлов"
    }
    val added = files.sumOf { it.additions }
    val removed = files.sumOf { it.deletions }
    return "${files.size} $word · +$added −$removed"
}

/** Patch lines keep their leading marker: ' ' context, '+' added, '-' removed. */
fun patchLines(patch: String?): List<String> =
    patch?.lines()?.dropLastWhile { it.isEmpty() } ?: emptyList()

fun statusLabel(status: String): String = when (status) {
    "added" -> "Добавлен"
    "removed" -> "Удалён"
    "renamed" -> "Переименован"
    "modified" -> "Изменён"
    else -> status
}
