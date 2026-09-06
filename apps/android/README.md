[Strona główna](../../README.md) > [apps](../README.md) > [android](README.md)

---

# Antigravity Mesh - Aplikacja Android 📱🤖

Natywna aplikacja na system Android (Kotlin + Jetpack Compose) umożliwiająca monitorowanie węzłów klastra **Antigravity Mesh** oraz bezpośrednią rozmowę z agentami **Google Antigravity** na maszynach macOS i Windows z poziomu Twojego telefonu!

---

## ✨ Funkcjonalności

1. **Dashboard Klastra (Live Monitoring w czasie rzeczywistym)**:
   - Ciągły, samoczynny monitoring (co 4s) stanu maszyn: ping (ms), obciążenie procesora (**CPU %**) i pamięci operacyjnej (**RAM GB i %**).
   - Inteligentne oszczędzanie baterii – pętla odświeżania działa wyłącznie, gdy ekran jest aktywny na pierwszym planie.
   - Ochrona czytelności: szerokość kafelków statusu i zabezpieczenie przed łamaniem tekstu.

2. **Czat z Agentem (Real-time SSE Streaming)**:
   - Bezpośrednia rozmowa z agentem Antigravity (`agy`) działającym na wybranym komputerze.
   - Podgląd na żywo aktualnego kroku agenta (np. *"Wykonywanie run_command: git status"*, *"Agent analizuje zapytanie..."*).
   - Bogate formatowanie Markdown (nagłówki, tabele z poziomym scrollem, bloki kodu z kolorowaniem składni).
   - Płynna obsługa nawigacji – naciśnięcie systemowego przycisku/gestu wstecz natychmiast wraca do listy maszyn.

3. **Zarządzanie Węzłami (Aliasy i Przypinanie)**:
   - **Własne nazwy urządzeń (✏️)**: Możliwość nadania czytelnej nazwy każdemu komputerowi (np. *"Mój Mac Studio"*, *"Serwer Linux"*).
   - **Przypinanie na górę (📌)**: Ulubione i najważniejsze maszyny zawsze na samej górze listy oraz na początku paska czatu.
   - **Ręczne dodawanie i usuwanie**: Obsługa bezpośrednich adresów IP/portów oraz sieci Tailscale (CGNAT).

4. **Zero-Touch LAN Scanner**:
   - Przycisk wyszukiwania nowych maszyn w lokalnej sieci Wi-Fi i automatycznego parowania bez przepisywania tokenów.

5. **Stylistyka i Logo Google Antigravity**:
   - Spójny design system: głęboki obsydianowy canvas (`#080B14`), neonowy cyjan i fiolet, nowoczesne karty i dedykowane logo klastra.

6. **Auto-aktualizacje z GitHub Releases**:
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
