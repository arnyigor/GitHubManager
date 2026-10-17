> Note: This document started with 0.1.0. For the 0.2.0 UI and code viewer, see [`UI_REDESIGN.md`](UI_REDESIGN.md).

# Архитектура GitHub Manager

## Смысл продукта

Мобильный GitHub-клиент с первоклассным управлением репозиториями. Android first, offline-aware в будущем, без обязательного собственного backend. TaskBridge никак не задействован.

## Требования

- Простые навигация и визуальная иерархия: список -> репозиторий -> вкладки.
- Безопасность токенов; по умолчанию только чтение, изменение — с явными полномочиями и подтверждением.
- Сетевое состояние не смешивается с навигационным. Одноразовые ошибки не изменяют данные оптимистически.
- Слои `ui -> ViewModel -> domain (GitHubService, RepositoryManager) -> api/security`.
- API-операции тестируемы через интерфейс и fake implementation.

## Функциональные модули и масштабирование

MVP — один Gradle-модуль для быстрой сборки и малой поверхности отказов. По мере развития вынести без изменения бизнес-логики:

```text
:app-android                      Compose + platform integrations
:core:model                       Kotlin Multiplatform DTO/Models
:core:domain                      Kotlin Multiplatform rules/usecases
:core:network                     Ktor (Android CIO/OkHttp, Desktop CIO)
:core:database                    Room KMP (cache)
:feature:repositories             state + list + settings
:feature:files                    browsing + content viewer
:feature:pull-requests            stage P1
:feature:actions                  stage P2
:core:auth                        platform secure storage expect/actual
```

В KMP нельзя механически переносить текущий `SecureTokenStore` и OkHttp implementation в commonMain. Сначала перенос DTO, интерфейсов и бизнес-правил; затем Ktor adapter и expect/actual для хранилищ.

## State & concurrency

`AppState` — immutable snapshot на StateFlow. Все сетевые действия в `viewModelScope` и `Dispatchers.IO`. Пока есть один активный вход; далее добавить редуктор событий и per-feature ViewModels. Пагинация по 50 и явное действие Load More исключают неконтролируемую автозагрузку. Список не обнуляется на сетевой ошибке refresh.

## Политика изменения видимости

```text
UX confirmation + type full repo name + acknowledgment
    -> RepositoryManager
    -> check last known permissions.admin/fork/archived/internal
    -> GET current repo (id, private, permissions)
    -> abort on stale data / changed permissions
    -> PATCH once visibility=public|private
    -> GET current repo
    -> display VERIFIED or UNKNOWN outcome
```

Мутация не повторяется автоматически. При нестабильной сети пользователь должен проверить результат. Проверка владельца организации и enterprise restrictions остаётся на GitHub API; ошибки 403/422 не обходятся.

## API contract

`GET /user`, `GET /user/repos`, `GET /repos/{owner}/{repo}`, `GET /repos/{owner}/{repo}/contents/{path}`, `PATCH /repos/{owner}/{repo}`.

Header: `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2026-03-10`, `Authorization: Bearer ...`. Для `user/repos` не смешивать `type` c `affiliation` / `visibility`, иначе GitHub возвращает 422.

## ADR

- 001: Android-only первый MVP вместо пяти платформ сразу. Сохраняем чистое Kotlin domain с адаптером API.
- 002: FG-PAT до появления безопасно зарегистрированной GitHub App с device flow. Не встраивать client_secret в мобильный APK.
- 003: REST first: дешевле по сложности, легко тестировать HTTP и права. GraphQL только там, где уменьшает roundtrip.
- 004: Запретить автоматический retry записей. Чтения позже — с ETag, Retry-After и отменой запросов.
- 005: Без DI-фреймворка на первом этапе, factory достаточно. Koin можно добавить с ростом числа фич.
