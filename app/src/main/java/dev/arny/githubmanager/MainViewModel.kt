package dev.arny.githubmanager

import android.util.Base64
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.arny.githubmanager.domain.*
import dev.arny.githubmanager.security.SecureTokenStore
import dev.arny.githubmanager.security.EncryptedDraftStore
import dev.arny.githubmanager.remote.RemoteGitHubApi
import dev.arny.githubmanager.remote.RemoteWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_FILE_BYTES = 2_000_000L

enum class Screen { REPOSITORIES, DETAILS }
enum class DetailTab { OVERVIEW, FILES, CHANGES, RELEASES }

data class BrowserState(
    val directoryPath: String = "",
    val entries: List<GitHubContent> = emptyList(),
    val fileName: String? = null,
    val fileBody: String? = null,
    val fileBytes: ByteArray? = null,
    val fileKind: FilePreviewKind = FilePreviewKind.TEXT,
    val fileSize: Long = 0,
    val fileUrl: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

data class DownloadState(val assetId: Long, val fileName: String, val bytes: Long, val total: Long)

data class ReleaseState(
    val entries: List<GitHubRelease> = emptyList(), val page: Int = 0, val hasMore: Boolean = true,
    val loading: Boolean = false, val error: String? = null
)

data class AppState(
    val hasSession: Boolean = false,
    val profile: GitHubUser? = null,
    val repos: List<GitHubRepository> = emptyList(),
    val screen: Screen = Screen.REPOSITORIES,
    val selected: GitHubRepository? = null,
    val detailTab: DetailTab = DetailTab.OVERVIEW,
    val browser: BrowserState = BrowserState(),
    val releases: ReleaseState = ReleaseState(),
    val download: DownloadState? = null,
    val filter: VisibilityFilter = VisibilityFilter.ALL,
    val search: String = "",
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val loggingIn: Boolean = false,
    val mutating: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

class MainViewModel(
    private val api: GitHubService,
    private val tokens: SecureTokenStore,
    private val drafts: EncryptedDraftStore,
    private val remoteApi: RemoteGitHubApi
) : ViewModel() {
    private var token: String? = tokens.read()

    val remote = RemoteWorkspace(remoteApi, drafts, { token }, viewModelScope,
        onCommitted = { api.clearCache(); refreshBrowser() })
    private val manager = RepositoryManager(api)
    private var browserJob: Job? = null
    private var releaseJob: Job? = null
    private var downloadJob: Job? = null
    private val mutable = MutableStateFlow(AppState(hasSession = token != null))
    val state = mutable.asStateFlow()

    init { if (token != null) reconnect() }

    fun login(userToken: String) {
        if (mutable.value.loggingIn) return
        val input = userToken.trim()
        if (!Regex("^[A-Za-z0-9_-]{10,1024}$").matches(input)) {
            mutable.update { it.copy(error = "Некорректный формат токена GitHub.") }
            return
        }
        viewModelScope.launch {
            mutable.update { it.copy(loggingIn = true, error = null, notice = null) }
            try {
                val user = api.currentUser(input)
                tokens.save(input)
                token = input
                mutable.update {
                    AppState(hasSession = true, profile = user, loading = true)
                }
                loadPage(clear = true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { it.copy(loggingIn = false, error = safeMessage(error)) }
            } finally {
                mutable.update { it.copy(loggingIn = false) }
            }
        }
    }

    fun reconnect() {
        val active = token ?: return
        viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val user = api.currentUser(active)
                mutable.update { it.copy(profile = user, hasSession = true) }
                loadPage(clear = true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (error is GitHubApiException && error.statusCode == 401) {
                    logout()
                    mutable.update { it.copy(error = "Срок действия токена истёк. Войдите снова.") }
                } else {
                    mutable.update { it.copy(error = safeMessage(error)) }
                }
            } finally {
                mutable.update { it.copy(loading = false) }
            }
        }
    }

    fun logout() {
        remote.logout()
        viewModelScope.coroutineContext.cancelChildren()
        browserJob = null
        releaseJob = null
        downloadJob = null
        api.clearCache()
        tokens.clear() // Removes local credentials; the token must be revoked on github.com separately.
        token = null
        mutable.value = AppState()
    }

    fun setSearch(search: String) { mutable.update { it.copy(search = search) } }
    fun setFilter(filter: VisibilityFilter) { mutable.update { it.copy(filter = filter) } }
    fun dismissMessages() { mutable.update { it.copy(error = null, notice = null) } }

    fun refresh() { refreshRepositories(force = true) }

    private fun refreshRepositories(force: Boolean) {
        if (mutable.value.loading) return
        viewModelScope.launch { loadPage(clear = true, force = force) }
    }

    fun refreshCurrent() {
        if (mutable.value.screen == Screen.REPOSITORIES) { refresh(); return }
        refreshDetails(forceRefresh = true)
        when (mutable.value.detailTab) {
            DetailTab.FILES -> refreshBrowser()
            DetailTab.RELEASES -> loadReleases(reset = true, forceRefresh = true)
            DetailTab.OVERVIEW, DetailTab.CHANGES -> Unit
        }
    }

    fun clearCacheAndRefresh() {
        api.clearCache()
        if (mutable.value.screen == Screen.REPOSITORIES) refresh()
        else refreshCurrent()
        mutable.update { it.copy(notice = "Локальный кэш очищен. Загружаем данные с GitHub.") }
    }

    fun loadMore() {
        val state = mutable.value
        if (state.loading || !state.hasMore || state.profile == null) return
        viewModelScope.launch { loadPage(clear = false) }
    }

    private suspend fun loadPage(clear: Boolean, force: Boolean = false) {
        val active = token ?: return
        val page = if (clear) 1 else mutable.value.page + 1
        mutable.update { it.copy(loading = true, error = null) }
        try {
            val result = api.listRepositories(active, page, forceRefresh = force)
            mutable.update { previous ->
                previous.copy(
                    repos = if (clear) result.entries else
                        (previous.repos + result.entries).distinctBy { it.id },
                    page = page,
                    hasMore = result.hasMore,
                    loading = false
                )
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            mutable.update { it.copy(error = safeMessage(error), loading = false) }
        }
    }

    fun openRepo(repoId: Long) {
        browserJob?.cancel()
        releaseJob?.cancel()
        val selected = mutable.value.repos.firstOrNull { it.id == repoId } ?: return
        remote.open(selected)
        mutable.update {
            it.copy(
                screen = Screen.DETAILS, selected = selected,
                detailTab = DetailTab.FILES, browser = BrowserState(), releases = ReleaseState(),
                error = null, notice = null
            )
        }
        refreshDetails()
        loadDirectory("")
    }

    fun backToRepos() {
        remote.reset()
        browserJob?.cancel()
        releaseJob?.cancel()
        mutable.update { it.copy(screen = Screen.REPOSITORIES, selected = null, browser = BrowserState()) }
    }

    fun refreshDetails(forceRefresh: Boolean = false) {
        val active = token ?: return
        val selected = mutable.value.selected ?: return
        viewModelScope.launch {
            try {
                val fresh = api.getRepository(active, selected.owner.login, selected.name, forceRefresh = forceRefresh)
                mutable.update { current ->
                    current.copy(
                        selected = if (current.selected?.id == fresh.id) fresh else current.selected,
                        repos = current.repos.map { old -> if (old.id == fresh.id) fresh else old }
                    )
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = safeMessage(error)) } }
        }
    }

    fun setDetailTab(tab: DetailTab) {
        val browser = mutable.value.browser
        mutable.update { it.copy(detailTab = tab) }
        if (tab == DetailTab.RELEASES && mutable.value.releases.page == 0 && !mutable.value.releases.loading) {
            loadReleases(reset = true)
        }
        // Returning to «Код» must restore the open file or directory, not reset it to the repository root.
        if (tab == DetailTab.FILES && browser.fileName == null && browser.entries.isEmpty() &&
            browser.error == null && !browser.loading && mutable.value.browser.fileName == null
        ) loadDirectory(browser.directoryPath)
    }

    fun loadDirectory(path: String, forceRefresh: Boolean = false) {
        val active = token ?: return
        val selected = mutable.value.selected ?: return
        browserJob?.cancel()
        browserJob = viewModelScope.launch {
            mutable.update {
                it.copy(browser = BrowserState(directoryPath = path, loading = true))
            }
            try {
                when (val result = api.getContentAtRef(active, selected.owner.login, selected.name, path, remote.state.value.branch, forceRefresh = forceRefresh)) {
                    is DirectoryResult.Directory -> mutable.update {
                        it.copy(browser = BrowserState(
                            directoryPath = path,
                            entries = result.entries.sortedWith(compareBy<GitHubContent> { it.type != "dir" }.thenBy { it.name.lowercase() })
                        ))
                    }
                    is DirectoryResult.File -> showFile(active, selected.owner.login, selected.name, result.file, path)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update {
                    it.copy(browser = BrowserState(directoryPath = path, error = safeMessage(error)))
                }
            }
        }
    }

    fun openContent(content: GitHubContent) {
        if (content.type == "dir") {
            loadDirectory(content.path)
        } else {
            val active = token ?: return
            val selected = mutable.value.selected ?: return
            if (content.size > MAX_FILE_BYTES) {
                mutable.update { it.copy(browser = it.browser.copy(error = "Файл больше ${MAX_FILE_BYTES / 1024 / 1024} МБ. Откройте его на GitHub.")) }
                return
            }
            browserJob?.cancel()
            browserJob = viewModelScope.launch {
                mutable.update { it.copy(browser = it.browser.copy(loading = true, error = null)) }
                try {
                    when (val result = api.getContentAtRef(active, selected.owner.login, selected.name, content.path, remote.state.value.branch)) {
                        is DirectoryResult.File -> showFile(active, selected.owner.login, selected.name, result.file, content.path)
                        is DirectoryResult.Directory -> loadDirectory(content.path)
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    mutable.update { it.copy(browser = it.browser.copy(loading = false, error = safeMessage(error))) }
                }
            }
        }
    }

    private suspend fun showFile(token: String, owner: String, repo: String, file: GitHubContent, path: String) {
        val inline = runCatching {
            if (file.encoding == "base64" && file.content != null) Base64.decode(file.content, Base64.DEFAULT) else null
        }.getOrNull()
        val bytes = inline ?: api.getRawContentAtRef(token, owner, repo, path, remote.state.value.branch, MAX_FILE_BYTES)
        val kind = previewKindFor(file.name, bytes)
        val text = if (kind == FilePreviewKind.TEXT || kind == FilePreviewKind.MARKDOWN) {
            bytes.toString(Charsets.UTF_8)
        } else null
        mutable.update {
            it.copy(browser = BrowserState(
                directoryPath = path.substringBeforeLast('/', ""),
                fileName = file.name, fileBody = text, fileBytes = bytes, fileKind = kind,
                fileSize = file.size, fileUrl = file.htmlUrl
            ))
        }
    }

    fun refreshBrowser() {
        val b = mutable.value.browser
        if (b.fileName == null) loadDirectory(b.directoryPath, forceRefresh = true)
        else {
            val fullPath = listOf(b.directoryPath, b.fileName).filter { it.isNotEmpty() }.joinToString("/")
            val active = token ?: return
            val selected = mutable.value.selected ?: return
            browserJob?.cancel()
            browserJob = viewModelScope.launch {
                mutable.update { it.copy(browser = it.browser.copy(loading = true, error = null)) }
                try {
                    when (val result = api.getContentAtRef(active, selected.owner.login, selected.name, fullPath, remote.state.value.branch, forceRefresh = true)) {
                        is DirectoryResult.File -> showFile(active, selected.owner.login, selected.name, result.file, fullPath)
                        is DirectoryResult.Directory -> loadDirectory(fullPath, forceRefresh = true)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.update { it.copy(browser = it.browser.copy(loading = false, error = safeMessage(e))) } }
            }
        }
    }

    fun loadReleases(reset: Boolean = false, forceRefresh: Boolean = false) {
        val active = token ?: return
        val repo = mutable.value.selected ?: return
        if (mutable.value.releases.loading && !(reset && forceRefresh)) return
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch {
            val page = if (reset) 1 else mutable.value.releases.page + 1
            mutable.update { it.copy(releases = it.releases.copy(loading = true, error = null)) }
            try {
                val result = api.listReleases(active, repo.owner.login, repo.name, page, forceRefresh)
                mutable.update { old ->
                    if (old.selected?.id != repo.id) old else old.copy(releases = ReleaseState(
                        entries = if (reset) result.entries else (old.releases.entries + result.entries).distinctBy { it.id },
                        page = page, hasMore = result.hasMore))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(releases = it.releases.copy(loading = false, error = safeMessage(e))) } }
        }
    }

    fun saveReleaseAsset(asset: GitHubReleaseAsset, uri: Uri, resolver: ContentResolver) {
        val active = token ?: return
        val repo = mutable.value.selected ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            mutable.update { it.copy(download = DownloadState(asset.id, asset.name, 0L, asset.size), error = null) }
            try {
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(uri, "w") ?: throw java.io.IOException("Невозможно открыть выбранный документ")
                    output.use { destination ->
                        var lastProgress = 0L
                        api.downloadAsset(active, repo.owner.login, repo.name, asset, destination) { bytes, total ->
                            if (bytes - lastProgress >= 256 * 1024 || bytes == total) {
                                lastProgress = bytes
                                mutable.update { it.copy(download = DownloadState(asset.id, asset.name, bytes, total)) }
                            }
                        }
                    }
                }
                mutable.update { it.copy(download = null, notice = "Файл ${asset.name} сохранён. SHA-256 проверен, если GitHub предоставил digest.") }
            } catch (e: CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
                mutable.update { it.copy(download = null, error = "Не удалось сохранить ${asset.name}: ${safeMessage(e)}") }
            } finally { mutable.update { it.copy(download = null) } }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    fun browserBack() {
        val browser = mutable.value.browser
        if (browser.fileName != null) loadDirectory(browser.directoryPath)
        else if (browser.directoryPath.isNotEmpty()) {
            loadDirectory(browser.directoryPath.substringBeforeLast('/', ""))
        }
    }

    fun setVisibility(makePrivate: Boolean) {
        val active = token ?: return
        val selected = mutable.value.selected ?: return
        if (mutable.value.mutating || !canChangeVisibility(selected) || selected.private == makePrivate) return
        viewModelScope.launch {
            mutable.update { it.copy(mutating = true, error = null, notice = null) }
            try {
                val changed = manager.setPrivate(active, selected, makePrivate)
                mutable.update { current ->
                    current.copy(
                        selected = if (current.selected?.id == changed.id) changed else current.selected,
                        repos = current.repos.map { old -> if (old.id == changed.id) changed else old },
                        notice = "GitHub подтвердил новую видимость: ${if (changed.private) "Private" else "Public"}."
                    )
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { it.copy(error = safeMessage(error)) }
            } finally {
                mutable.update { it.copy(mutating = false) }
            }
        }
    }

    private fun safeMessage(error: Exception): String =
        when (error) {
            is GitHubApiException, is VisibilityConflict -> error.message ?: "Ошибка GitHub"
            is IllegalArgumentException -> error.message ?: "Некорректный запрос"
            else -> "Операция не выполнена: ${error.message?.take(120) ?: "проверьте соединение"}"
        }
}
