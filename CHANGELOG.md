[Strona główna](README.md) > [CHANGELOG](CHANGELOG.md)

---

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.4.1] - 2026-09-06

### Added
- **Android Adaptive Icons**: Full XML adaptive icon configuration (`res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`) with dedicated `@mipmap/ic_launcher_background` (spacetime grid on obsidian canvas) and `@mipmap/ic_launcher_foreground` (glowing Antigravity "M" logo with safe zone compliance).

### Fixed
- **App Icon White Corners**: Fixed rasterization artifact where SVG transparency was flattened onto solid white by `qlmanage`. Re-rendered all mipmaps (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`), drawable, desktop `AppIcon.icns`, `icon.ico`, and `icon.png` with true 100% alpha transparency outside squircle and circle masks. Eliminates white backing plate and white corners across modern Android launchers (Pixel, Samsung OneUI, Nova).

## [1.4.0] - 2026-09-06

### Added
- **Live Cluster Monitoring**: Automatic real-time polling (every 4s) of node health, ping, CPU, and RAM metrics in Android app.
- **Parallel Async Queries**: Concurrent node probing (`async` / `awaitAll`) in Android repository preventing offline or slow nodes from delaying cluster updates.
- **Lifecycle Awareness**: Auto-refresh loop active strictly while `DashboardScreen` is in foreground; pauses automatically on chat navigation or app backgrounding.
- **Device Aliases & Custom Names**: Rename nodes via edit dialog (✏️) with original hostname preserved in subtitles and option to restore default.
- **Pin-to-Top**: Pin favorite nodes (📌) to permanently float them to the top of dashboard and chat chips.
- **Antigravity Dark Branding & Visual Design**: Deep obsidian canvas (`#080B14`), midnight card surfaces (`#111728`, `#182238`), electric cyan and violet signature gradients, and refined card borders.
- **Official Antigravity Vector Logo & Launcher Icons**: Pure vector SVG with twin-wave "M" (for Mesh) over spacetime coordinate grid with authentic Antigravity lime-amber-violet-blue Gaussian gradient across Android mipmaps/drawables, macOS `AppIcon.icns`, Windows `icon.ico`, and raw RGBA system tray icon.
- **Cross-Platform Autostart**: Native autostart on boot support in `apps/daemon-rs` via macOS Login Items and Windows Registry Run key with tray menu toggle item (*Launch at Login*).

### Fixed
- **System Back Navigation**: Registered `BackHandler` in `ChatScreen.kt` so pressing the Android system back button or back gesture returns to the device list instead of exiting the app.
- **Ping Status Badge Layout**: Enforced `maxLines = 1, softWrap = false` and dedicated `Surface` container to prevent narrow compression and vertical single-letter text wrapping.
- **Header Action Cleanup**: Removed redundant LAN scan button from dashboard top bar while retaining bottom action button.

## [1.3.5] - 2026-09-06

### Added
- **Server-Sent Events (SSE) Streaming**: Implemented `POST /ask/stream` in `apps/daemon-rs` providing real-time streaming of agent execution steps, commands, and text deltas.
- **Live Agent Status in Android App**: Real-time status display ("Wykonywanie run_command...", "Agent analizuje zapytanie...") during execution.
- **Extended Analysis Timeout**: Increased execution timeout to 600s (10 minutes) to eliminate premature timeouts during deep code repository investigations.

## [1.3.4] - 2026-09-06

### Added
- **1-Click macOS Self-Updater**: Added `scripts/update_macos.sh` script to download latest DMG, mount, install to `/Applications`, remove quarantine, and restart daemon.
- **Release Manifest Fallback**: Enhanced update checker with fallback to raw GitHub release manifest.

## [1.3.3] - 2026-09-05

### Added
- **Manual Node Pairing**: In-app dialog to add and pair nodes by IP/hostname and port.
- **Node Deletion**: Ability to remove obsolete or unreachable nodes from the cluster.

### Fixed
- **Keystore Signing Inconsistency**: Embedded persistent `keystore/debug.keystore` to permanently eliminate Android `INSTALL_FAILED_UPDATE_INCOMPATIBLE` error during updates.

## [1.3.2] - 2026-09-05

### Added
- **Multi-Turn Chat Sessions**: Conversation continuity preserved across consecutive requests to the same node agent.
- **Tailscale & CGNAT Range Support**: Added automatic recognition and handling of Tailscale IP ranges (`100.64.0.0/10`) for remote pairing.
- **Windows Executable Icon**: Embedded high-resolution icon into `daemon-rs.exe` binary via `winres`.

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
