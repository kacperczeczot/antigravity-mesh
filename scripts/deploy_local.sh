#!/bin/bash
# Antigravity Mesh - Lokalny deploy binarki deweloperskiej (macOS)
# Używaj tego zamiast ręcznego `cp` + restart, żeby uniknąć resetu uprawnień TCC
# Użycie: ./scripts/deploy_local.sh [--no-restart]
set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BINARY_SRC="$REPO_ROOT/apps/daemon-rs/target/release/daemon-rs"
APP_BUNDLE="/Applications/AntigravityMesh.app"
APP_BINARY="$APP_BUNDLE/Contents/MacOS/AntigravityMesh"
NO_RESTART=false

for arg in "$@"; do
    [[ "$arg" == "--no-restart" ]] && NO_RESTART=true
done

if [ ! -f "$BINARY_SRC" ]; then
    echo "❌ Nie znaleziono binarki: $BINARY_SRC"
    echo "   Uruchom najpierw: cargo build --release -p daemon-rs"
    exit 1
fi

if [ ! -d "$APP_BUNDLE" ]; then
    echo "❌ Nie znaleziono app bundle: $APP_BUNDLE"
    exit 1
fi

echo "⏹️  Zatrzymywanie bieżącej instancji AntigravityMesh..."
killall AntigravityMesh 2>/dev/null && sleep 0.5 || true

echo "📂 Kopiowanie binarki do App Bundle..."
cp "$BINARY_SRC" "$APP_BINARY"

echo "✍️  Podpisywanie binarki (zachowanie tożsamości TCC)..."
# --preserve-metadata=identifier zapewnia że identifier pochodzi z Info.plist bundla,
# a nie z linker-signed binarki. Dzięki temu macOS TCC nie traktuje jej jako nowej
# aplikacji i NIE resetuje uprawnień Dostępności ani Pełnego dostępu do dysku.
if codesign --sign - --force --preserve-metadata=identifier,entitlements \
      "$APP_BINARY" 2>/dev/null; then
    echo "   ✅ Podpisano z identifierem bundla."
elif codesign --sign - --force "$APP_BINARY" 2>/dev/null; then
    echo "   ⚠️  Podpisano bez zachowania identifiera (fallback)."
else
    echo "   ⚠️  codesign niedostępny - pomiń (binarki ad-hoc)."
fi

echo "🛡️  Zdejmowanie kwarantanny..."
xattr -d com.apple.quarantine "$APP_BINARY" 2>/dev/null || true

if [ "$NO_RESTART" = false ]; then
    echo "🚀 Uruchamianie zaktualizowanego demona..."
    open "$APP_BUNDLE"
    sleep 1
    if pgrep -x AntigravityMesh >/dev/null; then
        echo "   ✅ Daemon uruchomiony."
    else
        echo "   ⚠️  Daemon nie uruchomił się - sprawdź logi /tmp/antigravity_mesh.log"
    fi
fi

echo ""
echo "✨ Deploy zakończony pomyślnie."
echo "   Jeśli uprawnienia TCC zostały zresetowane (unlikely po tym skrypcie),"
echo "   odznacz i zaznacz AntigravityMesh w:"
echo "   Ustawienia systemowe → Prywatność → Dostępność"
echo "   Ustawienia systemowe → Prywatność → Pełny dostęp do dysku"
