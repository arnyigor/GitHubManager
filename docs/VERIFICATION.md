# Verification — 0.3.0 (2026-10-17)

## UX-правки по аудиту (ветка `fix/0.3.1-ux`)

Из материалов аудита применены 8 точечных правок `ui/GitHubManagerApp.kt` и одна строка README
(скрипт `githubmanager_ui_patch.py` не запускался: он ожидает фрагмент с другой индентацией,
поэтому правки внесены вручную с проверкой diff):

1. Тема сохраняется в `SharedPreferences("ui_prefs")` и переживает перезапуск.
2. Верхняя панель разгружена: share, ↻, overflow. Тема, «Очистить кэш и обновить» и «Выйти из аккаунта» — в меню.
3. Введённый токен больше не очищается до результата входа: при ошибке его можно отправить повторно.
4. В файловом браузере появились кликабельные breadcrumbs (`Корень / … / текущий каталог`).
5. Ошибка каталога показывается отдельным экраном с кнопкой «Повторить», а не как «Папка пуста».
6. Ошибка списка релизов получила кнопку «Повторить запрос».
7. Пустое состояние релизов больше не показывается одновременно с ошибкой.
8. Для ассета с неизвестным размером — indeterminate-прогресс и подпись «размер неизвестен».

Проверено: `:app:compileDebugKotlin`, `:app:testDebugUnitTest` (16 тестов, 0 падений), `:app:assembleDebug` — успешно.
Не проверено: поведение на устройстве — на момент правок телефон был недоступен по adb (Wi-Fi ADB offline, экран выключен).

## Что пришлось изменить в архиве 0.3.0, чтобы он собрался

Архив 0.3.0 возвращает toolchain 0.1.0 (AGP 8.13.2 / Kotlin 2.3.21 / `compileSdk 36` /
`kotlinOptions`-строка) и пинует `compose-highlight 0.31.0`. Такой набор не собирается:
`kotlinOptions { jvmTarget = "17" }` — ошибка в Kotlin 2.3+, а `compose-highlight` начиная
с 0.29.0 требует Kotlin 2.4.x и Compose 1.12 (то есть AGP 9.1+ и `compileSdk 37`).
Поэтому исходники 0.3.0 наложены на уже проверенный toolchain 0.2.0:

| Файл | В архиве 0.3.0 | В проекте |
|---|---|---|
| `build.gradle.kts` | AGP 8.13.2, Kotlin 2.3.21 | AGP 9.4.1, `plugin.compose`/`plugin.serialization` 2.4.20, плагин `kotlin.android` не применяется (в AGP 9 Kotlin встроен) |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.13 | Gradle 9.6.1 |
| `app/build.gradle.kts` | `kotlinOptions`-строка, BOM 2026.05.01, `compileSdk 36`, highlight 0.31.0 | `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }`, BOM 2026.09.00, `compileSdk 37`, highlight 0.39.0 (проверен на устройстве в 0.2.0) |

Добавленные в 0.3.0 зависимости оставлены как в архиве: `com.mikepenz:multiplatform-markdown-renderer`
и `...-m3` версии 0.39.2 (метаданные Kotlin 2.3.0 — читаются компилятором 2.4.x).

`local.properties` создан в формате Android Studio (`sdk.dir=G\:\\Android\\Sdk`): при
неэкранированном варианте `lintDebug` падает с `PropertyEscape`.

## Выполнено на этой машине

- `:app:compileDebugKotlin` — BUILD SUCCESSFUL (предупреждения об устаревших API: `Icons.Filled.Logout`, `centerAlignedTopAppBarColors`).
- `:app:testDebugUnitTest` — **16 тестов, 0 падений**: `ClientPoliciesTest` 4, `ModelsParsingTest` 3, `RepositoryManagerTest` 5, `CodePagerTest` 4.
- `:app:assembleDebug` — BUILD SUCCESSFUL.
- `:app:assembleRelease` — BUILD SUCCESSFUL (R8 + `shrinkResources`, `lintVitalRelease` чист). APK не подписан.
- `:app:lintDebug` — 0 ошибок, 18 предупреждений.
- `adb install -r app-debug.apk` на OnePlus 6T (ONEPLUS A6013, Android 11, API 30) — Success, `versionName 0.3.0`, `versionCode 3`.
- После установки приложение запускается, процесс жив, `logcat -b crash` пуст.

## Smoke-тест на устройстве

ОдинPlus 6T, Android 11, APK 0.3.0 установлен поверх предыдущей версии (`-r`, токен и данные сохранены).

- Запуск: список репозиториев (`@arnyigor · Загружено 50`), поиск и фильтры отрисованы.
- Переход в репозиторий: заголовок, бейдж видимости, вкладки «Код / Релизы / Настройки», дерево файлов.
- Новая иконка share отображается в верхней панели экрана репозитория (слева от переключателя темы).
- `logcat -b crash` пуст после установки и навигации.

Вкладка «Релизы», Markdown-просмотр/«Исходник», нажатие share, скачивание ассета через SAF
и поведение кэша на устройстве **не прогонялись**: в момент проверки телефон использовался
пользователем, а до этого был заблокирован PIN-кодом.

## Не проверено

- Вкладка «Релизы»: список, пагинация, раскрытие release notes, статусы draft/prerelease.
- Скачивание ассетов: SAF-диалог, прогресс, отмена, удаление неполного файла, проверка SHA-256.
- Markdown-просмотр и переключатель «Просмотр / Исходник» на реальном `.md`.
- Кэш: TTL 10 минут, условный `If-None-Match`/304, fallback на устаревший кэш без сети, «очистить кэш и обновить».
- Ввод токена с нуля в 0.3.0, приватные репозитории, `PATCH` видимости, коды 403/404/422/429.
- Compose-инструментальные тесты, CI-workflow (он всё ещё рассчитывает на Gradle 8.13 и `platforms;android-36`).
- Подписанный release APK: `signingConfig` отсутствует.

---

## Проверка 0.2.0 (для истории)

- Собиралась и устанавливалась на тот же OnePlus 6T; на устройстве проверены загрузка репозитория, дерево файлов, код-вьюер с подсветкой, тёмная тема, поворот экрана, вкладка «Настройки».
- 12 unit-тестов, `assembleDebug`/`assembleRelease`, lint 0 ошибок.
- Тогда же был поднят toolchain: AGP 9.4.1 / Gradle 9.6.1 / Kotlin 2.4.20 / `compileSdk 37` / Compose BOM 2026.09.00.
