package dev.arny.githubmanager.remote

import dev.arny.githubmanager.domain.GitHubRepository
import dev.arny.githubmanager.security.EncryptedDraftStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Deliberately separate from MainViewModel: remote drafts are not local Git staging. */
data class RemoteState(
    val branch: String = "main",
    val branches: List<String> = emptyList(),
    val editor: EditBuffer? = null,
    val changes: List<RemoteChange> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

class RemoteWorkspace(
    private val remote: RemoteGitHubApi,
    private val drafts: EncryptedDraftStore,
    private val tokenProvider: () -> String?,
    private val scope: CoroutineScope,
    private val onCommitted: () -> Unit
) {
    private var repo: GitHubRepository? = null
    private var editorSave: Job? = null
    private val mutable = MutableStateFlow(RemoteState())
    val state = mutable.asStateFlow()

    fun open(selected: GitHubRepository) {
        editorSave?.cancel()
        repo = selected
        val branch = selected.defaultBranch
        val saved = loadBundle(branch)
        mutable.value = RemoteState(branch = branch, changes = saved.changes, editor = saved.editor)
        val id = selected.id
        scope.launch {
            try {
                val tok = tokenProvider() ?: return@launch
                val names = remote.branches(tok, selected.owner.login, selected.name)
                if (repo?.id == id) mutable.update { it.copy(branches = names) }
            } catch (_: Exception) { /* branch listing is auxiliary; default remains available */ }
        }
    }

    fun reset() {
        editorSave?.cancel()
        repo = null
        mutable.value = RemoteState()
    }

    fun logout() {
        reset()
        drafts.clearAll()
    }

    private fun loadBundle(branch: String): DraftBundle {
        val r = repo ?: return DraftBundle()
        val t = tokenProvider() ?: return DraftBundle()
        return drafts.read(t, r.id, branch)
    }

    private fun persist(changes: List<RemoteChange>, editor: EditBuffer? = mutable.value.editor) {
        val r = repo ?: error("Репозиторий не выбран")
        val token = tokenProvider() ?: error("Требуется авторизация")
        editorSave?.cancel()
        drafts.write(token, r.id, mutable.value.branch, DraftBundle(changes, editor))
        mutable.update { it.copy(changes = changes, editor = editor) }
    }

    fun selectBranch(branch: String, onSelected: () -> Unit) {
        if (mutable.value.busy || mutable.value.editor != null) return
        if (branch !in mutable.value.branches && branch != repo?.defaultBranch) return
        editorSave?.cancel()
        val saved = loadBundle(branch)
        mutable.value = RemoteState(branch = branch, branches = mutable.value.branches,
            changes = saved.changes, editor = saved.editor)
        onSelected()
    }

    fun newFile(directory: String) {
        if (mutable.value.busy) return
        val prefix = directory.trim('/')
        val path = if (prefix.isEmpty()) "new-file.txt" else "$prefix/new-file.txt"
        mutable.update { it.copy(editor = EditBuffer(null, null, "", path, ""), error = null) }
        autosaveEditor()
    }

    fun editFile(path: String) {
        if (mutable.value.busy) return
        val r = repo ?: return
        val branch = mutable.value.branch
        scope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            try {
                val file = remote.readFile(tokenProvider() ?: error("Нет токена"), r.owner.login, r.name, branch, path)
                if (repo?.id == r.id && mutable.value.branch == branch) {
                    val draft = mutable.value.changes.firstOrNull { it.path == path && it.type == ChangeType.UPSERT }
                    if (draft != null && draft.originalSha != file.sha) {
                        throw IllegalStateException("Файл изменён на GitHub. Черновик сохранён; обновите и сравните вручную")
                    }
                    mutable.update { it.copy(editor = EditBuffer(path, file.sha, file.text, path,
                        draft?.content ?: file.text)) }
                    autosaveEditor()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "Файл недоступен") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun updateEditor(path: String, text: String) {
        mutable.update { it.copy(editor = it.editor?.copy(path = path, text = text), error = null) }
        autosaveEditor()
    }

    private fun autosaveEditor() {
        editorSave?.cancel()
        val r = repo ?: return
        val token = tokenProvider() ?: return
        val snapshotBranch = mutable.value.branch
        val snapshot = DraftBundle(mutable.value.changes, mutable.value.editor)
        editorSave = scope.launch {
            delay(600)
            try {
                withContext(Dispatchers.IO) { drafts.write(token, r.id, snapshotBranch, snapshot) }
            } catch (e: Exception) {
                mutable.update { it.copy(error = "Автосохранение не удалось: ${e.message}") }
            }
        }
    }


    fun saveDraft() {
        val edit = mutable.value.editor ?: return
        try {
            validateGitPath(edit.path)
            // Pressing Back on an untouched editor must not stage a no-op change.
            val unchanged = edit.originalPath == edit.path && edit.originalSha != null && edit.text == edit.originalText
            if (unchanged) {
                editorSave?.cancel()
                persist(mutable.value.changes.filterNot { it.path == edit.path }, editor = null)
                mutable.update { it.copy(notice = "Изменений нет — черновик не создан") }
                return
            }
            val changes = buildList {
                // Rename = new blob and delete old path, atomic in one Git tree.
                if (edit.originalPath != null && edit.originalPath != edit.path) {
                    add(RemoteChange(edit.originalPath, ChangeType.DELETE, originalSha = edit.originalSha, originalText = edit.originalText))
                    add(RemoteChange(edit.path, ChangeType.UPSERT, edit.text, null))
                } else {
                    add(RemoteChange(edit.path, ChangeType.UPSERT, edit.text, edit.originalSha, edit.originalText))
                }
            }
            // Persist editor closed and changes together, avoiding orphan transient buffers.
            editorSave?.cancel()
            val merged = applyDraftChanges(mutable.value.changes, changes)
            persist(merged, editor = null)
            mutable.update { it.copy(notice = "Черновик зашифрованно сохранён на устройстве") }
        } catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
    }

    fun stageDelete() {
        val edit = mutable.value.editor ?: return
        try {
            require(edit.originalPath != null && edit.originalSha != null) { "Новый файл ещё не существует" }
            editorSave?.cancel()
            val merged = applyDraftChanges(mutable.value.changes,
                listOf(RemoteChange(edit.originalPath, ChangeType.DELETE, originalSha = edit.originalSha, originalText = edit.originalText)))
            persist(merged, editor = null)
            mutable.update { it.copy(notice = "Удаление добавлено в черновики") }
        } catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
    }

    fun closeEditor() {
        editorSave?.cancel()
        try { persist(mutable.value.changes, editor = null) }
        catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
    }

    fun discardDraft(path: String) {
        try { persist(mutable.value.changes.filterNot { it.path == path }) }
        catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
    }

    fun commit(message: String) {
        if (mutable.value.busy || mutable.value.editor != null) return
        val r = repo ?: return
        val branch = mutable.value.branch
        val changes = mutable.value.changes
        scope.launch {
            mutable.update { it.copy(busy = true, error = null, notice = null) }
            try {
                val sha = remote.commit(tokenProvider() ?: error("Нет токена"), r.owner.login, r.name,
                    branch, changes, message)
                if (repo?.id == r.id && mutable.value.branch == branch) {
                    persist(emptyList())
                    mutable.update { it.copy(notice = "Создан коммит ${sha.take(12)} в $branch") }
                    onCommitted()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "Ошибка GitHub") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
