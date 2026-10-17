package dev.arny.githubmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arny.githubmanager.*
import dev.arny.githubmanager.domain.*
import dev.arny.githubmanager.ui.code.CodeViewer
import dev.arny.githubmanager.ui.markdown.MarkdownViewer
import dev.arny.githubmanager.domain.isMarkdownFile
import dev.hossain.highlight.ui.HighlightThemeProvider
import dev.hossain.highlight.ui.rememberTomorrowNightTheme
import dev.hossain.highlight.ui.rememberTomorrowLightTheme

private val NightBg = Color(0xFF0D1117)
private val NightSurface = Color(0xFF161B22)
private val NightBorder = Color(0xFF30363D)
private val DayBg = Color(0xFFF6F8FA)
private val DayBorder = Color(0xFFD0D7DE)
private val Accent = Color(0xFF58A6FF)

private val NightScheme = darkColorScheme(
    primary = Accent, onPrimary = NightBg,
    background = NightBg, onBackground = Color(0xFFE6EDF3),
    surface = NightSurface, onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF212830), onSurfaceVariant = Color(0xFF9CA9B6),
    outline = NightBorder, outlineVariant = NightBorder,
    secondaryContainer = Color(0xFF183653), onSecondaryContainer = Color(0xFFD2E8FF),
    errorContainer = Color(0xFF472226), onErrorContainer = Color(0xFFFFC3C3)
)
private val DayScheme = lightColorScheme(
    primary = Color(0xFF0969DA), onPrimary = Color.White,
    background = DayBg, onBackground = Color(0xFF1F2328),
    surface = Color.White, onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFF1F3F6), onSurfaceVariant = Color(0xFF57606A),
    outline = DayBorder, outlineVariant = DayBorder,
    secondaryContainer = Color(0xFFDDF1FF), onSecondaryContainer = Color(0xFF0969DA)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubManagerApp(state: AppState, vm: MainViewModel) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val uiPrefs = remember(context) {
        context.applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    }
    var darkTheme by rememberSaveable {
        mutableStateOf(uiPrefs.getBoolean("dark_theme", systemDark))
    }
    var showMenu by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = if (darkTheme) NightScheme else DayScheme) {
        HighlightThemeProvider(
            darkTheme = darkTheme,
            lightHighlightTheme = rememberTomorrowLightTheme(),
            darkHighlightTheme = rememberTomorrowNightTheme()
        ) {
            val browser = state.browser
            val canGoBrowserBack = state.detailTab == DetailTab.FILES &&
                (browser.fileName != null || browser.directoryPath.isNotEmpty())
            BackHandler(state.screen == Screen.DETAILS) {
                if (canGoBrowserBack) vm.browserBack() else vm.backToRepos()
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(if (state.screen == Screen.DETAILS) state.selected?.name ?: "Репозиторий" else "GitHub Manager",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        },
                        navigationIcon = {
                            if (state.screen == Screen.DETAILS) IconButton(onClick = {
                                if (canGoBrowserBack) vm.browserBack() else vm.backToRepos()
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                        },
                        actions = {
                            if (state.screen == Screen.DETAILS) {
                                val repoUrl = state.selected?.htmlUrl
                                if (repoUrl != null) IconButton(onClick = { shareRepository(context, repoUrl) }) {
                                    Icon(Icons.Default.Share, "Поделиться ссылкой на репозиторий")
                                }
                            }
                            if (state.hasSession) {
                                IconButton(onClick = vm::refreshCurrent) {
                                    Icon(Icons.Default.Refresh, "Обновить с GitHub без кэша")
                                }
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, "Дополнительные действия")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (darkTheme) "Светлая тема" else "Тёмная тема") },
                                        leadingIcon = {
                                            Icon(if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                                        },
                                        onClick = {
                                            showMenu = false
                                            darkTheme = !darkTheme
                                            uiPrefs.edit().putBoolean("dark_theme", darkTheme).apply()
                                        }
                                    )
                                    if (state.hasSession) {
                                        DropdownMenuItem(text = { Text("Очистить кэш и обновить") },
                                            onClick = { showMenu = false; vm.clearCacheAndRefresh() })
                                        if (state.screen == Screen.REPOSITORIES) {
                                            DropdownMenuItem(
                                                text = { Text("Выйти из аккаунта") },
                                                leadingIcon = { Icon(Icons.Default.Logout, null) },
                                                enabled = !state.mutating,
                                                onClick = { showMenu = false; vm.logout() }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    state.error?.let { MessageStrip(it, true, vm::dismissMessages) }
                    state.notice?.let { MessageStrip(it, false, vm::dismissMessages) }
                    when {
                        !state.hasSession -> LoginScreen(state.loggingIn, vm::login)
                        state.profile == null -> ReconnectionScreen(state.loading, vm::reconnect, vm::logout)
                        state.screen == Screen.DETAILS && state.selected != null -> DetailScreen(state, vm)
                        else -> RepositoryScreen(state, vm)
                    }
                }
            }
        }
    }
}

/** Opens the system share sheet with the repository web URL. */
private fun shareRepository(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, "Поделиться репозиторием"))
}

