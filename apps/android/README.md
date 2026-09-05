[Strona główna](../../README.md) > [apps](../README.md) > [android](README.md)

---

# Antigravity Mesh - Aplikacja Android 📱🤖

Natywna aplikacja na system Android (Kotlin + Jetpack Compose) umożliwiająca monitorowanie węzłów klastra **Antigravity Mesh** oraz bezpośrednią rozmowę z agentami **Google Antigravity** na maszynach macOS i Windows z poziomu Twojego telefonu!

---

## ✨ Funkcjonalności

1. **Dashboard Klastra (Podgląd Węzłów)**:
   - Podgląd na żywo stanu maszyn: **Mac mini (M4)** oraz **Windows PC (Workstation)**.
   - Pomiary opóźnienia sieciowego (ping ms).
   - Wskaźniki obciążenia procesora (**CPU %**).
   - Wykresy zajętości pamięci RAM (**GB i %**).
   - Podgląd wolnego miejsca na dyskach.

2. **Czat z Agentem (Agent Chat)**:
   - Bezpośrednia rozmowa z agentem Antigravity (`agy`) działającym na wybranym komputerze.
   - Pasek szybkich sugestii (np. *"Sprawdź stan dysków"*, *"Top procesy RAM"*).
   - Formatowanie odpowiedzi (kod, parametry, statusy).

3. **Szybkie Akcje (Quick Actions)**:
   - Wykonywanie predefiniowanych poleceń jednym tapnięciem:
     - Szczegółowa zajętość partycji (`df -h` / `wmic`),
     - Top 5 procesów o największym zużyciu pamięci RAM,
     - Status projektów Git (`git status`),
     - Sprawdzenie wersji kompilatorów i środowisk.

4. **Zero-Touch LAN Scanner**:
   - Przycisk wyszukiwania nowych maszyn w lokalnej sieci Wi-Fi i automatycznego parowania bez przepisywania tokenów.

5. **Auto-aktualizacje z GitHub Releases**:
   - Automatyczne powiadomienie o nowej wersji i bezpieczna instalacja przez `PackageInstaller`.

---

## 🚀 Jak zbudować i zainstalować na telefonie

### Sposób 1: Otwarcie w Android Studio (Rekomendowany)
1. Otwórz **Android Studio**.
2. Wybierz **Open** i wskaż folder `apps/android`.
3. Podłącz telefon z Androidem przez kabel USB (z włączonym debugowaniem USB) lub wybierz urządzenie bezprzewodowe (Wireless Debugging).
4. Kliknij zielony przycisk **Run (Shift + F10)**.
5. Aplikacja zostanie skompilowana i zainstalowana na Twoim telefonie!

### Sposób 2: Kompilacja z wiersza poleceń
```bash
cd apps/android
./gradlew assembleDebug
```
Gotowy plik instalacyjny `.apk` znajdziesz w:
`app/build/outputs/apk/debug/app-debug.apk`
Możesz go przesłać na telefon i zainstalować jednym kliknięciem!

---

## 🌐 Dostęp poza domem (w podróży)
Zainstaluj na telefonie oraz komputerach darmowy **Tailscale**.
Wtedy w aplikacji jako adres hosta podajesz adres IP Tailscale maszyny (np. `100.x.x.x`), co pozwala rozmawiać z agentami domowych komputerów z dowolnego miejsca na świecie na danych mobilnych!
