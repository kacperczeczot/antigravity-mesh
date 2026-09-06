[Strona główna](../../README.md) > [apps](../README.md) > [android](README.md)

---

# Antigravity Mesh - Aplikacja Android 📱🤖

Natywna aplikacja na system Android (Kotlin + Jetpack Compose) umożliwiająca monitorowanie węzłów klastra **Antigravity Mesh** oraz bezpośrednią rozmowę z agentami **Google Antigravity** na maszynach macOS i Windows z poziomu Twojego telefonu!

---

## ✨ Funkcjonalności
 
1. **Dwukierunkowa Wymiana Plików i Bogate Podglądy (Nowość v2.1.0)**:
   - **Wgrywanie z telefonu na komputer**: Przycisk *„Wgraj plik”* na pasku narzędzi eksploratora uruchamia natywny selektor Androida (`*/*`) i strumieniuje wybrany plik bezpośrednio do bieżącego folderu na komputerze ze wskaźnikiem postępu.
   - **Szybkie pobieranie 1-kliknięciem**: Bezpośredni przycisk pobierania przy każdym pliku na liście – natychmiastowy zapis do systemowego folderu *Pobrane* (`Downloads`) w tle.
   - **Wbudowany Odtwarzacz Audio**: Natywne odtwarzanie nagrań i muzyki (`.mp3`, `.wav`, `.ogg`, `.m4a`, `.aac`, `.flac`) z paskiem przewijania, przeskokami ±10s i licznikami czasu.
   - **Natywna Przeglądarka PDF**: Wyświetlanie stron dokumentów w wysokiej rozdzielczości z nawigacją (`Strona X z Y`) dzięki wbudowanemu silnikowi `PdfRenderer`.
   - **Przeglądarka Obrazów i Plików Binarnych**: Podgląd grafik (`.png`, `.jpg`, `.webp`, itp.) oraz uniwersalna karta plików binarnych z opcją *„Otwórz w aplikacji”* (`FileProvider`).

2. **Zdalny Eksplorator Plików i Podgląd Kodu**:
   - **Przeglądarka plików w sieci**: Pełnoekranowy widok plików i katalogów maszyny (`~` katalog domowy, nawigacja w głąb, `⬆` katalog wyżej).
   - **Jednolinijkowa wyszukiwarka i filtry**: Szybkie filtrowanie plików w czasie rzeczywistym oraz zintegrowane menu sortowania (`Nazwa`, `Data`, `Rozmiar`, foldery na początku, pliki ukryte).
   - **Bezpieczna nawigacja**: Niezależny przycisk wyjścia (`←`) oraz obsługa systemowego gestu powrotu po historii odwiedzonych folderów (`historyStack`).
   - **Rozpoznawanie typów i kolorowanie**: Dedykowana ikonografia dla kodu, plików konfiguracyjnych, markdown, grafik i archiwów.
   - **Zaawansowany podgląd kodu**: Numeracja linii, czcionka o stałej szerokości (monospace), przewijanie w obu osiach, obsługa notcha/pasków gestów oraz kopiowanie całości do schowka z haptyką.
   - **Integracja z Agentem AI**: Przycisk *„🤖 Zapytaj agenta”* przy dowolnym pliku automatycznie otwiera czat z daną maszyną i wysyła prompt z prośbą o analizę pliku!

3. **Interaktywne Linki Markdown i Podgląd w Czacie**:
   - **Klikalne linki `file:///...`**: Kliknięcie w plik podlinkowany przez agenta otwiera modal z kodem bezpośrednio nad rozmową!
   - **Wykrywanie linii docelowej**: Automatyczny skok i wyróżnienie linii (np. `#L42`).
   - **Przejście do Eksploratora**: Opcja natychmiastowego otwarcia folderu pliku w Eksploratorze.
   - **Obsługa linków zewnętrznych**: Bezpieczne otwieranie linków www w przeglądarce telefonu.

3. **Czat z Agentem i Narzędzia Deweloperskie (Udoskonalone w v2.1.1)**:
   - **Załączanie plików w czacie (Spinacz 📎)**: Wgrywanie plików z telefonu na komputer wprost z paska czatu, pasek postępu i autouzupełnianie promptu klikalnym linkiem markdown.
   - **Czysty, kompaktowy nagłówek**: Odchudzony pasek górny dający maksymalną przestrzeń na historię rozmowy.
   - **Szybkie akcje w nagłówku**: Zawsze widoczna ikona przeglądarki plików (`FolderOpen`), eksport rozmowy oraz wyrazisty przycisk czyszczenia czatu (`AccentRed`).
   - **Przycisk natychmiastowego przerwania (⏹ STOP)**: Anuluje strumień i ubija proces agenta na komputerze w ułamku sekundy.
   - **Kopiowanie bloków kodu**: Nagłówki z etykietami języka i przycisk kopiowania pojedynczego bloku do schowka.
   - **Kafelki szybkich akcji**: Gotowe szablony zapytań (status git, testy, obciążenie CPU/RAM, struktura projektu).
   - **Eksport i udostępnianie**: Formatowanie historii do Markdown i systemowy Android Share Sheet.
   - Podgląd na żywo aktualnego kroku agenta w czasie rzeczywistym (SSE).

4. **Dashboard Klastra, Wyszukiwarka i Filtry**:
   - **Filtry i wyszukiwanie**: Szybki filtr po nazwie/IP oraz segmenty: `Wszystkie`, `Online`, `Przypięte ⭐`.
   - **Pełna edycja parametrów węzła**: Możliwość zmiany nazwy, IP i portu urządzenia.
   - Ciągły monitoring (co 4s) stanu maszyn: ping (ms), obciążenie procesora (**CPU %**) i pamięci operacyjnej (**RAM GB i %**).
   - Inteligentne oszczędzanie baterii – pętla odświeżania działa wyłącznie, gdy ekran jest aktywny na pierwszym planie.

5. **Zarządzanie Węzłami i Bezpieczeństwo LAN**:
   - **Bezpieczne parowanie**: Natywny monit potwierdzenia parowania na komputerze lub kod PIN z tacki systemowej.
   - **Zero-Touch LAN Scanner**: Skanowanie sieci lokalnej z ochroną przed wyczerpaniem gniazd TCP (Semaphore).
   - **Przypinanie na górę (📌)**: Ulubione i najważniejsze maszyny zawsze na samej górze listy.

6. **Stylistyka i Logo Google Antigravity**:
   - Spójny design system: głęboki obsydianowy canvas (`#080B14`), neonowy cyjan i fiolet, nowoczesne karty i dedykowane adaptive icons.

7. **Auto-aktualizacje z GitHub Releases**:
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