@Composable
private fun MessageStrip(text: String, isError: Boolean, dismiss: () -> Unit) {
    Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
            IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "Закрыть", modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun LoginScreen(busy: Boolean, login: (String) -> Unit) {
    var pat by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Default.Terminal, null, Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text("Весь GitHub. Без браузера.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Репозитории, исходники и управление доступом — в одном мобильном инструменте.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Text("GitHub access token", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = pat, onValueChange = { pat = it }, singleLine = true,
            placeholder = { Text("github_pat_…") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(12.dp))
        Button(enabled = !busy && pat.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp),
            onClick = { login(pat) }, shape = RoundedCornerShape(12.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Продолжить")
        }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new")))
        }) { Text("Создать fine-grained token на GitHub") }
        Spacer(Modifier.height(12.dp))
        Text("Токен хранится зашифрованным в Android Keystore. Для управления видимостью нужны права Administration: write.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReconnectionScreen(loading: Boolean, retry: () -> Unit, logout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) CircularProgressIndicator() else {
            Icon(Icons.Default.WifiOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Text("GitHub недоступен", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = retry) { Text("Повторить") }
            TextButton(onClick = logout) { Text("Сменить токен") }
        }
    }
}

@Composable
private fun RepositoryScreen(state: AppState, vm: MainViewModel) {
    val shown = remember(state.repos, state.search, state.filter) {
        filterRepositories(state.repos, state.search, state.filter)
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)) {
            Text("ВАШЕ РАБОЧЕЕ ПРОСТРАНСТВО", style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(5.dp))
            Text("Репозитории", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("@${state.profile?.login ?: "—"}  ·  Загружено ${state.repos.size}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(value = state.search, onValueChange = vm::setSearch,
                placeholder = { Text("Найти репозиторий…") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(VisibilityFilter.ALL to "Все", VisibilityFilter.PUBLIC to "Public", VisibilityFilter.PRIVATE to "Private")
                    .forEach { (filter, label) ->
                        FilterChip(selected = state.filter == filter, onClick = { vm.setFilter(filter) },
                            label = { Text(label) }, shape = RoundedCornerShape(10.dp))
                    }
            }
            Text("В списке: ${shown.size} · Поиск только по загруженным", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.loading && state.page == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (shown.isEmpty()) item {
                    EmptyPanel("Ничего не найдено", "Измените запрос или фильтр. Возможно, нужно загрузить следующую страницу.")
                }
                items(shown, key = { it.id }) { repo ->
                    RepositoryRow(repo, onClick = { vm.openRepo(repo.id) })
                }
                if (state.hasMore) item {
                    OutlinedButton(onClick = vm::loadMore, enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(10.dp)) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Загрузить следующие 50")
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoryRow(repo: GitHubRepository, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(Icons.Default.Folder, null, Modifier.padding(11.dp).size(23.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(repo.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    VisibilityPill(repo.private)
                }
                Text(repo.owner.login, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (!repo.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(repo.description, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    repo.language?.let { LanguageLabel(it) }
                    Text(repo.updatedAt?.take(10) ?: "—", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VisibilityPill(isPrivate: Boolean) {
    Row(
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(if (isPrivate) Icons.Default.Lock else Icons.Default.Public, null, Modifier.size(11.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (isPrivate) "Private" else "Public", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LanguageLabel(language: String) {
    val dot = when (language.lowercase()) {
        "kotlin" -> Color(0xFFAD79EE)
        "javascript" -> Color(0xFFF1E05A)
        "typescript" -> Color(0xFF3178C6)
        "python" -> Color(0xFF3572A5)
        "java" -> Color(0xFFB07219)
        "rust" -> Color(0xFFDEA584)
        "c++" -> Color(0xFFF34B7D)
        else -> Color(0xFF8B949E)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyPanel(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inventory2, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(state: AppState, vm: MainViewModel) {
    val repo = state.selected ?: return
    var confirmTarget by remember(repo.id, repo.private) { mutableStateOf<Boolean?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(repo.owner.login + " /", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(repo.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                VisibilityPill(repo.private)
            }
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(repo.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        val context = LocalContext.current
        var pendingAsset by remember(repo.id) { mutableStateOf<GitHubReleaseAsset?>(null) }
        val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val asset = pendingAsset
            pendingAsset = null
            if (uri != null && asset != null) vm.saveReleaseAsset(asset, uri, context.contentResolver)
        }
        PrimaryTabRow(selectedTabIndex = when (state.detailTab) {
            DetailTab.FILES -> 0; DetailTab.RELEASES -> 1; DetailTab.OVERVIEW -> 2
        }, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = state.detailTab == DetailTab.FILES, onClick = { vm.setDetailTab(DetailTab.FILES) },
                text = { Text("Код") }, icon = { Icon(Icons.Default.Code, null, Modifier.size(17.dp)) })
            Tab(selected = state.detailTab == DetailTab.RELEASES, onClick = { vm.setDetailTab(DetailTab.RELEASES) },
                text = { Text("Релизы") }, icon = { Icon(Icons.Default.Inventory2, null, Modifier.size(17.dp)) })
            Tab(selected = state.detailTab == DetailTab.OVERVIEW, onClick = { vm.setDetailTab(DetailTab.OVERVIEW) },
                text = { Text("Настройки") }, icon = { Icon(Icons.Default.Settings, null, Modifier.size(17.dp)) })
        }
        when (state.detailTab) {
            DetailTab.FILES -> FileBrowser(state.browser, vm)
            DetailTab.RELEASES -> ReleasesScreen(state.releases, state.download,
                refresh = { vm.loadReleases(reset = true, forceRefresh = true) },
                more = { vm.loadReleases() },
                save = { asset -> pendingAsset = asset; createDocument.launch(asset.name) },
                cancel = vm::cancelDownload)
            DetailTab.OVERVIEW -> SettingsScreen(repo, state.mutating) { confirmTarget = it }
        }
    }
    confirmTarget?.let { target ->
        ConfirmVisibilityDialog(repo, target,
            onConfirm = { confirmTarget = null; vm.setVisibility(target) },
            onDismiss = { confirmTarget = null })
    }
}

@Composable
private fun SettingsScreen(repo: GitHubRepository, mutating: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Общая информация", fontWeight = FontWeight.SemiBold)
                Text("Последнее обновление · ${repo.updatedAt?.take(10) ?: "—"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                repo.language?.let { LanguageLabel(it) }
                OutlinedButton(onClick = {
                    if (repo.htmlUrl.startsWith("https://github.com/"))
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl)))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.OpenInBrowser, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть на GitHub")
                }
            }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Видимость репозитория", fontWeight = FontWeight.SemiBold)
                VisibilityPill(repo.private)
                Text("Изменение видимости может повлиять на форки, GitHub Actions и доступность кода.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (canChangeVisibility(repo)) {
                    Button(enabled = !mutating, onClick = { onChange(!repo.private) },
                        modifier = Modifier.fillMaxWidth()) {
                        if (mutating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (repo.private) "Сделать публичным" else "Сделать приватным")
                    }
                } else {
                    Text("Нужны права Admin. Форки, архивные и Internal-репозитории не изменяются.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Перед изменением приложение повторно проверяет права и состояние репозитория.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FileBrowser(browser: BrowserState, vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (browser.fileName != null || browser.directoryPath.isNotEmpty()) {
                IconButton(onClick = vm::browserBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Уровень выше") }
            } else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (browser.fileName != null) "ФАЙЛ" else "ДЕРЕВО ФАЙЛОВ",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(if (browser.fileName != null) browser.fileName else
                    if (browser.directoryPath.isEmpty()) "/" else "/${browser.directoryPath}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            if (browser.fileName == null) IconButton(onClick = vm::refreshBrowser) {
                Icon(Icons.Default.Refresh, "Обновить папку")
            }
        }
        if (browser.directoryPath.isNotEmpty()) {
            val segments = remember(browser.directoryPath) {
                browser.directoryPath.split('/').filter { it.isNotEmpty() }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Корень", Modifier.clickable { vm.loadDirectory("") },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                segments.forEachIndexed { index, segment ->
                    Text(" / ", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(segment,
                        modifier = Modifier.clickable {
                            vm.loadDirectory(segments.take(index + 1).joinToString("/"))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        browser.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        when {
            browser.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            browser.fileName != null -> FileContentPanel(browser)
            browser.entries.isEmpty() && browser.error != null -> Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Не удалось загрузить каталог", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = vm::refreshBrowser) { Text("Повторить") }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (browser.entries.isEmpty()) item {
                    EmptyPanel("Папка пуста", "В ней нет доступных файлов.")
                }
                items(browser.entries, key = { it.path }) { entry ->
                    Row(Modifier.fillMaxWidth().clickable { vm.openContent(entry) }.padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(if (entry.type == "dir") Icons.Default.Folder else Icons.Default.Description, null,
                            tint = if (entry.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(if (entry.type == "dir") "Папка" else "${entry.size} байт",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ConfirmVisibilityDialog(repo: GitHubRepository, targetPrivate: Boolean,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember(repo.id, targetPrivate) { mutableStateOf("") }
    var acknowledged by remember(repo.id, targetPrivate) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(if (targetPrivate) "Сделать Private?" else "Опубликовать репозиторий?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (targetPrivate)
                    "Публичные форки останутся публичными. Pages и настройки доступа могут измениться."
                else "Исходный код и история GitHub Actions станут публичными.")
                Text("Введите ${repo.fullName} для подтверждения:", fontWeight = FontWeight.Medium)
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text("Понимаю последствия", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(enabled = acknowledged && typed.trim() == repo.fullName, onClick = onConfirm) {
            Text("Подтвердить")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}


@Composable
private fun FileContentPanel(browser: BrowserState) {
    val name = browser.fileName ?: return
    val isMarkdown = isMarkdownFile(name)
    var preview by remember(name, browser.directoryPath) { mutableStateOf(isMarkdown) }
    Column(Modifier.fillMaxSize()) {
        if (isMarkdown) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Markdown", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                FilterChip(selected = preview, onClick = { preview = true }, label = { Text("Просмотр") })
                FilterChip(selected = !preview, onClick = { preview = false }, label = { Text("Исходник") })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (isMarkdown && preview) {
            MarkdownViewer(browser.fileBody.orEmpty(), modifier = Modifier.fillMaxSize())
        } else {
            CodeViewer(fileName = name, filePath = browser.directoryPath,
                code = browser.fileBody.orEmpty(), url = browser.fileUrl)
        }
    }
}

@Composable
private fun ReleasesScreen(
    state: ReleaseState,
    download: DownloadState?,
    refresh: () -> Unit,
    more: () -> Unit,
    save: (GitHubReleaseAsset) -> Unit,
    cancel: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Релизы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Версии, заметки и файлы для загрузки", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = refresh, enabled = !state.loading) {
                    Icon(Icons.Default.Refresh, "Обновить релизы без кэша")
                }
            }
        }
        if (state.error != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = refresh) { Text("Повторить запрос") }
            }
        }
        if (state.loading && state.entries.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (!state.loading && state.entries.isEmpty() && state.error == null) item {
            EmptyPanel("Релизов пока нет", "Репозиторий может содержать теги без опубликованных Releases.")
        }
        items(state.entries, key = { it.id }) { release ->
            ReleaseCard(release, download, save, cancel)
        }
        if (state.hasMore && state.page > 0) item {
            OutlinedButton(onClick = more, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.loading) "Загрузка…" else "Следующие релизы")
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: GitHubRelease, download: DownloadState?,
    save: (GitHubReleaseAsset) -> Unit, cancel: () -> Unit) {
    var notesExpanded by remember(release.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(release.name?.ifBlank { release.tagName } ?: release.tagName,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                if (release.prerelease) Text("Pre-release", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                if (release.draft) Text("Draft", style = MaterialTheme.typography.labelSmall)
            }
            Text("${release.tagName}  ·  ${release.publishedAt?.take(10) ?: "Без даты"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            release.body?.takeIf { it.isNotBlank() }?.let { body ->
                TextButton(onClick = { notesExpanded = !notesExpanded }) {
                    Text(if (notesExpanded) "Свернуть описание" else "Показать описание релиза")
                    Icon(if (notesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                if (notesExpanded) {
                    MarkdownViewer(body, modifier = Modifier.fillMaxWidth(), scrollable = false)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Файлы (${release.assets.size})", style = MaterialTheme.typography.labelLarge)
            if (release.assets.isEmpty()) {
                Text("Прикреплённых файлов нет", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            release.assets.forEach { asset ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(asset.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("${formatAssetSize(asset.size)} · ${asset.downloadCount} скачиваний",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { save(asset) }, enabled = download == null) {
                            Icon(Icons.Default.Download, "Скачать ${asset.name}")
                        }
                    }
                    if (download?.assetId == asset.id) {
                        if (download.total > 0) {
                            LinearProgressIndicator(
                                progress = { (download.bytes.toFloat() / download.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${formatAssetSize(download.bytes)} / ${if (download.total > 0) formatAssetSize(download.total) else "размер неизвестен"}",
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = cancel) { Text("Отмена") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatAssetSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 * 1024 -> "%.1f ГБ".format(java.util.Locale.US, bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> "%.1f МБ".format(java.util.Locale.US, bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.1f КБ".format(java.util.Locale.US, bytes / 1024.0)
    else -> "$bytes Б"
}
