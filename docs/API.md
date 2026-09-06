[Strona główna](../README.md) > [Dokumentacja](README.md) > [API REST & SSE](API.md)

---

# Antigravity Mesh - Specyfikacja API REST & SSE 🌐🤖

Natywny demon węzła (`daemon-rs` oraz referencyjny `daemon-py`) eksponuje bezpieczny interfejs HTTP/JSON-RPC oraz Server-Sent Events (SSE) działający domyślnie na porcie `8888` (lub konfigurowanym parametrem `--port`).

---

## 🔐 Uwierzytelnianie i Bezpieczeństwo

Każde zapytanie (oprócz otwartego punktu parowania `/pair` oraz strony głównej `/` w przeglądarce) wymaga nagłówka autoryzacyjnego **Pre-Shared Key (PSK)**:

```http
X-Mesh-Token: <TWÓJ_TOKEN_PSK>
```

Alternatywnie akceptowany jest standardowy nagłówek Bearer:
```http
Authorization: Bearer <TWÓJ_TOKEN_PSK>
```

---

## 📋 Spis Endpointów

| Metoda | Ścieżka | Opis |
| :--- | :--- | :--- |
| `GET` | `/` | Web Dashboard (HTML) lub podsumowanie węzła (JSON) |
| `GET` | `/health` | Szybki test żywotności węzła (Ping / Liveness) |
| `GET` | `/system` | Telemetria sprzętowa na żywo (CPU, RAM, Dyski, OS) |
| `GET` | `/check-updates` | Sprawdzenie dostępności nowych wydań na GitHubie |
| `POST` | `/update/apply` | Automatyczna bezobsługowa aktualizacja demona w tle |
| `POST` | `/pair` | Zero-Touch LAN Pairing (parowanie kodem PIN lub powiadomieniem) |
| `POST` | `/ask` | Wysłanie zapytania do agenta AI (np. Google Antigravity `agy`) |
| `POST` | `/ask/stream` | Strumieniowanie odpowiedzi agenta w czasie rzeczywistym (SSE) |
| `GET` | `/sessions` | Pobranie historii i identyfikatorów sesji agenta |
| `POST` | `/query` | Zdalna przeglądarka plików (listowanie katalogów i wyszukiwanie) |
| `POST` | `/read-file` | Odczyt fragmentu lub całości pliku tekstowego |
| `GET` / `POST` | `/file-raw` | Strumieniowanie binarne mediów (audio, obrazy, PDF z HTTP 206 Range) |
| `POST` | `/upload` | Przesyłanie plików z telefonu/klienta na dysk komputera |
| `POST` | `/exec` | Zdalne wykonanie polecenia shell na maszynie |

---

## 🛠️ Szczegółowy Opis Endpointów

### 1. `GET /health`
Służy do szybkiego sprawdzania obecności węzła w sieci LAN przez aplikację mobilną i inne węzły.

* **Nagłówki**: `X-Mesh-Token: <token>`
* **Odpowiedź (200 OK)**:
```json
{
  "status": "ok",
  "version": "2.1.1",
  "node_name": "Mac-mini-Kacper",
  "has_ai_cli": true
}
```

---

### 2. `GET /system`
Zwraca szczegółowe dane o wykorzystaniu zasobów maszyny (wykorzystywane przez wykresy w dashboardzie i aplikacji Android).

* **Nagłówki**: `X-Mesh-Token: <token>`
* **Odpowiedź (200 OK)**:
```json
{
  "node_name": "Mac-mini-Kacper",
  "os_name": "macOS",
  "os_version": "26.5.2",
  "os_display": "macOS 26.5.2 (ARM64)",
  "arch": "ARM64",
  "kernel_version": "25.5.0",
  "cpu_count": 10,
  "cpu_brand": "Apple M4",
  "cpu_usage_pct": 8.2,
  "memory": {
    "total_mb": 16384,
    "used_mb": 12572,
    "free_mb": 101,
    "usage_pct": 76.7
  },
  "uptime_secs": 1420,
  "request_count": 48,
  "recent_logs": [
    "[20:35:34] 📡 GET /health -> 200",
    "[20:36:02] 📡 GET / -> 200"
  ],
  "disks": [
    {
      "name": "Macintosh HD",
      "mount_point": "/",
      "total_gb": 228,
      "available_gb": 118
    }
  ],
  "cwd": "/Volumes/MAC_STORAGE_APFS/Developer/GitHub/antigravity-mesh",
  "engine": "rust-native"
}
```

