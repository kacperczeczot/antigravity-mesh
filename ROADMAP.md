[Strona główna](README.md) > [Roadmap](ROADMAP.md)

---

# 🗺️ Antigravity Mesh — Roadmapa Projektu

Dokument określa strategiczny plan rozwoju platformy Antigravity Mesh, kamienie milowe oraz szczegółowy zakres prac dla kolejnych wydań minor w linii **v2.x**. 

Uzasadnienie architektoniczne dla ewolucji platformy znajduje się w [ADR 0002: Przejście z Modelu Synchronicznego RPC na Architekturę Orkiestracji Zasobów i Silnik Zadań](docs/adr/0002-architektura-orkiestracji-zasobow-i-zadan.md).

---

## 📊 Przegląd Kamieni Milowych

```
v2.6.1 (Aktualna) ──► v2.7: Task Engine ──► v2.8: Cluster & Projects ──► v2.9: Autonomous Mesh
 [Stabilne I/O,        - Capability Model    - Fan-Out Scheduler          - Profile agentów
  Zero-Sleep daemon]   - Asynchroniczne Tasks- Obiekt Project             - Delegacja zadań
                       - Execution Policies  - Git JSON API               - Schowek i Bridge
                       - Baza redb w Rust    - mTLS & Tożsamość           - WebRTC Screen
                       - Android Task Center - Agent Workspace
```

---

## 🎯 Kamień Milowy: Wydanie `v2.7` — Foundation & Asynchronous Task Engine
**Status:** W trakcie planowania / Rozpoczęcie implementacji  
**Cel:** Przekształcenie węzła z serwera synchronicznych endpointów w odporną na rozłączenia mobilne platformę asynchronicznych zadań z jawnym modelem możliwości.

### Backend (`apps/daemon-rs`)
- [ ] **Capability Model (`GET /api/v1/node`)**:
  - Implementacja struktury `CapabilitySet` (flagi: `filesystem`, `process_exec`, `agent`, `gpu`, `tasks`).
  - Zwracanie tożsamości węzła, architektury sprzętowej i wersji systemu operacyjnego.
- [ ] **Asynchroniczny Silnik Zadań (Task Engine)**:
  - Maszyna stanów zadania: `QUEUED` ➔ `RUNNING` ➔ `COMPLETED` | `FAILED` | `CANCELLED`.
  - Endpointy:
    - `POST /api/v1/tasks` — utworzenie i uruchomienie zadania w tle.
    - `GET /api/v1/tasks` — lista ostatnich zadań węzła.
    - `GET /api/v1/tasks/:id` — szczegóły stanu zadania i metadane.
    - `GET /api/v1/tasks/:id/logs` — pobranie buforowanych logów (stdout/stderr) zadania.
    - `DELETE /api/v1/tasks/:id` — anulowanie wykonującego się zadania.
- [ ] **Wbudowana Baza Stanu (`redb`)**:
  - Czysty magazyn klucz-wartość w Rust dla historii zadań i metadanych.
  - Retencja logów i automatyczne czyszczenie starych zadań.
- [ ] **Execution Policies (Polityki Bezpieczeństwa)**:
  - Tryby egzekucji: `SAFE` (whitelist narzędzi deweloperskich), `NORMAL` (ochrona przed komendami destrukcyjnymi), `UNRESTRICTED`.
- [ ] **Warstwa Wstecznej Zgodności**:
  - Gwarancja pełnego wsparcia dla endpointów v2.6.x (`/health`, `/system`, `/query`, `/read-file`, `/ask/stream`).

### Aplikacja Android (`apps/android`)
- [ ] **Centrum Zadań (Task Center)**:
  - Nowy dedykowany ekran z listą aktywnych i archiwalnych zadań na połączonych węzłach.
  - Odporność na usypianie aplikacji — pobieranie wyników i logów po powrocie do aplikacji bez błędów zerwania połączenia.
- [ ] **Dynamiczny UI na bazie Capabilities**:
  - Aktywacja/deaktywacja kafelków w zależności od deklaracji zwracanej przez `GET /api/v1/node`.

---

## 🚀 Kamień Milowy: Wydanie `v2.8` — Multi-Node Cluster Orchestrator & Project Workspace
**Status:** Zaplanowane  
**Cel:** Połączenie maszyn w skoordynowany klaster z natywnym obiektem projektu i wsparciem dla Git.

### Zakres prac:
- [ ] **Fan-Out Scheduler**:
  - Rozpraszanie i zrównoleglanie zadań na wiele maszyn naraz według kryteriów sprzętowych (np. build iOS na Macu ARM64, akceleracja AI na PC z GPU Nvidia, testy jednostkowe równolegle).
- [ ] **Pierwszoklasowy Obiekt `Project`**:
  - Zastąpienie surowych ścieżek dyskowych bytem projektu (`name`, `workspace_root`, `git_remote`, `build_profiles`).
- [ ] **Natywne Git JSON API**:
  - Endpointy `/api/v1/git/status`, `/api/v1/git/diff`, `/api/v1/git/branches` eliminujące konieczność parsowania powłoki tekstowej przez agenta.
- [ ] **Bezpieczeństwo Klasy Enterprise**:
  - Wymiana certyfikatów mTLS (X.509) podczas procedury parowania kodem PIN.
  - Granularny RBAC per-capability.
- [ ] **Android Agent Workspace**:
  - Ewolucja ekranu czatu w zintegrowany pulpit projektu z podglądem gałęzi Git i kartami zadań.

---

## 🌐 Kamień Milowy: Wydanie `v2.9` — Autonomous Agent Mesh & Device Bridge
**Status:** Zaplanowane  
**Cel:** Pełna autonomia agentów (podział ról i współpraca między maszynami) oraz most sprzętowy pomiędzy urządzeniami.

### Zakres prac:
- [ ] **Profile Agentów i Delegacja Zadań (Agent-to-Agent)**:
  - Autonomiczne zlecanie i rozliczanie podzadań pomiędzy agentami na różnych maszynach w klastrze (role: Developer, Tester, Reviewer).
- [ ] **Device Bridge**:
  - Obustronny schowek systemowy w czasie rzeczywistym (Clipboard Sync) pomiędzy Mac, Windows i telefonem.
  - Przekazywanie powiadomień systemowych (Push Notifications).
  - Opcjonalny podgląd pulpitu stacji roboczej przez WebRTC.

---

## 📜 Historia Wydań

- **`v2.6.1` (2026-09-08)**: Patch release — poprawki zawijania tekstu w dialogach i oknach aplikacji Android, natywne zarządzanie energią Zero-Sleep (IOKit na macOS, SetThreadExecutionState na Windows).
- **`v2.6.0` (2026-09-06)**: Nowoczesny panel uprawnień macOS TCC, auto-fix, dark mode UI, podgląd PDF, Markdown, Mermaid i KaTeX.
