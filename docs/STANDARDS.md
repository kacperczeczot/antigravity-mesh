[Strona główna](../README.md) > [Dokumentacja](README.md) > [Standardy](STANDARDS.md)

---

# Standardy Inżynieryjne Projektu (Monorepo)

Niniejszy projekt został ustrukturyzowany na bazie szablonu **[template-monorepo](https://github.com/kacperczeczot/template-monorepo)** i bezwzględnie przestrzega globalnych reguł zdefiniowanych w centralnej Konstytucji **[devex-standards](https://github.com/kacperczeczot/devex-standards)**.

---

## 1. Zgodność ze Standardami Zewnętrznymi

| Standard | Implementacja w Projekcie | Oficjalna Specyfikacja |
| :--- | :--- | :--- |
| **Conventional Commits** | Commity w języku angielskim wg typów (`feat`, `fix`, `docs`, `refactor`) | [conventionalcommits.org](https://www.conventionalcommits.org/pl/v1.0.0/) |
| **Semantic Versioning** | SemVer (`MAJOR.MINOR.PATCH`) + tagi `vX.Y.Z` w GitHub Releases | [semver.org](https://semver.org/lang/pl/) |
| **Keep a Changelog** | [`CHANGELOG.md`](../CHANGELOG.md) wg specyfikacji 1.1.0 | [keepachangelog.com](https://keepachangelog.com/pl/1.1.0/) |
| **ADR** | Rejestr w [`docs/adr/`](adr/README.md) na podstawie wzorca `0000-*.md` | [adr.github.io](https://adr.github.io/) |
| **EditorConfig** | [`.editorconfig`](../.editorconfig) w root dla spójności formatowania | [editorconfig.org](https://editorconfig.org/) |
| **Kanon Root** | Wyłącznie dozwolone katalogi (`apps/`, `packages/`, `data/`, `docs/`, `scripts/`) | [devex-standards](https://github.com/kacperczeczot/devex-standards) |

---

## 2. Architektura Monorepo (`template-monorepo`)

| Moduł / Aplikacja | Ścieżka | Technologia | Rola |
| :--- | :--- | :--- | :--- |
| **Aplikacja mobilna** | `apps/android` | Kotlin / Jetpack Compose / Material 3 | Mobilny kokpit sterowania klastrem, czat z agentami |
| **Demon natywny** | `apps/daemon-rs` | Rust / Tokio / Axum / tray-icon | Szybki proces tła z ikoną w trayu (macOS / Windows / Linux) |
| **Demon referencyjny** | `apps/daemon-py` | Python / Asyncio / HTTP / MCP | Wzorcowy serwer węzła i interfejsu narzędziowego MCP |
| **Klient CLI & SDK** | `packages/client` | Python / CLI (`agy-mesh`) | Biblioteka kliencka oraz narzędzie wiersza poleceń |
| **Skill Antigravity** | `packages/skill` | Markdown / YAML | Definicja umiejętności dla asystenta Google Antigravity |
| **Dane & Konfiguracja**| `data/config` | JSON | Szablony konfiguracji klastra węzłów |

---

## 3. Nadrzędne Źródło Prawdy (SSOT)
Szczegółowe zasady inżynierii dziedziczone są z:
👉 **[devex-standards / Architecture Rules](https://github.com/kacperczeczot/devex-standards/blob/main/docs/architecture/RULES.md)**
👉 **[devex-standards / Tooling Rules](https://github.com/kacperczeczot/devex-standards/blob/main/docs/tooling/RULES.md)**
