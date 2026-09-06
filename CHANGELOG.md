[Strona główna](README.md) > [CHANGELOG](CHANGELOG.md)

---

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.0.6] - 2026-09-06

### Fixed (Android)
- **Markdown Code Block & Indentation Rendering**:
  - Fixed issue where indented code blocks (e.g. `  ```text` inside bullet lists) were not recognized as code blocks due to leading whitespace and were rendered as raw broken text.
  - Implemented `trimIndent()` on multiline code blocks to eliminate unwanted indentation margins.
  - Added support for single-line code blocks (` ```lang code``` `).
  - Enhanced inline code parser to properly handle arbitrary numbers of backtick delimiters (` `code` `, ` ``code`` `, ` ```code``` `).
  - Added unit test suite `MarkdownTextTest.kt` covering inline code, links, and bold formatting.

## [2.0.5] - 2026-09-06

### Fixed (Android)
- **File Viewer Dialog bottom navigation padding**: Added `.navigationBarsPadding()` to the action bar in `FileViewerDialog`, preventing the bottom action buttons (Zamknij, etc.) from being cut off or overlapped by the Android system navigation bar / gesture pill.
- **Android APK update release fix**: Bumped `versionCode` to 20 and `versionName` to `2.0.5` with clean release artifacts and fresh URLs to guarantee in-app updater picks up the new binary without CDN caching conflicts.

## [2.0.4] - 2026-09-06

### Added
- **Persistent Session Logger — `mesh_sessions.jsonl`** (daemon-rs):
  - New `session_log` module records every agent interaction to a JSONL file **independently of the IDE** — tool-call history is never lost even if the client disconnects mid-stream.
  - Log path: `~/.gemini/mesh_sessions.jsonl` (macOS/Linux) or `%APPDATA%\AntigravityMesh\mesh_sessions.jsonl` (Windows).
  - **Incremental, per-event writes**: each event is flushed to disk immediately upon occurrence (append + newline), not at the end of the session. A dropped SSE connection still preserves all prior tool calls.
  - Events logged: `session_start` (question + conv_id), `tool_call` (name + full parameters JSON), `tool_result` (name + completion), `response_delta` (text preview ≤ 500 chars), `session_end` (full response + return code + status), `disconnected` (client drop mid-session), `error` (spawn failure / no CLI).
  - Both `/ask` (synchronous) and `/ask/stream` (SSE streaming) endpoints are covered.
  - **New `GET /sessions` endpoint** — returns the last 100 JSONL entries as a JSON array with `count` and `log_path` metadata. Suitable for consumption by the Android app or any REST client.
- **11 unit tests** (`session_log::tests`):
  - ISO 8601 timestamp formatter verified against known Unix timestamps (epoch, 2024-01-01, leap day 2024-02-29, end-of-year 2023-12-31, time components).
  - JSONL serialization: correct fields, `skip_serializing_if` omits `None` optional fields.
  - File I/O round-trips: write → read-back → field validation; multi-event append ordering; `limit` slicing returns correct tail; graceful empty-vec on missing file.
## [2.0.3] - 2026-09-06

### Fixed & Improved
- **Mobile UI/UX Refinement & Visual Polish (Android)**:
  - **Single-Line File Search Bar**: Converted file search field to a full-width, single-line input with short placeholder ("Szukaj plików..."), eliminating double-line expansion and text wrapping bugs.
  - **Unified Sorting & Filter Pill**: Consolidated duplicate sorting buttons into a single sleek pill menu (`Sortuj: Nazwa (A-Z) ▾`) with options for Name, Date, Size, Folders-first, and Hidden files.
  - **Directory Traversal & Navigation Clarification**: Separated top-left back arrow (exits directly to previous view/chat) from system back gesture (steps backward through directory history `historyStack`). Fixed infinite navigation loops at root directories.
  - **Clean Breadcrumb Toolbar**: Replaced ambiguous briefcase icon with intuitive `⬆` (Katalog wyżej), `🏠` (Katalog domowy), scrollable monospace path, and `📋` (Kopiuj ścieżkę).
  - **Safe Window Insets**: Added `systemBarsPadding()` to `FileViewerDialog` to prevent content overlapping camera cutouts, notches, and system navigation bars.
  - **Responsive Node Cards**: Redesigned `NodeCard` action buttons with `SpaceBetween` layout; moved utility tools (`Pliki`, `Odśwież`, `Przypnij`, `Usuń`) to the left and compact `Rozmawiaj` button to the right, preventing button squishing and horizontal overflow on narrow screens.
  - **Natural Chat Bubbles**: Replaced forced `85%` width on `ChatBubble` with content-adaptive wrapping (`widthIn(min = 40.dp, max = 320.dp)`), eliminating oversized empty balloons for short replies.
  - **Sleek Chat Input**: Redesigned input area using elevated `Surface` and `OutlinedTextField` with `Alignment.Bottom` anchor, keeping the Send button stable during multi-line typing.
  - **Interactive Scan Feedback**: Added `CircularProgressIndicator` to the `Skanuj LAN` button during active network discovery.
  - **Clean Header Icons**: Removed harsh borders from dashboard update button for a consistent Material 3 aesthetic.

## [2.0.2] - 2026-09-06

### Added
- **In-App Desktop Self-Updater & Auto-Restart**:
  - Implemented in-app background auto-updater for the native Rust daemon across macOS, Windows, and Linux via `POST /update/apply`.
  - Downloads updated binaries directly through the daemon process (bypassing browser quarantine).
  - Automatically applies executable permissions (`chmod 0o755`) and executes atomic replacement with automatic backup recovery on failure.
  - Spawns the updated binary and gracefully restarts the daemon in-place.
- **Automatic macOS Quarantine Removal (StageSync Architecture)**:
  - Added native quarantine stripping at daemon startup (`clear_self_quarantine_if_needed`), removing `com.apple.quarantine` from the binary and running `xattr -cr` across the `.app` bundle.
  - Automatically strips Gatekeeper quarantine attributes immediately after downloading and installing new updates.
- **One-Click Update UI**:
  - Added interactive `⚡ Aktualizuj teraz (v...)` item directly inside the system tray menu (`apps/daemon-rs/src/tray.rs`), initiating updates in a background thread with live state labels (`⏳ Pobieranie aktualizacji...`).
  - Added interactive `⚡ Aktualizuj teraz` button in the Web Dashboard (`handle_root`), replacing the previous external browser link with instant in-app execution.
  - Documented Gatekeeper bypass (`xattr -cr /Applications/AntigravityMesh.app`) in `README.md` for initial manual DMG installations.

## [2.0.1] - 2026-09-06

### Fixed
- **Remote File Explorer Directory Traversal**:
  - Added folder navigation history stack (`historyStack`); the back arrow and Android system gesture step back folder-by-folder instead of exiting to the dashboard.
  - Added dedicated top-right `✕` button to exit file explorer directly.
  - Added client-side fallback `getParentDirectory()` ensuring the folder up (`↑`) button is always active and functional even if older daemons return `parent_path: null`.
  - Added client-side home directory inference (`inferHomeDirectory()`) and daemon-side multi-layer home resolution (`Path.home()`, `HOME`, `USERPROFILE`, `HOMEDRIVE`+`HOMEPATH`) resolving `Path ~ does not exist` errors.
  - Safe folder clicks: directory transitions now compute sanitized absolute paths, preventing relative path resolution bugs.
- **Universal Remote File Reading**:
  - Implemented automatic shell fallback (`cat "$path"` on macOS/Linux or PowerShell `Get-Content` on Windows) in `MeshRepository.kt` when the `/read-file` HTTP endpoint returns 404 on older or unrestarted daemons, ensuring 100% file opening reliability across all daemon versions.
  - Added retry (`Spróbuj ponownie`) and AI analysis (`Zapytaj agenta`) buttons to file viewer error states.
- **File Sorting & Hidden Files**:
  - Added interactive sorting menu in file explorer: Name (A-Z, Z-A), Date (Newest/Oldest), Size (Largest/Smallest), and Folders First toggle.
  - Hidden files and folders (starting with `.`) are now **hidden by default**.
  - Added "Pokaż ukryte pliki" toggle in the sort menu, along with a prominent button in empty folders when hidden files are present.
  - Added status bar count indicating visible elements and hidden file count (`N elementów (ukryto X)`).
- **File Viewer & Code Formatting**:
  - Disabled `softWrap` on code lines, preserving natural indentation and enabling clean horizontal scrolling across long lines.
  - Memoized line splitting with `remember(content)` eliminating scroll stutter/jank on large files.
  - Added dedicated UI states for binary files and empty files (0 B).
- **Search Field Text Clipping**:
  - Removed fixed `46.dp` height constraint on `OutlinedTextField` that caused internal Material 3 padding to clip descender characters (`g`, `y`, `p`, `ą`, `ę`).
- **Chat UX & Link Resolution**:
  - Removed artificial quick action prompt cards on empty chat screens in favor of a clean, minimalist prompt interface.
  - Added URL decoding (`URLDecoder.decode(..., "UTF-8")`) for `file://` links containing encoded spaces (`%20`) and special characters, with line anchor parsing (`#L42`).
  - Added tap-to-copy to file explorer breadcrumb path toolbar.
- **Daemon Consistency**:
  - Added `name` and `is_dir` fields to Python daemon's `/read-file` handler to maintain strict schema parity with the native Rust daemon.

## [2.0.0] - 2026-09-06

### Added
- **Remote File Explorer & Code Viewer**:
  - Full-screen file browser in Android (`FileExplorerScreen`) connecting to remote macOS, Windows, and Linux machines via `POST /query` and `POST /read-file`.
  - Directory tree navigation with breadcrumb trail, quick roots (`~` Home, `.` Project), and parent folder (`..` / system back gesture) traversal.
  - In-folder live search filter, file type recognition, and extension-colored iconography (source code, configs, markdown, images, archives).
  - High-performance modal code viewer (`FileViewerDialog`) with line numbering, monospace typography, bi-directional scrolling, and quick clipboard copy with haptic feedback.
  - **Direct AI Agent Integration**: One-tap `🤖 Zapytaj agenta` button on any file immediately opens the node's chat with an automatic prompt to inspect and analyze the file.
- **Interactive Markdown File Links & Deep Inspection**:
  - Complete Markdown link parsing (`[label](url_or_path)`) and bare URL recognition in `MarkdownText` using Compose 1.7 `LinkAnnotation.Clickable`.
  - Tapping any `file:///...` link in chat instantly opens the remote file viewer modal without leaving the conversation.
  - Line anchor support: automatically scrolls to and highlights target line numbers (e.g. `[main.rs:L42](file:///...#L42)`).
  - Directory link detection: automatically suggests opening target directories in the full Remote File Explorer.
  - Web link routing: opens external `http://` and `https://` URLs in default browser.
- **Chat UX Enhancements**:
  - **Instant Stop Button (⏹ STOP)**: Client-side cancellation closes SSE stream, immediately triggering server-side `child.kill().await` in daemon to terminate abandoned CLI/agent processes on the workstation.
  - **Code Block Banners & Copy**: Elegant code headers with language badges (`KOD`, `RUST`, `PYTHON`, `BASH`) and dedicated `📋 Kopiuj` button.
  - **Quick Prompt Chips**: Instant-launch query suggestions on empty chat screens (git status, run tests, CPU/RAM usage, project layout).
  - **Export & Share Sheet**: Formats entire conversation into clean Markdown and launches Android system share sheet.
- **Cluster Management & Security**:
  - **Search & Filters**: Real-time filtering by node name, alias, or host, with filter chips (`Wszystkie`, `Online`, `Przypięte ⭐`).
  - **Host & Port Editing**: Edit IP/hostname and port in node settings dialog to accommodate network migrations without losing chat history.
  - **Desktop Tray Session & PIN Reset**: Added `🔄 Zresetuj PIN i sesje` in macOS/Windows/Linux system tray to instantly cycle pairing PINs and synchronize active sessions.
  - **Linux XDG Autostart**: Added standard `~/.config/autostart/antigravity-mesh.desktop` support for 100% autostart coverage across macOS, Windows, and Linux.
- **Stability & Sockets**:
  - Socket exhaustion prevention: throttled LAN subnet scanner with `Semaphore(32)` to prevent router overload and `EMFILE: Too many open files`.

### Changed
- **Modular Component Architecture**: Extracted shared `FileViewerDialog` across both `FileExplorerScreen` and `ChatScreen` for consistent inspection UX.
- **Daemon Path Resolution**: Upgraded `resolve_path` in Rust daemon to sanitize URI schemes (`file://localhost/`, `file:///`, `file://`, `file:`), expand home directories across OSes, and strip anchor fragments.

## [1.4.3] - 2026-09-06

### Fixed
- **Manual Node Pairing Freeze**: Separated fast timeout HTTP client (4s connect, 5s read) for node pairing and health checks from 600s AI streaming client. Added `withTimeout(7000L)` safety guard in repository, immediate UI dismissal, and host URL sanitization/port validation (1..65535).
- **Duplicate Key Crash on Valid Address**: Fixed `LazyList` crashes caused by duplicate Jetpack Compose keys (`IllegalArgumentException: Key was already used`) when adding nodes with identical hostnames or re-pairing existing nodes; now safely updates existing nodes in-place (preserving aliases and pin status) or assigns collision-free IDs (`-2`, `-3`), with `.distinctBy { it.id }` protection in Compose views.
- **Metrics NaN/Infinity Guards**: Protected CPU and RAM progress bars and percentages in `NodeCard` against `NaN` and infinite values from containerized or virtualized nodes.

### Changed
- **Update Banner Compact Design**: Redesigned the update announcement banner on the dashboard into a sleek, slim single-row card with compact typography, clickable surface, and optimized padding and vertical spacers.

## [1.4.2] - 2026-09-06

### Added
- **Delete Confirmation Dialogs**: Interactive warning dialogs with clear confirmation prompts before deleting a node from the cluster or clearing chat history, preventing accidental data loss.

### Changed
- **Header Action Bar Cleanup**: Streamlined the dashboard top bar by removing redundant manual add and refresh buttons; the top bar now exclusively features the clean, focused update notification button.

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
