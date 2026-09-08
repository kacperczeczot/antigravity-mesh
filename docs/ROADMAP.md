[Strona główna](../README.md) > [Dokumentacja](README.md) > [Roadmap](ROADMAP.md)

---

# 🗺️ Antigravity Mesh — Roadmapa Projektu

Dokument określa strategiczny plan rozwoju platformy Antigravity Mesh, kamienie milowe oraz zakres prac. 

Plan zakłada **dokładnie jeden duży skok generacyjny (wersję Major `v3.0`)**, poprzedzony wydaniem fundamentu `v2.7` i kontynuowany w linii `v3.x`.

Uzasadnienie architektoniczne dla ewolucji platformy znajduje się w [ADR 0002: Przejście z Modelu Synchronicznego RPC na Architekturę Orkiestracji Zasobów i Silnik Zadań](adr/0002-architektura-orkiestracji-zasobow-i-zadan.md).

---

## 📊 Przegląd Kamieni Milowych

```
v2.6.1 (Bieżąca) ──► v2.7: Foundation ──► v3.0: Cluster & Workspace ──► v3.1: Autonomous Mesh
 [Stabilne I/O,       - Capability Model   - [MAJOR RELEASE]              - Profile agentów
  Zero-Sleep daemon]  - Task Engine        - Fan-Out Scheduler            - Delegacja zadań
                      - Baza redb          - Obiekt Project               - Schowek w czasie rzecz.
                      - Execution Policies - Git JSON API                 - WebRTC Screen Bridge
                      - Android Task Center- mTLS & Tożsamość urządzeń
                                           - Agent Workspace na Androidzie
```

---

## 🎯 Kamień Milowy 1: Wydanie `v2.7` — Foundation & Asynchronous Task Engine
**Status:** ✅ Zrealizowane (`v2.7.0`)  
**Cel:** Przekształcenie węzła z serwera synchronicznych endpointów w odporną na rozłączenia mobilne platformę asynchronicznych zadań z jawnym modelem możliwości.

### Backend (`apps/daemon-rs`)
- [x] **Capability Model (`GET /api/v1/node`)**:
  - Implementacja struktury `CapabilitySet` (flagi: `filesystem`, `process_exec`, `agent`, `gpu`, `tasks`).
  - Zwracanie tożsamości węzła, architektury sprzętowej i wersji systemu operacyjnego.
- [x] **Asynchroniczny Silnik Zadań (Task Engine)**:
  - Maszyna stanów zadania: `QUEUED` ➔ `RUNNING` ➔ `COMPLETED` | `FAILED` | `CANCELLED`.
  - Endpointy:
    - `POST /api/v1/tasks` — utworzenie i uruchomienie zadania w tle.
    - `GET /api/v1/tasks` — lista ostatnich zadań węzła.
    - `GET /api/v1/tasks/:id` — szczegóły stanu zadania i metadane.
    - `GET /api/v1/tasks/:id/logs` — pobranie buforowanych logów (stdout/stderr) zadania.
    - `DELETE /api/v1/tasks/:id` — anulowanie wykonującego się zadania.
- [x] **Wbudowana Baza Stanu (`redb`)**:
  - Czysty magazyn klucz-wartość w Rust dla historii zadań i metadanych.
  - Retencja logów i automatyczne czyszczenie starych zadań.
- [x] **Execution Policies (Polityki Bezpieczeństwa)**:
  - Tryby egzekucji: `SAFE` (whitelist narzędzi deweloperskich), `NORMAL` (ochrona przed komendami destrukcyjnymi), `UNRESTRICTED`.
- [x] **Warstwa Wstecznej Zgodności**:
  - Gwarancja pełnego wsparcia dla endpointów v2.6.x (`/health`, `/system`, `/query`, `/read-file`, `/ask/stream`).

### Aplikacja Android (`apps/android`)
- [x] **Inteligentne Wznawianie i Odzyskiwanie Stanu (Re-attach / Fetch Result)**:
  - Eliminacja „ślepego ponawiania” wiadomości, które ryzykowało zdublowaniem pracy agenta w tle.
  - Przycisk `🔄 Sprawdź status na węźle (Wznów)` w dymku błędu:
    1. Sprawdza, czy zadanie nadal trwa na węźle ➔ wznawia podgląd pracy i animację agenta (Re-attach).
    2. Sprawdza, czy zadanie zakończyło się w tle ➔ natychmiast pobiera gotową odpowiedź i logi (Fetch Result).
    3. Dopiero gdy węzeł potwierdzi, że zapytanie w ogóle nie dotarło ➔ oferuje bezpieczne ponowne wysłanie.
- [x] **Kolejkowanie Wiadomości w Trakcie Pracy Agenta (Message Queueing)**:
  - Pole tekstowe czatu nie jest blokowane podczas generowania odpowiedzi.
  - Użytkownik może dodawać kolejne instrukcje do kolejki (`⏳ W kolejce (oczekuje na agenta)`), które są automatycznie wysyłane do agenta po zakończeniu bieżącego kroku (identycznie jak w edytorach Cursor / Antigravity IDE).
- [x] **Wielowątkowość Czatu (Multi-Session / Chat Threads per Node)**:
  - Odejście od ograniczenia "jeden komputer = jeden kontekst rozmowy".
  - Wprowadzenie wielu niezależnych sesji czatu per węzeł: przycisk `➕ Nowy wątek`, poziomy pasek wątków, tytuły sesji i płynne przełączanie kontekstów bez utraty historii.
- [x] **Dynamiczny UI na bazie Capabilities i obsługa API v1**:
  - Integracja endpointów `/api/v1/node`, `/api/v1/tasks` w repozytorium sieciowym.

---

## 🚀 Kamień Milowy 2: Wydanie `v3.0` [MAJOR] — Multi-Node Cluster Orchestrator & Project Workspace
**Status:** Zaplanowane  
**Cel:** Przejście na nową generację platformy — połączenie maszyn w skoordynowany klaster z natywnym obiektem projektu, mTLS i wsparciem dla Git.

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
- [ ] **Android Agent Workspace & Kontekst Projektowy**:
  - Powiązanie wątków czatu z wybranym projektem i katalogiem repozytorium (`workspace_root`) zamiast płaskiego katalogu domowego.
  - Ewolucja ekranu czatu w zintegrowany pulpit projektu z podglądem gałęzi Git, diffów i kartami zadań.

---

## 🌐 Kamień Milowy 3: Wydanie `v3.1` — Autonomous Agent Mesh & Device Bridge
**Status:** Zaplanowane  
**Cel:** Rozszerzenie platformy v3 o pełną autonomię agentów (podział ról i współpraca między maszynami) oraz most sprzętowy pomiędzy urządzeniami.

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
