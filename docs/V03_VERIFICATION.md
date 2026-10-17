# v0.3: Changes, verification, known gaps

Date: 2026-10-17

## Scope

1. New repository **Releases** tab, paginated GitHub `GET /repos/{owner}/{repo}/releases`; Android Storage Access Framework export for release assets; no full-disk storage permission, streaming and optional checksum.
2. Native Compose Markdown preview of README and Markdown files, with source toggle; release body renderer.
3. On-device encrypted JSON cache, TTL 10 minutes, conditional ETag, network error stale fallback, force refresh bypass, cache clearing and mutation guardrails.

## Security invariants

- Release binary downloads originate only from `api.github.com`; credentials are not forwarded on redirects.
- Redirect targets must be HTTPS and have host `github.com` or a subdomain of `githubusercontent.com`.
- Asset downloads are streamed, not buffered into JVM memory.
- Expected size and optional SHA-256 are validated; failed downloads attempt SAF document deletion.
- Authenticated JSON is encrypted using Android Keystore AES-GCM, `noBackupFilesDir`, a SHA256 hash of token+URL for each cache key.
- Cache is cleared at logout and after repository writes. Repository mutation *preflight and postflight* force GET without cache.
- API errors such as 401 are not treated as offline cache hits.

## Verified locally

The following command was run with the environment's Kotlin CLI compiler and returned PASS:

```
kotlinc app/src/main/java/dev/arny/githubmanager/domain/ClientPolicies.kt /tmp/githubmanager_v03_checks.kt -include-runtime -d /tmp/githubmanager_v03_checks.jar
java -jar /tmp/githubmanager_v03_checks.jar
PASS: cache TTL, redirect host, markdown extension, SHA256 policy
```

The smoke harness was run locally outside the archive; corresponding JUnit assertions live in `ClientPoliciesTest.kt`.

## Acceptance test matrix for Android Studio / emulator

| # | Test | Expected |
|---|---|---|
| 1 | Build APK | `assembleDebug` succeeds; no Compose API version conflicts |
| 2 | Visit repo with releases | Tag, name, notes, assets appear; next page loads |
| 3 | Download public `.zip` | SAF document saved; size and digest match API |
| 4 | Download private release asset | Token scoped with `Contents: read` succeeds; no PAT in CDN request |
| 5 | Cancel large download | UI resets; partial SAF file deleted if provider allows |
| 6 | Open `README.md` | Native formatted Markdown; tables, lists and code fences visible |
| 7 | Press «Исходник» | Raw Markdown shown in code viewer with copy and line numbers |
| 8 | Navigate away and return within TTL | API GET cache hit (no new request) |
| 9 | Click ↻ | A new GitHub network GET is made, without ETag |
| 10 | Wait > 10 min | ETag validation occurs; 304 reuses existing body |
| 11 | Disable network | Cached GET content remains available; uncached resources fail clearly |
| 12 | Switch repository to Private | Preflight+verify always fresh; cache invalidated after successful mutation |
| 13 | Logout then log in with different token | Prior private cache content is unavailable |

**Not run here:** Gradle Android build, JUnit under Gradle, authenticated API integration and Android UI tests. The source is a best-effort implementation pending compilation and device QA.

## Known technical debt

- No resume/range requests for release assets; use WorkManager + foreground progress for robust long-running downloads.
- No persisted download queue; app process death may interrupt files.
- No per-response UI cache hit provenance or stale-data badge yet.
- Large (>700 KiB) JSON responses are not cached; downloaded binaries are never cached in the API JSON cache.
- Markdown preview does not resolve private images or relative links; fenced-block preview syntax highlighting is not integrated.
- API pagination uses fixed page size and simple count; using the GitHub `Link` header is preferable to avoid false next-page prompts.
- UI tests and screenshot-based design review should be added before production release.

## Dependency correction from v0.2

Older project pinned Kotlin 2.3.20 but used code highlighter 0.39.0 built with Kotlin 2.4.20, creating a potential metadata incompatibility. v0.3 pins Kotlin 2.3.21, `compose-highlight:0.31.0` (prior to Kotlin 2.4-series libraries) and Compose BOM 2026.05.01. Visual highlight theme changed from GitHub to Tomorrow Light/Night. Dependency resolution has not been executed locally.
