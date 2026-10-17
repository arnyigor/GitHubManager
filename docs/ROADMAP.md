> Note: This document started with 0.1.0. For the 0.2.0 UI and code viewer, see [`UI_REDESIGN.md`](UI_REDESIGN.md).

# Разработка — последовательный план

| Этап | Результат | Критерии приёмки |
|---|---|---|
| M0 (в архиве) | Android Compose MVP, PAT, repos, files, visibility | unit test политики; сборка APK; manual smoke |
| M1 | device OAuth / GitHub App, token lifecycle, multi-account | отозванный токен; expired token; безопасный re-auth; no secrets in APK |
| M2 | Paging 3 + Room KMP cache + offline display | 1000+ repo без рывков; кэш и ETag; отсутствие дублей |
| M3 | Code browser: markdown, images, search, ветки и diffs | большие каталоги, binary files, branch switch, UTF-8 |
| M4 | Issues/PR: список, фильтры, review, комментарии | проверка permissions; pagination; error recovery |
| M5 | Actions и releases, notifications | workflow dispatch с подтверждением, run logs, rate limits |
| M6 | Security hardening, audit UI, accessibility | input validation, tokens, redaction, rotation, WCAG, CI |
| M7 | Desktop via KMP shared modules | единый domain, отдельные secure stores, независимые UI tests |

Каждый этап: ADR -> задача -> тесты -> code review -> PR -> commit. Не смешивать большой рефакторинг с добавлением нескольких функций за один PR.

## Следующая работа — приоритет M0

1. Собрать Debug на Windows + Android Studio (SDK 36 / Gradle 8.13).
2. Прогнать `testDebugUnitTest` и исправить версионные/компиляционные несоответствия.
3. Проверить read-only PAT и отдельно PAT с Administration write на **тестовом репозитории**.
4. Проверить Public -> Private и Private -> Public с учётом реальных последствий GitHub; не использовать важный репозиторий.
5. Провести тестирование долгой сети, отмены, 401, 403, 404, 422, 429 и ротации устройства.
6. Только после smoke test публиковать debug APK.
