#!/usr/bin/env python3
"""
Generuje ustrukturyzowane, czytelne notatki wydania (Release Notes)
na podstawie pliku CHANGELOG.md oraz tabeli bezpośrednich linków do instalatorów.
"""
import sys
import os
import re

def generate_notes(repo: str, tag: str, changelog_path: str = "CHANGELOG.md") -> str:
    version = tag.lstrip("v")
    base_url = f"https://github.com/{repo}/releases/download/{tag}"
    changelog_url = f"https://github.com/{repo}/blob/{tag}/CHANGELOG.md"

    # Extract section from CHANGELOG.md
    highlights = []
    if os.path.exists(changelog_path):
        with open(changelog_path, "r", encoding="utf-8") as f:
            content = f.read()

        pattern = rf"## \[?{re.escape(version)}\]?[^\n]*\n(.*?)(?=\n## |\Z)"
        match = re.search(pattern, content, re.DOTALL)
        if match:
            raw_section = match.group(1).strip()
            current_category = ""
            for line in raw_section.splitlines():
                line = line.strip()
                if line.startswith("###"):
                    cat = line.lstrip("#").strip()
                    current_category = "Dodano" if cat.lower() == "added" else ("Zmieniono" if cat.lower() == "changed" else cat)
                elif line.startswith("-") and current_category:
                    item = line.lstrip("-").strip()
                    highlights.append(f"- **{current_category}** — {item}")
                elif line.startswith("-"):
                    highlights.append(line)

    if not highlights:
        highlights_block = f"Wydanie wersji {version} Antigravity Mesh."
    else:
        highlights_block = "\n".join(highlights)

    notes = f"""### 📦 Pobierz Antigravity Mesh

| System operacyjny | Plik instalacyjny |
| :--- | :--- |
| 🍎 **macOS** (Instalator DMG) | [macOS (.dmg)]({base_url}/AntigravityMesh.dmg) |
| 🍏 **macOS** (Binarka CLI) | [macOS CLI]({base_url}/AntigravityMesh-macOS) |
| 💻 **Windows** (64-bit) | [Windows (.exe)]({base_url}/AntigravityMesh-Windows.exe) |
| 🐧 **Linux** (64-bit) | [Linux]({base_url}/AntigravityMesh-Linux) |
| 🤖 **Android** | [Aplikacja (.apk)]({base_url}/AntigravityMesh.apk) |

---

### 🚀 Highlights — {version}

{highlights_block}

---

Pełna historia zmian: [CHANGELOG.md]({changelog_url})
"""
    return notes

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: build-release-notes.py <repo> <tag> [changelog_path]")
        sys.exit(1)
    
    r = sys.argv[1]
    t = sys.argv[2]
    c = sys.argv[3] if len(sys.argv) > 3 else "CHANGELOG.md"
    print(generate_notes(r, t, c))
