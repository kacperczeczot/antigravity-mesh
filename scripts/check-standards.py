#!/usr/bin/env python3
"""
Repository Standards Verification Script
Enforces architectural and code hygiene rules defined in docs/STANDARDS.md and .agents/rules/project.md.
"""

import os
import sys
import re
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent

# Allowed entries in repository root
ALLOWED_ROOT_FILES = {
    ".editorconfig",
    ".gitattributes",
    ".gitignore",
    ".DS_Store",
    "CHANGELOG.md",
    "README.md",
}

ALLOWED_ROOT_DIRS = {
    ".agents",
    ".git",
    ".github",
    "apps",
    "data",
    "docs",
    "packages",
    "scripts",
}

FORBIDDEN_KOTLIN_PATTERNS = [
    (
        re.compile(r"\.navigationBarsPadding\(\)\s*\.imePadding\(\)"),
        "Stacked .navigationBarsPadding().imePadding() causes layout displacement when keyboard opens. Use WindowInsets.isImeVisible check instead."
    ),
]


def check_root_hygiene() -> list[str]:
    errors = []
    for entry in ROOT_DIR.iterdir():
        name = entry.name
        if entry.is_dir():
            if name not in ALLOWED_ROOT_DIRS:
                errors.append(f"Root hygiene violation: Unauthorized directory '{name}/' in repository root.")
        else:
            if name not in ALLOWED_ROOT_FILES:
                errors.append(f"Root hygiene violation: Unauthorized file '{name}' in repository root. Place documents in docs/ or relevant module.")
    return errors


def check_markdown_breadcrumbs() -> list[str]:
    errors = []
    # Check docs/, scripts/, data/ markdown files
    for md_file in ROOT_DIR.glob("**/*.md"):
        rel_path = md_file.relative_to(ROOT_DIR)
        parts = rel_path.parts

        # Skip root README/CHANGELOG, GitHub issue/PR templates, and build caches
        if len(parts) == 1:
            continue
        if any(ignored in parts for ignored in [".git", ".github", "build", ".gradle", "target", "node_modules"]):
            continue

        try:
            with open(md_file, "r", encoding="utf-8") as f:
                first_line = f.readline().strip()
                # If frontmatter exists, find the line after frontmatter
                if first_line == "---":
                    lines = [line.strip() for line in f]
                    try:
                        end_fm = lines.index("---")
                        # next non-empty line
                        first_line = ""
                        for l in lines[end_fm + 1:]:
                            if l:
                                first_line = l
                                break
                    except ValueError:
                        pass

                if not (first_line.startswith("[") and ">" in first_line):
                    errors.append(
                        f"Markdown breadcrumb missing in '{rel_path}'. First content line must contain breadcrumbs, got: '{first_line[:50]}...'"
                    )
        except Exception as e:
            errors.append(f"Could not read '{rel_path}': {e}")
    return errors


def check_ui_anti_patterns() -> list[str]:
    errors = []
    android_src = ROOT_DIR / "apps" / "android"
    if not android_src.exists():
        return errors

    for kt_file in android_src.glob("**/*.kt"):
        rel_path = kt_file.relative_to(ROOT_DIR)
        try:
            content = kt_file.read_text(encoding="utf-8")
            for pattern, msg in FORBIDDEN_KOTLIN_PATTERNS:
                if pattern.search(content):
                    errors.append(f"UI anti-pattern in '{rel_path}': {msg}")
        except Exception as e:
            errors.append(f"Could not read '{rel_path}': {e}")
    return errors


import subprocess

CONVENTIONAL_COMMIT_REGEX = re.compile(
    r"^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-zA-Z0-9_\-,\s]+\))?:\s.+"
)


def check_changelog_unreleased() -> list[str]:
    errors = []
    changelog = ROOT_DIR / "CHANGELOG.md"
    if not changelog.exists():
        errors.append("Missing CHANGELOG.md in repository root.")
        return errors

    content = changelog.read_text(encoding="utf-8")
    if "## [Unreleased]" not in content:
        errors.append(
            "CHANGELOG.md is missing '## [Unreleased]' section required by Keep a Changelog standard (1.1.0)."
        )
    return errors


def check_recent_commits() -> list[str]:
    errors = []
    try:
        res = subprocess.run(
            ["git", "log", "-n", "5", "--format=%s"],
            cwd=ROOT_DIR,
            capture_output=True,
            text=True,
            check=True
        )
        for line in res.stdout.strip().splitlines():
            line = line.strip()
            if not line:
                continue
            if line.startswith("Merge "):
                continue
            if not CONVENTIONAL_COMMIT_REGEX.match(line):
                errors.append(
                    f"Recent commit '{line}' does not follow Conventional Commits (type(scope): subject)."
                )
    except Exception:
        pass
    return errors


FORBIDDEN_TEXT_PATTERNS = [
    (
        re.compile(r"(?i)\b(w stylu|jak w|identycznie jak w?)\s+(Cursor|Antigravity\s+IDE)\b"),
        "Forbidden comparison to external products ('w stylu Cursor / Antigravity'). Describe features technically on their own merits."
    ),
]


def check_forbidden_references() -> list[str]:
    errors = []
    # Check CHANGELOG.md and docs/
    files_to_check = [ROOT_DIR / "CHANGELOG.md"] + list((ROOT_DIR / "docs").glob("**/*.md"))
    for file_path in files_to_check:
        if not file_path.exists():
            continue
        try:
            content = file_path.read_text(encoding="utf-8")
            for pattern, msg in FORBIDDEN_TEXT_PATTERNS:
                if pattern.search(content):
                    rel = file_path.relative_to(ROOT_DIR)
                    errors.append(f"Forbidden phrasing in '{rel}': {msg}")
        except Exception as e:
            errors.append(f"Could not read '{file_path}': {e}")
    return errors


def main() -> int:
    print("🔍 [Standards Check] Running automated repository standards verification...")
    all_errors = []

    # 1. Root hygiene
    root_errors = check_root_hygiene()
    if root_errors:
        all_errors.extend(root_errors)
    else:
        print("  ✓ Root directory hygiene: PASS (only allowed files/directories in root)")

    # 2. Markdown breadcrumbs
    md_errors = check_markdown_breadcrumbs()
    if md_errors:
        all_errors.extend(md_errors)
    else:
        print("  ✓ Markdown breadcrumbs: PASS (all nested documentation files have breadcrumbs)")

    # 3. UI/UX anti-patterns
    ui_errors = check_ui_anti_patterns()
    if ui_errors:
        all_errors.extend(ui_errors)
    else:
        print("  ✓ UI/UX code anti-patterns: PASS (no duplicate navigation/IME inset stacking)")

    # 4. Keep a Changelog
    changelog_errors = check_changelog_unreleased()
    if changelog_errors:
        all_errors.extend(changelog_errors)
    else:
        print("  ✓ Keep a Changelog: PASS (## [Unreleased] section present in CHANGELOG.md)")

    # 5. Conventional Commits
    commit_errors = check_recent_commits()
    if commit_errors:
        all_errors.extend(commit_errors)
    else:
        print("  ✓ Conventional Commits: PASS (recent commits follow specification)")

    # 6. Forbidden phrasing / external product references
    phrase_errors = check_forbidden_references()
    if phrase_errors:
        all_errors.extend(phrase_errors)
    else:
        print("  ✓ Professional language: PASS (no external product comparison references)")

    if all_errors:
        print("\n❌ Standards verification FAILED with the following violations:")
        for err in all_errors:
            print(f"  - {err}")
        return 1

    print("\n✅ All standards verified successfully!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
