[Strona główna](README.md)

---

# Antigravity Mesh (`agy-mesh`) 🌐🤖

> Rozproszony system węzłów (Peer-to-Peer / Client-Server) umożliwiający bezpośrednią komunikację, synchronizację oraz delegację zadań pomiędzy agentami **Google Antigravity** na wielu maszynach (macOS, Windows, Linux).

---

## 🎯 Główne Cele Projektu

1. **Współpraca Międzysystemowa w Czasie Rzeczywistym**:
   - Agent na Macu może bezpośrednio odpytywać uśpionego agenta na Windowsie o pliki, stan dysków, status kompilacji lub backupów.
   - Płynny transfer danych i informacji bez konieczności przełączania stanowiska pracy przez użytkownika.
2. **Standard Model Context Protocol (MCP)**:
   - Każdy węzeł eksponuje bezpieczny interfejs MCP lub REST API, integrując się natywnie z Antigravity jako zestaw wbudowanych narzędzi.
3. **Autonomia i Zerowy Narzut (Zero-Overhead Daemon)**:
   - Działa jako cichy serwis systemowy (`launchd` na macOS, Windows Service / Task Scheduler na Windows, `systemd` na Linux).
   - Zużywa 0% CPU w trybie czuwania.
4. **Bezpieczeństwo Sieci Lokalnej**:
   - Uwierzytelnianie tokenem PSK (Pre-Shared Key) lub mTLS.
   - Idealnie współpracuje z sieciami lokalnymi oraz nakładkowymi VPN (np. Tailscale, ZeroTier).

---

## 🏗️ Architektura Monorepo

```text
antigravity-mesh/
├── apps/
│   ├── android/          # Aplikacja mobilna Jetpack Compose (kokpit, czat, auto-update)
│   ├── daemon-rs/        # Natywny demon Rust z zasobnikiem systemowym (tray)
│   └── daemon-py/        # Serwer MCP / HTTP JSON-RPC węzła
├── packages/
│   ├── client/           # Klient Python oraz CLI (agy-mesh ping, ask, exec)
│   └── skill/            # Antigravity Skill
├── data/
│   └── config/           # Szablony konfiguracji (nodes.example.json)
├── docs/                 # Dokumentacja, standardy inżynieryjne i rejestr ADR
└── scripts/              # Instalatory autostartu (macOS launchd, Windows task)
```

---

## 🚀 Szybki Start (Zero-Touch LAN Pairing)

### 1. Uruchomienie Węzła (Node Daemon)
- **macOS**: Pobierz `AntigravityMesh.dmg` z [GitHub Releases](https://github.com/kacperczeczot/antigravity-mesh/releases), przeciągnij do `/Applications` i uruchom. Aplikacja dodaje ikonę do tacki systemowej i obsługuje autostart przy logowaniu (*Launch at Login*).
- **Windows**: Uruchom `AntigravityMesh-Windows.exe` lub skrypt `scripts\run_windows.bat` (obsługa autostartu przez rejestr Windows).
- **Python (Referencyjny serwer MCP / JSON-RPC)**:
```bash
python apps/daemon-py/server.py
```

### 2. Automatyczne parowanie węzłów (Zero-Touch)
Z drugiej maszyny (np. Mac):
```bash
# Szybkie przeskanowanie sieci LAN:
python3 packages/client/cli.py scan

# Automatyczne sparowanie i wymiana tokenów:
python3 packages/client/cli.py pair 192.168.1.50
```
*Tokeny zostaną wymienione i zapisane na obu komputerach automatycznie w `~/.gemini/mesh_nodes.json`.*

### 3. Zapytanie z Agenta (lub CLI)
```bash
python3 packages/client/cli.py ping --node windows-pc
python3 packages/client/cli.py system --node windows-pc
python3 packages/client/cli.py query "C:\Projects" --depth 2 --node windows-pc
python3 packages/client/cli.py exec "nvidia-smi" --node windows-pc
python3 packages/client/cli.py ask "Jaki jest stan kompilacji projektu?" --node windows-pc
```
Lub z poziomu Pythona:
```python
import sys
sys.path.append("packages/client")
from mesh_client import MeshClient

node = MeshClient.from_node("windows-pc")
print(node.query_files(path="C:\\Projects", max_depth=2))
```

---

## 📱 Aplikacja Mobilna (Android) & Auto-aktualizacje
 
W katalogu [`apps/android/`](apps/android/README.md) znajduje się natywna aplikacja w Jetpack Compose w stylistyce Google Antigravity umożliwiająca:
- **Zdalny Eksplorator Plików i Podgląd Kodu (v2.0)**: Przeglądanie katalogów i bezpieczny podgląd plików źródłowych ze stacji roboczych z numeracją linii, czcionką monospace i kopiowaniem do schowka.
- **Interaktywne Linki Markdown i Integracja AI (v2.0)**: Kliknięcie w linki `file:///...` w wypowiedziach agenta natychmiast otwiera podgląd pliku nad czatem z automatycznym podświetleniem docelowej linii (np. `#L42`). Przycisk *„Zapytaj agenta”* pozwala zlecić analizę wskazanego pliku.
- **Natychmiastowe Zatrzymanie (⏹ STOP) i Kopiowanie Kodu (v2.0)**: Błyskawiczne anulowanie generowania z ubijaniem procesów na komputerze oraz kopiowanie bloków kodu jednym dotknięciem.
- **Wyszukiwarka i Filtry Klastra (v2.0)**: Szybkie przeszukiwanie węzłów oraz filtrowanie (`Wszystkie`, `Online`, `Przypięte ⭐`).
- **Live Monitoring Klastra**: Samoczynne odświeżanie w czasie rzeczywistym (ping, CPU %, RAM GB/%) co 4s.
- **Real-time SSE Streaming**: Prowadzenie rozmów z podglądem na żywo aktualnie wykonywanych przez agenta operacji powłoki i narzędzi.
- **Aliasy, Edycja Parametrów i Przypinanie (📌)**: Nadawanie własnych nazw, korekta adresu IP/portu i przypinanie kluczowych maszyn na szczycie listy.
- **Skanowanie i automatyczne parowanie węzłów** w sieci LAN oraz obsługa sieci VPN (Tailscale).
- **Płynna nawigacja**: Pełna integracja z systemowym gestem/przyciskiem cofania.
 
### 🔄 Auto-aktualizacje (GitHub Releases)
- Aplikacja automatycznie sprawdza dostępność nowych wydań na GitHubie za pomocą bezpośredniego manifestu `android-latest.json`.
- W przypadku dostępności nowszej wersji wyświetla estetyczny dialog z informacją o numerze wersji, liście zmian oraz paskiem postępu pobierania.
- Bezpieczna instalacja realizowana jest poprzez systemowe API `PackageInstaller` ze stałym kluczem podpisu APK.

---

## 📄 Licencja
MIT
