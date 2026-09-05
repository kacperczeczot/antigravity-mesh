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

## 🏗️ Architektura Projektu

```text
antigravity-mesh/
├── daemon/               # Usługa działająca w tle na maszynie węzłowej
│   ├── server.py         # Serwer HTTP / MCP JSON-RPC
│   ├── tools.py          # Narzędzia: file_query, system_info, task_exec
│   └── auth.py           # Bezpieczeństwo i autoryzacja żądań
├── client/               # Narzędzia klienta dla aktywnego agenta
│   ├── mesh_client.py    # Interfejs odpytywania węzłów
│   └── cli.py            # CLI do testów (agy-mesh ping, agy-mesh ask)
├── skill/                # Antigravity Skill
│   └── SKILL.md          # Instrukcje uczenia agenta korzystania z węzłów
├── config/               # Szablony konfiguracji (nodes.json)
└── scripts/              # Instalatory autostartu
    ├── install_macos.sh  # Konfiguracja launchd
    └── install_windows.ps1 # Konfiguracja Windows Service / Task
```

---

## 🚀 Szybki Start (Zero-Touch LAN Pairing)

### 1. Uruchomienie Węzła (Node Daemon)
Na maszynie docelowej (np. Windows):
```bash
# Wystarczy uruchomić bez żadnych flag - token i konfiguracja utworzą się same!
python daemon/server.py

# Lub na Windowsie kliknij / uruchom skrypt pomocniczy:
scripts\run_windows.bat
```

### 2. Automatyczne parowanie węzłów (Zero-Touch)
Z drugiej maszyny (np. Mac):
```bash
# Szybkie przeskanowanie sieci LAN:
python3 client/cli.py scan

# Automatyczne sparowanie i wymiana tokenów:
python3 client/cli.py pair 192.168.68.51
```
*Tokeny zostaną wymienione i zapisane na obu komputerach automatycznie w `~/.gemini/mesh_nodes.json`.*

### 3. Zapytanie z Agenta (lub CLI)
```bash
python3 client/cli.py ping --node windows-pc
python3 client/cli.py system --node windows-pc
python3 client/cli.py query "C:\Projects" --depth 2 --node windows-pc
python3 client/cli.py exec "nvidia-smi" --node windows-pc
```
Lub z poziomu Pythona:
```python
from client.mesh_client import MeshClient

node = MeshClient.from_node("windows-pc")
print(node.query_files(path="C:\\Projects", max_depth=2))
```


---

## 📄 Licencja
MIT