---

### 3. `POST /pair`
Umożliwia bezprzewodowe połączenie nowej aplikacji mobilnej bez ręcznego przepisywania długiego tokena PSK.

* **Nagłówki**: brak wymaganych nagłówków autoryzacyjnych.
* **Ciało żądania (JSON)**:
```json
{
  "client_name": "Pixel 8 Pro",
  "client_id": "android_9a8f7c6e",
  "pin": "482910"
}
```
* **Działanie**:
  - Jeśli podano prawidłowy 6-cyfrowy `pin` (wyświetlany w zasobniku systemowym lub web dashboardzie), żądanie zostaje natychmiast zatwierdzone.
  - Jeśli `pin` nie został podany, na komputerze pojawia się natywny monit systemowy (lub powiadomienie macOS/Windows) z pytaniem o zezwolenie na parowanie.
* **Odpowiedź (200 OK)**:
```json
{
  "token": "4a7b9c...psk_token...",
  "node_name": "Mac-mini-Kacper",
  "port": 8888
}
```

---

### 4. `POST /ask`
Wysyła prompt do lokalnego agenta AI zainstalowanego na maszynie (np. `agy`, `gemini`, `claude`).

* **Nagłówki**: `X-Mesh-Token: <token>`, `Content-Type: application/json`
* **Ciało żądania (JSON)**:
```json
{
  "prompt": "Przeanalizuj ostatnie zmiany w gałęzi main i podsumuj status testów.",
  "session_id": "optional-uuid"
}
```
* **Odpowiedź (200 OK)**:
```json
{
  "reply": "W gałęzi main wszystkie 43 testy jednostkowe zakończyły się sukcesem...",
  "session_id": "9af9ea9c-fd50-479d-8b3c-8f3a2696d792"
}
```

---

### 5. `POST /ask/stream` (Server-Sent Events)
Strumieniuje tokeny odpowiedzi agenta na żywo w miarę ich generowania.

* **Nagłówki**: `X-Mesh-Token: <token>`, `Content-Type: application/json`, `Accept: text/event-stream`
* **Ciało żądania**: takie samo jak w `/ask`.
* **Strumień SSE**:
```http
event: delta
data: {"content": "Analizuję"}

event: delta
data: {"content": " repozytorium..."}

event: done
data: {"session_id": "9af9ea9c-fd50-479d-8b3c-8f3a2696d792"}
```

---

### 6. `POST /query` (Eksplorator Plików)
Listuje zawartość katalogu lub przeszukuje drzewo plików.

* **Nagłówki**: `X-Mesh-Token: <token>`, `Content-Type: application/json`
* **Ciało żądania (JSON)**:
```json
{
  "path": "/Users/kacper/Developer/antigravity-mesh",
  "show_hidden": false
}
```
* **Odpowiedź (200 OK)**:
```json
{
  "current_path": "/Users/kacper/Developer/antigravity-mesh",
  "parent_path": "/Users/kacper/Developer",
  "entries": [
    {
      "name": "apps",
      "is_dir": true,
      "size": 0,
      "modified": 1757189000
    },
    {
      "name": "Cargo.toml",
      "is_dir": false,
      "size": 657,
      "modified": 1757199100
    }
  ]
}
```

---

### 7. `POST /read-file`
Bezpieczny odczyt pliku tekstowego (z podglądem kodu i numeracją linii).

