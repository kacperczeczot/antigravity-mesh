[Strona główna](../README.md) > [scripts](README.md)

---

# Skrypty Pomocnicze (`scripts/`)

Skrypty instalacyjne oraz autostartu dla poszczególnych platform:

| Skrypt | Zastosowanie | Opis |
| :--- | :--- | :--- |
| [`update_macos.sh`](update_macos.sh) | macOS | Automatyczna aktualizacja aplikacji z GitHub Releases jednym kliknięciem |
| [`install_macos.sh`](install_macos.sh) | macOS | Rejestracja usługi w tle przez `launchd` |
| [`unquarantine_macos.sh`](unquarantine_macos.sh) | macOS | Zdjęcie blokady Gatekeeper (kwarantanny) i lokalne podpisanie aplikacji |
| [`install_windows.ps1`](install_windows.ps1) | Windows | Rejestracja zadania w harmonogramie Windows Task Scheduler |
| [`run_windows.bat`](run_windows.bat) | Windows | Bezpośrednie uruchomienie demona węzła |
| [`build-release-notes.py`](build-release-notes.py) | CI/CD | Ekstrakcja notatek wydania z pliku `CHANGELOG.md` dla GitHub Actions |
