package dev.arny.githubmanager.remote

import kotlinx.serialization.Serializable

@Serializable
enum class ChangeType { UPSERT, DELETE }

/** A draft is not a Git index entry and not a local Git commit. */
@Serializable
data class RemoteChange(
    val path: String,
    val type: ChangeType,
    val content: String? = null,
    val originalSha: String? = null,
    val originalText: String? = null
)

@Serializable
data class DraftBundle(val changes: List<RemoteChange> = emptyList(), val editor: EditBuffer? = null)

data class RemoteFile(val path: String, val sha: String, val text: String)

@Serializable
data class EditBuffer(
    val originalPath: String?,
    val originalSha: String?,
    val originalText: String,
    val path: String,
    val text: String
) {
    val dirty: Boolean get() = path != originalPath || text != originalText
}

fun validateGitPath(path: String): String {
    require(path.isNotEmpty() && path.length <= 512 && path == path.trim()) { "Некорректный путь" }
    require(!path.startsWith('/') && !path.endsWith('/') && '\\' !in path && '\u0000' !in path) { "Недопустимый путь" }
    require(path.split('/').all {
        it.isNotEmpty() && it != "." && it != ".." && it != ".git" && !it.contains('\n') && !it.contains('\r')
    }) { "Недопустимый компонент пути" }
    return path
}

fun validateChanges(changes: List<RemoteChange>) {
    require(changes.isNotEmpty() && changes.size <= 30) { "Количество файлов должно быть от 1 до 30" }
    require(changes.map { validateGitPath(it.path) }.toSet().size == changes.size) { "Повторяющийся путь" }
    changes.forEach {
        require(it.type == ChangeType.DELETE || (it.content != null && it.content.toByteArray().size <= 512 * 1024)) {
            "Файл слишком большой или отсутствует содержимое"
        }
        require(it.type != ChangeType.DELETE || it.originalSha != null) { "Удаление без исходного SHA запрещено" }
    }
}

/** Preserve files not currently in the draft, replace the change with the same path. */
fun applyDraftChanges(previous: List<RemoteChange>, incoming: List<RemoteChange>): List<RemoteChange> {
    val paths = incoming.map { it.path }.toSet()
    val result = previous.filterNot { it.path in paths } + incoming
    validateChanges(result)
    return result
}
