#!/bin/bash
# Antigravity Mesh - launchd installer for macOS
set -e

PLIST_NAME="com.antigravity.mesh.plist"
PLIST_PATH="$HOME/Library/LaunchAgents/$PLIST_NAME"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PYTHON_BIN="$(which python3)"
TOKEN=$(python3 -c "import json, os; p=os.path.expanduser('~/.gemini/mesh_nodes.json'); print(json.load(open(p))['local-mac']['token'] if os.path.isfile(p) else 'default-token')")

mkdir -p "$HOME/Library/LaunchAgents"

cat << PLIST > "$PLIST_PATH"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.antigravity.mesh</string>
    <key>ProgramArguments</key>
    <array>
        <string>$PYTHON_BIN</string>
        <string>$PROJECT_DIR/daemon/server.py</string>
        <string>--host</string>
        <string>0.0.0.0</string>
        <string>--port</string>
        <string>8888</string>
        <string>--token</string>
        <string>$TOKEN</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/tmp/agy_mesh.log</string>
    <key>StandardErrorPath</key>
    <string>/tmp/agy_mesh.err</string>
</dict>
</plist>
PLIST

echo "Installed launchd service to $PLIST_PATH"
echo "To activate immediately, run: launchctl load -w $PLIST_PATH"
echo "To deactivate, run: launchctl unload $PLIST_PATH"
