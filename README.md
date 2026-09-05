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
Na maszynie docelowej (np. Windows):
```bash
# Wystarczy uruchomić bez żadnych flag - token i konfiguracja utworzą się same!
python apps/daemon-py/server.py

# Lub na Windowsie kliknij / uruchom skrypt pomocniczy:
scripts\run_windows.bat
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

W katalogu [`apps/android/`](apps/android/README.md) znajduje się natywna aplikacja w Jetpack Compose umożliwiająca:
- Monitorowanie stanu całego klastra węzłów w sieci LAN
- Skanowanie i automatyczne parowanie węzłów
- Prowadzenie niezależnych rozmów (czatów AI) z każdym urządzeniem z osobna
- Wykonywanie poleceń powłoki (Quick Actions)

### 🔄 Auto-aktualizacje (GitHub Releases)
- Aplikacja automatycznie sprawdza dostępność nowych wydań na GitHubie za pomocą bezpośredniego manifestu `android-latest.json`.
- W przypadku dostępności nowszej wersji wyświetla estetyczny dialog z informacją o numerze wersji, liście zmian oraz paskiem postępu pobierania.
- Bezpieczna instalacja realizowana jest poprzez systemowe API `PackageInstaller` z walidacją pakietu APK.

---

## 📄 Licencja
MIT
