[Strona główna](README.md) > [CHANGELOG](CHANGELOG.md)

---

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
## [1.3.1] - 2026-09-05

### Added
- **Desktop Update Checker**: Added background update checker to `apps/daemon-rs` (Windows / macOS / Linux) with System Tray menu badge (`✨ Update Available`) and Web Dashboard banner.

### Fixed
- **Android Layout Insets & Padding**: Added `statusBarsPadding()` and `navigationBarsPadding()` to `DashboardScreen.kt` and `ChatScreen.kt` to prevent layout overlap with Android status bar and gesture navigation bar.

## [1.3.0] - 2026-09-05

### Added
- **`MainViewModel` & Android State Preservation**: Jetpack Compose state retained across activity recreations and screen rotations.
- **Persistent Chat History**: Full chat history stored in Android `SharedPreferences` with per-node history clear button.
- **Raw Manifest Update Fallback**: `ReleaseUpdateChecker` updated with raw GitHub manifest fallback (`android-latest.json`) and CDN redirect support (`*.amazonaws.com`, `*.githubusercontent.com`).
- **Clear Chat Action**: Top-bar action button to wipe conversation history for a specific node.

### Changed
- **Windows Silent Daemon**: Added `#![windows_subsystem = "windows"]` to `apps/daemon-rs` to run 100% silently in the background without popping up console windows.
- **Android Keyboard Scaling**: Added `android:windowSoftInputMode="adjustResize"` and `.imePadding()` to `ChatScreen.kt` for smooth soft keyboard adjustment.
- **Cleaned UI**: Removed sample suggestion chips from chat screen for clean user experience.

## [1.2.0] - 2026-09-05

### Added
- Native Jetpack Compose Markdown text parser (`MarkdownText`) supporting headers, bold/italics, bullet lists, inline code, and syntax code blocks.
- Automatic horizontal scroll containers (`Modifier.horizontalScroll`) for Markdown tables and code blocks to prevent layout breaking on mobile screens.
- Cross-platform AI CLI auto-discovery on Windows (`where.exe`, `%LOCALAPPDATA%\agy\bin`, `.exe`/`.cmd`/`.bat` extensions).
- Dedicated `POST /ask` endpoint implementation in Python daemon (`apps/daemon-py/server.py`) for complete feature parity with Rust daemon.

### Changed
- Streamlined mobile UI/UX in Android companion app: removed bottom navigation bar (`NavigationBar`) and legacy `QuickActionsScreen`.
- Transitioned to clean 2-level architecture: Devices & Conversations List -> Node Chat with top App Bar back button (`<-`).
- Displayed last conversation message preview on device cards (`NodeCard`).

## [1.1.0] - 2026-09-05

### Added
- Unified high-resolution app icon deployed across Android (`ic_launcher`), Desktop Tray daemon (`icon_32.rgba`, `icon_256.png`, `icon.ico`), and master assets.
- Auto-update support on Android via GitHub Releases (`android-latest.json`, `ReleaseUpdateChecker`, `ApkInstaller`, `UpdateDialog`).
- Native Rust tray daemon with token copy, browser launch, and tray menu (`apps/daemon-rs`).
- CI/CD workflow building and publishing Android APK, macOS arm64 binary, Linux x64 binary, and Windows x64 executable (`.github/workflows/release.yml`).
- Zero-touch LAN scanner and pairing mechanism for peer-to-peer mesh nodes.

### Changed
- Refactored repository structure to 100% compliance with `devex-standards` and `template-monorepo` (`apps/`, `packages/`, `data/`, `docs/`, `scripts/`).
- Split chat histories per node in Android app to isolate cross-device conversations.

## [1.0.0] - 2026-09-05

### Added
- Initial release of Antigravity Mesh cluster daemon, client, skill, and mobile companion app.
