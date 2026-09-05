#!/usr/bin/env bash
# Antigravity Mesh - Skrypt do zdjęcia blokady Gatekeeper (kwarantanny) na macOS
# Usuwa atrybut com.apple.quarantine oraz odnawia podpis ad-hoc dla pobranych aplikacji lub obrazów DMG.

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

TARGET="${1}"

# Jeśli nie podano celu, szukaj w domyślnych lokalizacjach
if [ -z "$TARGET" ]; then
    if [ -d "/Applications/AntigravityMesh.app" ]; then
        TARGET="/Applications/AntigravityMesh.app"
    elif [ -f "$HOME/Downloads/AntigravityMesh.dmg" ]; then
        TARGET="$HOME/Downloads/AntigravityMesh.dmg"
    else
        # Wyszukaj najnowszy pasujący plik w Pobranych
        LATEST_DMG=$(find "$HOME/Downloads" -maxdepth 1 -iname "*AntigravityMesh*.dmg" -type f 2>/dev/null | head -n 1)
        if [ -n "$LATEST_DMG" ]; then
            TARGET="$LATEST_DMG"
        fi
    fi
fi

if [ -z "$TARGET" ] || [ ! -e "$TARGET" ]; then
    echo -e "${RED}Nie znaleziono aplikacji ani obrazu DMG.${NC}"
    echo "Użycie:"
    echo "  $0 [ścieżka_do_pliku.app_lub_dmg]"
    echo ""
    echo "Przykłady:"
    echo "  $0 /Applications/AntigravityMesh.app"
    echo "  $0 ~/Downloads/AntigravityMesh.dmg"
    exit 1
fi

echo -e "Target: ${YELLOW}$TARGET${NC}"

# 1. Usunięcie flagi kwarantanny macOS (Gatekeeper)
echo "Zdejmowanie atrybutów kwarantanny (xattr)..."
xattr -d com.apple.quarantine "$TARGET" 2>/dev/null || true
xattr -cr "$TARGET" 2>/dev/null || true

# 2. Jeśli to aplikacja .app, odnów podpis ad-hoc
if [ -d "$TARGET" ] && [[ "$TARGET" == *.app ]]; then
    echo "Aplikacja .app: odnawianie podpisu lokalnego (codesign ad-hoc)..."
    codesign --force --deep --sign - "$TARGET" 2>/dev/null || true
fi

# 3. Weryfikacja
REMAINING_QUARANTINE=$(xattr -l "$TARGET" 2>/dev/null | grep -i "quarantine" || true)
if [ -z "$REMAINING_QUARANTINE" ]; then
    echo -e "${GREEN}Kwarantanna została pomyślnie zdjęta!${NC}"
    if [[ "$TARGET" == *.app ]]; then
        echo -e "Możesz teraz bezpiecznie uruchomić aplikację komendą:"
        echo -e "  ${YELLOW}open \"$TARGET\"${NC}"
    elif [[ "$TARGET" == *.dmg ]]; then
        echo -e "Możesz teraz otworzyć obraz DMG i przeciągnąć aplikację do Programów:"
        echo -e "  ${YELLOW}open \"$TARGET\"${NC}"
    fi
else
    echo -e "${YELLOW}Ostrzeżenie: Niektóre atrybuty kwarantanny mogą nadal istnieć. Spróbuj uruchomić z sudo:${NC}"
    echo "  sudo $0 \"$TARGET\""
fi
