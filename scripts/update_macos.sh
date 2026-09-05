#!/bin/bash
# Antigravity Mesh - 1-Click Self-Updater for macOS
set -e

echo "🔍 Sprawdzanie najnowszej wersji Antigravity Mesh..."
LATEST_JSON=$(curl -sL https://github.com/kacperczeczot/antigravity-mesh/releases/latest/download/latest.json)
VERSION=$(echo "$LATEST_JSON" | python3 -c "import sys, json; print(json.load(sys.stdin).get('version', ''))")

if [ -z "$VERSION" ]; then
    echo "❌ Nie udało się pobrać informacji o najnowszej wersji."
    exit 1
fi

echo "📦 Najnowsza wersja: v$VERSION"
DMG_URL="https://github.com/kacperczeczot/antigravity-mesh/releases/download/v${VERSION}/AntigravityMesh.dmg"
TMP_DIR=$(mktemp -d)
DMG_PATH="$TMP_DIR/AntigravityMesh.dmg"
MOUNT_DIR="$TMP_DIR/mount"

echo "⬇️ Pobieranie $DMG_URL..."
curl -fL -o "$DMG_PATH" "$DMG_URL"

echo "💿 Montowanie obrazu DMG..."
mkdir -p "$MOUNT_DIR"
hdiutil attach "$DMG_PATH" -mountpoint "$MOUNT_DIR" -nobrowse -quiet

echo "⏹️ Zamykanie bieżącej instancji AntigravityMesh..."
pkill -f AntigravityMesh || true
sleep 1

echo "📂 Kopiowanie AntigravityMesh.app do /Applications..."
rm -rf /Applications/AntigravityMesh.app
cp -R "$MOUNT_DIR/AntigravityMesh.app" /Applications/AntigravityMesh.app

echo "🛡️ Zdejmowanie kwarantanny macOS (Gatekeeper)..."
xattr -rd com.apple.quarantine /Applications/AntigravityMesh.app 2>/dev/null || true

echo "🧹 Odmontowywanie i czyszczenie plików tymczasowych..."
hdiutil detach "$MOUNT_DIR" -quiet || true
rm -rf "$TMP_DIR"

echo "🚀 Uruchamianie zaktualizowanej aplikacji..."
open /Applications/AntigravityMesh.app

echo "✨ Antigravity Mesh został pomyślnie zaktualizowany do wersji v$VERSION!"
