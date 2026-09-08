#!/usr/bin/env bash
# Instaluje pre-commit hook w lokalnym repozytorium git.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOKS_DIR="${REPO_ROOT}/.git/hooks"

echo "Instalowanie pre-commit hooka w ${HOOKS_DIR}..."

cat << 'EOF' > "${HOOKS_DIR}/pre-commit"
#!/usr/bin/env bash
set -e

echo "🔍 [Git Hook] Weryfikacja standardów repozytorium..."
python3 scripts/check-standards.py
EOF

chmod +x "${HOOKS_DIR}/pre-commit"
echo "✅ Hook pre-commit zainstalowany pomyślnie."