* **Nagłówki**: `X-Mesh-Token: <token>`, `Content-Type: application/json`
* **Ciało żądania (JSON)**:
```json
{
  "path": "/Users/kacper/Developer/antigravity-mesh/README.md",
  "max_lines": 500
}
```
* **Odpowiedź (200 OK)**:
```json
{
  "path": "/Users/kacper/Developer/antigravity-mesh/README.md",
  "content": "# Antigravity Mesh...",
  "total_lines": 121,
  "size_bytes": 7010
}
```

---

### 8. `GET /file-raw` & `POST /file-raw` (Streaming Mediów i Pobieranie)
Strumieniuje surowy strumień binarny pliku. Obsługuje nagłówek `Range: bytes=0-` (HTTP 206 Partial Content) dla odtwarzacza audio i wideo.

* **Parametry URL**: `?path=/sciezka/do/pliku.mp3`
* **Nagłówki**: `X-Mesh-Token: <token>`
* **Odpowiedź**: Strumień binarny z poprawnym nagłówkiem `Content-Type` (np. `audio/mpeg`, `application/pdf`, `image/png`).

---

### 9. `POST /upload` (Wgrywanie Plików)
Przesyła plik z klienta (np. telefonu) i strumieniuje go bezpośrednio na dysk komputera bez buforowania w RAM.

* **Parametry URL**:
  - `?dir=/katalog/docelowy&filename=raport.pdf`
  - lub `?path=/pelna/sciezka/do/pliku.pdf`
* **Nagłówki**:
  - `X-Mesh-Token: <token>`
  - `Content-Type: application/octet-stream`
* **Ciało żądania**: Surowy strumień bajtów pliku.
* **Odpowiedź (200 OK)**:
```json
{
  "ok": true,
  "path": "/katalog/docelowy/raport.pdf",
  "bytes_written": 1048576
}
```

---

### 10. `POST /exec`
Uruchamia polecenie shell na komputerze i zwraca standardowe wyjście oraz kod zakończenia.

* **Nagłówki**: `X-Mesh-Token: <token>`, `Content-Type: application/json`
* **Ciało żądania (JSON)**:
```json
{
  "command": "git status --short",
  "cwd": "/Users/kacper/Developer/antigravity-mesh"
}
```
* **Odpowiedź (200 OK)**:
```json
{
  "stdout": " M Cargo.toml\n",
  "stderr": "",
  "exit_code": 0
}
```

---

### 11. `GET /check-updates` & `POST /update/apply`
Automatyczny system aktualizacji węzła.

* **`GET /check-updates`**:
  Zwraca status dostępności nowszej wersji w serwisie GitHub Releases.
  ```json
  {
    "update_available": true,
    "current_version": "2.1.1",
    "latest_version": "2.2.0"
  }
  ```
* **`POST /update/apply`**:
  Pobiera oficjalną paczkę dla aktualnego systemu (macOS/Windows/Linux), podmienia binarkę w `/Applications/AntigravityMesh.app` (na macOS zdejmując kwarantannę `com.apple.quarantine`) i bezprzestojowo restartuje proces.
  ```json
  {
    "ok": true,
    "message": "Update applied successfully. Daemon restarting."
  }
  ```

---

## 💻 Przykłady Wywołania w Terminalu (cURL)

```bash
# 1. Test żywotności węzła
curl -H "X-Mesh-Token: TWÓJ_TOKEN" http://localhost:8888/health

# 2. Odpytanie o obciążenie CPU i RAM
curl -H "X-Mesh-Token: TWÓJ_TOKEN" http://localhost:8888/system

# 3. Zadanie pytania agentowi AI
curl -X POST -H "X-Mesh-Token: TWÓJ_TOKEN" -H "Content-Type: application/json" \
  -d '{"prompt": "Wyjaśnij strukturę tego projektu"}' \
  http://localhost:8888/ask

# 4. Wgranie pliku na komputer
curl -X POST -H "X-Mesh-Token: TWÓJ_TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @dokument.pdf \
  "http://localhost:8888/upload?dir=.&filename=dokument.pdf"
```
