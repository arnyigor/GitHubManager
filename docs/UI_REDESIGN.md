# GitHub Manager 0.2 — UX and Code Viewer

## Design decisions

- Visual direction: GitHub-native rather than unthemed Material3. Dark `#0D1117` with surfaces `#161B22`, blue action `#58A6FF`, restrained borders `#30363D`; light surface and accessible dark text.
- Screens: Login / Repository list / Repository detail with **Code** and **Settings**.
- Repository list: compact cards for density; search and private/public filter; loaded vs matching counts accurately scoped to loaded results.
- Repository detail: filename and directory affordances; "Code" first, dangerous controls under "Settings".
- Code: global line numbering, pinned gutter on horizontal scroll, selection and copy of entire file, GitHub deep link, known language tokens.
- Manual dark/light toggle works during activity recreation via `rememberSaveable`; not saved to a durable preferences store.

## Code viewer data flow

```
GET /repos/{owner}/{repo}/contents/{path}
 -> base64 decode + 200 KB limit (existing)
 -> BrowserState(fileName, fileBody, fileUrl)
 -> codePages(text): 200 lines / 24 KB approximate
 -> HighlightLanguage.fromExtension(extension)
 -> rememberHighlightedCode(chunk, language)
 -> native Compose AnnotatedString + fixed gutter
```

**Bigger files**: GitHub Contents API itself supports files over 200 KB, but this app applies a smaller conservative preview limit. Later versions should use raw file API with streaming, binary detection, truncation warnings and a virtualized line renderer. For multiline grammar correctness across chunks, tokenize continuously or use an incremental parser (TextMate / Tree-sitter) while retaining viewport virtualization.

## Privacy / supply chain

- The highlighter's bundled Highlight.js engine runs locally in a hidden WebView. Review the dependency and lock checksums before security-sensitive use. Data is not intentionally sent to third-party services by this application.
- The browser `fileUrl` is only offered for `https://github.com/...` links.
- No source-code uploads or external APIs were added by UI v0.2.

## Acceptance checklist (to perform on real Android device)

- [ ] Gradle dependency resolution and `assembleDebug` succeeds.
- [ ] Both themes render legibly and persist across rotation.
- [ ] Repo with description / long name / Chinese / emoji does not overlap.
- [ ] Folders navigate forward and backward correctly via system & toolbar back.
- [ ] Kotlin, JS, JSON, YAML, XML and Python highlight in both themes.
- [ ] Plaintext and unknown file extensions fall back without crashing.
- [ ] 1 line, 10 lines, 1000+ lines, long line, LF/CRLF, and trailing newline cases.
- [ ] Code gutter stays pinned when horizontal scrolling.
- [ ] Large code file paging maintains absolute line numbers and complete content.
- [ ] Clipboard copies entire source and file path accurately.
- [ ] Visible GitHub file links open only in official host.
- [ ] Private repo source isn't exposed in Android logs or analytics.
- [ ] Accessibility: screen reader labels and 200% display scaling.
- [ ] Changing visibility still requires admin and full-name confirmation.

## Not implemented / backlog priority

P0: build + emulator test, automated screenshot tests, fix code viewer issues, credentials review.
P1: persistent theme, lazy repository pagination, file search, syntax-highlight performance benchmarking, GitHub App Device Flow.
P2: tree-sitter incremental tokenizer, streaming raw files, blame/diff, repo branches and PRs.
