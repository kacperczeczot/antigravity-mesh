# 📋 Lista Kontrolna Projektu — Antigravity Mesh

Dokument referencyjny weryfikujący zaawansowane możliwości renderowania Markdown, linków lokalnych oraz integracji z agentem AI.

---

## 🎯 1. Status Węzłów i Komponentów

- [x] **Demon macOS (daemon-rs)** — obsługa SSE, tokenów, uploadu plików i wykonywania komend
- [x] **Aplikacja mobilna (Android Jetpack Compose)** — wykrywanie mDNS, streaming odpowiedzi, eksplorator plików
- [x] **Skaner kodów QR** — szybkie parowanie węzłów i import konfiguracji
- [x] **Bezpieczny upload plików** — domyślne przekierowanie do `~/Downloads` z uprawnieniami systemowymi
- [ ] **Wektorowy renderer diagramów** — obsługa wizualizacji Mermaid bezpośrednio w aplikacji
- [ ] **Wieloplatformowy klient Desktop** — wersja Electron / Tauri z trayem

---

## 🛠️ 2. Przykładowa Konfiguracja Serwisu

```yaml
version: "3.9"
services:
  antigravity-mesh:
    image: antigravity-mesh/daemon:latest
    container_name: mesh-daemon
    restart: unless-stopped
    ports:
      - "8888:8888"
    environment:
      - MESH_NODE_NAME=mac-mini-kacper
      - MESH_PORT=8888
    volumes:
      - ~/.gemini:/root/.gemini
      - ~/Downloads:/root/Downloads
```

---

## 📊 3. Macierz Wydajności i Opóźnień

| Protokół / Endpoint | Średnie Opóźnienie (RTT) | Przepustowość (LAN) | Status |
| :--- | :---: | :---: | :---: |
| `GET /` (Health Check) | 1.8 ms | > 100 MB/s | ✅ Optymalny |
| `POST /ask/stream` (SSE) | 12.4 ms (pierwszy token) | Strumieniowy | ✅ Aktywny |
| `POST /upload` (32 KB chunks) | 4.2 ms | ~ 85 MB/s | ✅ Działa |
| `POST /query` (File Tree) | 8.1 ms | N/A | ✅ Błyskawiczny |

---

## 💡 4. Kluczowe Zasady Architektury

> **Niezawodność i Odporność:**
> Bez względu na to, czy klient mobilny utraci zasięg Wi-Fi, czy demon zostanie zrestartowany, sesja agenta musi zachować swój kontekst i stan w logu JSONL.
>
> Kodowanie to nie tylko pisanie instrukcji dla komputera, to przede wszystkim projektowanie zrozumiałego przekazu dla inżynierów.
>
> — *Edsger W. Dijkstra*

---

> [!TIP]
> Załączone pliki w czacie automatycznie przekazują ścieżkę lokalną do agenta AI, umożliwiając mu natychmiastową analizę kodu lub dokumentów.

> [!NOTE]
> Plik utworzony w celu weryfikacji integracji podglądu dokumentów Markdown w Antigravity Mesh.
