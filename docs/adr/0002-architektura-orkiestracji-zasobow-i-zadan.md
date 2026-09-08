[Strona główna](../../README.md) > [Dokumentacja](../README.md) > [ADR](README.md) > [0002-architektura-orkiestracji-zasobow-i-zadan](0002-architektura-orkiestracji-zasobow-i-zadan.md)

---

# 0002. Przejście z Modelu Synchronicznego RPC na Architekturę Orkiestracji Zasobów i Silnik Zadań

* **Status:** Zaakceptowana
* **Data:** 2026-09-08
* **Autorzy:** Kacper Czeczot / Antigravity AI

---

## 1. Kontekst i Problem

Wersje v2.0–v2.6.1 Antigravity Mesh ustabilizowały warstwę I/O: strumieniowanie plików, podgląd dokumentów biurowych, PDF, multimediów, podświetlanie składni, zarządzanie energią (Zero-Sleep) oraz podstawową integrację z asystentami AI (`agy`).

Jednak dotychczasowa architektura węzłów opierała się na modelu **„Endpoint Soup”** — dużej liczbie płaskich, synchronicznych endpointów HTTP RPC (`/query`, `/read-file`, `/upload`, `/exec`, `/ask`). Model ten napotkał na fundamentalne ograniczenia architektoniczne:

1. **Kruchość synchronicznych operacji w sieciach mobilnych**:
   Długotrwałe operacje (kompilacje, testy, wieloetapowa analiza AI) realizowane przez synchroniczne żądania HTTP/SSE (`/ask/stream`, `/exec`) ulegają przerwaniu przy chwilowej utracie zasięgu, przełączeniu Wi-Fi ➔ LTE lub wygaszeniu ekranu smartfona. Klient traci wynik pracy, mimo że proces na węźle mógł zakończyć się sukcesem.
2. **Brak deklaratywnego kontraktu możliwości (Capability Model)**:
   Klient (aplikacja Android / CLI) musiał dedukować możliwości węzła na podstawie nazwy platformy operacyjnej, co utrudniało obsługę heterogenicznych maszyn (np. węzły z akceleracją GPU CUDA, węzły z narzędziami budowania iOS).
3. **Płaski model operowania na surowych ścieżkach dyskowych**:
   Brak semantycznego pojęcia „Projektu” (`Project`) zmuszał klientów i agentów do operowania na bezwzględnych ścieżkach systemowych i tekstowego parsowania wyjścia narzędzi takich jak Git.
4. **Płaski model uprawnień (All-or-Nothing PSK)**:
   Token dostępowy dawał pełny dostęp do powłoki bez możliwości definiowania polityk bezpieczeństwa (Execution Policies).

---

## 2. Rozważane Opcje

* **Opcja 1 (Kontynuacja modelu synchronicznego RPC):**
  Pozostanie przy obecnych endpointach i sztuczne wydłużanie timeoutów HTTP oraz dodawanie kolejnych dedykowanych ścieżek ad-hoc.
  * *Wada:* Nie rozwiązuje problemu rozłączeń mobilnych, prowadzi do postępującej degradacji czytelności API i utrudnia automatyzację klastrową.

* **Opcja 2 (Przejście na Architekturę Orkiestracji Zasobów i Silnik Zadań Asynchronicznych):**
  Przebudowa rdzenia węzła w oparciu o cztery filary:
  1. *Model Możliwości (Capability Model)* z jawnym kontraktem `GET /api/v1/node`.
  2. *Asynchroniczny Silnik Zadań (Task Engine)* z maszyną stanów, trwałym buforem logów i możliwością rozłączania/ponownego łączenia klientów.
  3. *Wbudowany magazyn stanu trwałego (`redb`)* w Rust eliminujący potrzebę zewnętrznych baz danych.
  4. *Semantyczna warstwa zasobów* (Projekty, ustrukturyzowane Git JSON API, tożsamość węzłów).
  5. *Pełna wsteczna zgodność* z dotychczasowymi endpointami v2.6.x.

---

## 3. Podjęta Decyzja

Wybrano **Opcję 2**: Przekształcenie węzła Antigravity Mesh z prostego serwera RPC w platformę orkiestracji zasobów i zadań.

### Kluczowe założenia projektowe:

1. **Jawna deklaracja możliwości węzła (Capability Contract)**:
   Każdy węzeł eksponuje standaryzowany kontrakt `/api/v1/node` zawierający identyfikator, metadane sprzętowe oraz zestaw flag możliwości (`filesystem`, `process_exec`, `agent`, `gpu`, `tasks`). Klienci dynamicznie dostosowują interfejs do możliwości maszyny.

2. **Asynchroniczny cykl życia zadania (Task State Machine)**:
   Operacje długotrwałe są rejestrowane jako zadania (`Task`) o stanach: `QUEUED` ➔ `RUNNING` ➔ `COMPLETED` | `FAILED` | `CANCELLED`. Wyniki oraz strumienie stdout/stderr są buforowane na węźle, umożliwiając klientom mobilnym ich bezpieczny odbiór nawet po całkowitym zerwaniu i wznowieniu sesji.

3. **Wbudowana baza stanu (`redb`)**:
   Do zarządzania historią zadań, artefaktami i stanem węzła przyjęto bibliotekę `redb` napisaną w czystym Rust (zero zależności C/C++, wysoka wydajność ACID, brak narzutu administracyjnego).

4. **Polityki wykonawcze (Execution Policies)**:
   Wprowadzenie polityk bezpieczeństwa dla egzekucji poleceń:
   - `SAFE`: dopuszczenie wyłącznie zdefiniowanej listy narzędzi deweloperskich (`git`, `cargo`, `gradle`, `npm`, `python`).
   - `NORMAL`: blokada komend destrukcyjnych (`rm -rf`, `shutdown`, `format`).
   - `UNRESTRICTED`: pełny dostęp administracyjny (na żądanie).

5. **Warstwa Wstecznej Zgodności (Zero Regression Guarantee)**:
   Istniejące endpointy v2.6.x (`/health`, `/system`, `/query`, `/read-file`, `/exec`, `/ask/stream`) pozostają w pełni obsługiwane, gwarantując bezproblemowe działanie dotychczasowych wersji aplikacji Android i skryptów klienckich.

---

## 4. Konsekwencje

* **Pozytywne:**
  - **Odporność na niestabilne łącza mobilne**: Zadania zlecone z telefonu nie są przerywane po wygaszeniu ekranu, utracie zasięgu czy zmianie sieci (Wi-Fi ➔ LTE).
  - **Czysty, wersjonowany kontrakt API (`/api/v1/`)**: Klient operuje na przewidywalnych zasobach, a nie heurystykach systemowych.
  - **Gotowość pod klaster wielowęzłowy**: Model zadań i możliwości umożliwia inteligentne planowanie (scheduler fan-out) na wiele maszyn w przyszłości.
  - **Bezpieczeństwo**: Granularna kontrola egzekucji poleceń ogranicza ryzyko przypadkowych uszkodzeń środowiska roboczego.

* **Kompromisy:**
  - **Wzrost złożoności węzła**: Konieczność zarządzania cyklem życia zadań w tle i retencją logów.
  - **Utrzymanie stanu**: Węzeł przestaje być całkowicie bezstanowy — wymaga bezpiecznego zarządzania lokalną bazą danych `redb` w katalogu domowym użytkownika.
  - **Konieczność utrzymywania dwóch warstw API**: Równoległa obsługa endpointów legacy v2.6.x oraz nowej ścieżki `/api/v1/`.
