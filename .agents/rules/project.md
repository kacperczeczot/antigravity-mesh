---
name: Reguły Projektu
description: Zasady architektoniczne i operacyjne dla Antigravity Mesh.
---

[Strona główna](../../README.md) > [.agents](../rules/project.md) > [project](project.md)

---

# Reguły Projektu Antigravity Mesh

Architektura i organizacja modułów w klastrze Antigravity Mesh.

## Struktura Monorepo
- `apps/android` – aplikacja mobilna (Jetpack Compose, PackageInstaller, auto-update GitHub Releases)
- `apps/daemon-rs` – natywny demon systemowy w Rust (system tray)
- `apps/daemon-py` – serwer MCP węzła w Pythonie
- `packages/client` – klient Python / CLI `agy-mesh`
- `packages/skill` – skill dla asystenta Google Antigravity
- `data/config` – szablony i pliki konfiguracji węzłów
- `scripts/` – skrypty instalacji i autostartu

## Zasady
- Bezwzględny zakaz dodawania folderów w korzeniu repozytorium poza dozwolonym słownikiem (`apps/`, `packages/`, `data/`, `docs/`, `scripts/`, `.agents/`).
- Wszystkie pliki Markdown posiadają breadcrumbs na samej górze.

## Standard Wydań i Release Notes
Każde wydanie projektu (Major, Minor, Patch) MUSI zachowywać jednolitą, ustrukturyzowaną postać:
1. **Tytuł**: `Antigravity Mesh v<wersja>`
2. **Tabela instalatorów**: Sekcja `### 📦 Pobierz Antigravity Mesh` z bezpośrednimi linkami do pobrania dla wszystkich 5 platform (macOS DMG, macOS CLI, Windows EXE, Linux, Android APK).
3. **Separator**: `---`
4. **Highlights**: Sekcja `### 🚀 Highlights — v<wersja>` z zachowaniem hierarchii kategorii (np. `#### 🛡️ ...`, `#### 📱 ...`), podtytułów oraz zagnieżdżonych list punktowanych z pliku `CHANGELOG.md`.
5. **Separator**: `---`
6. **Stopka**: Bezpośredni link do dziennika zmian: `Pełna historia zmian: [CHANGELOG.md](...)`.

Generowanie notatek jest zautomatyzowane za pomocą skryptu [`scripts/build-release-notes.py`](../../scripts/build-release-notes.py).

## Rygor Weryfikacji UI / UX i Testów Przed Ukończeniem Zadań
Każda zmiana w kodzie aplikacji (`apps/android`) i demonów MUSI przejść pełną procedurę weryfikacji przed zgłoszeniem ukończenia prac:
1. **Audyt Orientacji (Portrait i Landscape)**:
   - Weryfikacja, czy elementy interfejsu (nagłówki, paski nawigacji, panele dokowane, listy) mieszczą się i są czytelne na ekranach o ograniczonej wysokości w poziomie (~360-400dp).
2. **Audyt Klawiatury Ekranowej (IME) i Insetów**:
   - Sprawdzenie zachowania przy otwartej klawiaturze – zakaz dublowania insetów (`navigationBars` + `ime`). Pola wprowadzania tekstu muszą być w 100% widoczne i dostępne.
   - W widoku poziomym elementy poboczne (np. paski zakładek, podglądy kolejek) muszą ustępować miejsca edytorowi tekstu (`isCompactLandscape`).
3. **Weryfikacja Przypadków Skrajnych (Edge Cases)**:
   - Długie teksty bez podziału na linie, wieloliniowe akapity, zerowe/puste listy, stany błędów i rozłączenia sieci.
   - Etykiety i teksty muszą być poprawne gramatycznie i nie mogą prezentować sztucznych/błędnych statystyk (np. surowych `lines().size`).
4. **Automatyczne Testy Jednostkowe**:
   - Przed zakończeniem zadania należy uruchomić pełny zestaw testów (`./gradlew test` oraz `cargo test`) i upewnić się, że wynik to 100% powodzenia.
5. **Zakaz Samowolnych Wydań (Releases)**:
   - Bezwzględny zakaz tworzenia tagów git, release'ów i pushowania bez wyraźnego, bezpośredniego polecenia użytkownika.
