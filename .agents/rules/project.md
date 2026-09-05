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
