[Strona główna](../../README.md) > [Dokumentacja](../README.md) > [ADR](README.md) > [0001-struktura-monorepo-devex](0001-struktura-monorepo-devex.md)

---

# 0001. Migracja do Modelu Monorepo i Standardów DevEx

* **Status:** Zaakceptowana
* **Data:** 2026-09-05
* **Autorzy:** Kacper Czeczot / Antigravity AI

---

## 1. Kontekst i Problem
Dotychczasowy układ repozytorium zawierał foldery w katalogu głównym (`android/`, `daemon/`, `daemon-rs/`, `client/`, `config/`, `skill/`), co stanowiło naruszenie Zamkniętego Kanonu Root oraz wytycznych inżynieryjnych ekosystemu zdefiniowanych w `devex-standards`.

Ze względu na wielotargetowy charakter projektu (aplikacja mobilna na Androida, natywny demon tray w Rust, demon w Pythonie, klient CLI oraz skill asystenta), projekt wymaga deterministycznego modelu **Monorepo** opartego na szablonie `template-monorepo`.

## 2. Rozważane Opcje
* **Opcja 1 (Pozostawienie struktury płaskiej):** Brak zmian w root, co prowadzi do długu architektonicznego, niespójności z resztą ekosystemu oraz łamania Konstytucji DevEx.
* **Opcja 2 (Migracja do Monorepo wg `template-monorepo`):** Przeniesienie aplikacji wykonawczych do `apps/`, współdzielonych bibliotek i skilli do `packages/`, szablonów danych do `data/` oraz wdrożenie kanonu dokumentacji z breadcrumbs.

## 3. Podjęta Decyzja
Wybrano **Opcję 2**: reorganizacja repozytorium do kanonicznej struktury:
- `apps/android` (dawne `android/`)
- `apps/daemon-rs` (dawne `daemon-rs/`)
- `apps/daemon-py` (dawne `daemon/`)
- `packages/client` (dawne `client/`)
- `packages/skill` (dawne `skill/`)
- `data/config` (dawne `config/`)
- `docs/` z `STANDARDS.md` i rejestrem ADR
- `scripts/` zachowane w kanonie root

## 4. Konsekwencje
* **Pozytywne:**
  - 100% zgodność z `devex-standards` i `template-monorepo`.
  - Czysty katalog główny (root) bez samowolki folderowej.
  - Spójne procedury CI/CD i standaryzacja dla modeli AI.
* **Kompromisy:**
  - Konieczność aktualizacji ścieżek w skryptach i CI (`.github/workflows/release.yml`), co zostało zrealizowane.
