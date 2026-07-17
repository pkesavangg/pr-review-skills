#!/usr/bin/env bash
#
# Mirror the iOS platform-scoped skills into the repo-root .claude/skills/ as
# relative symlinks, so they auto-trigger from the monorepo root regardless of
# the working directory. The single source of truth stays under
# iOS/.claude/skills/ — the root entries are just symlinks that widen scope.
#
# Run this after adding, renaming, or removing an iOS skill.
#
# Usage:
#   scripts/sync-root-skill-links.sh          # create missing links + prune stale ones
#   scripts/sync-root-skill-links.sh --check   # report only; exit 1 if out of sync (CI-safe)
#
# A real root skill that shares a name with an iOS skill (e.g. prepare-simulator-build)
# is left untouched — root always wins over the mirror.
#
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

SRC="iOS/.claude/skills"
DEST=".claude/skills"

check_only=false
if [[ "${1:-}" == "--check" ]]; then check_only=true; fi

missing=()
created=()
pruned=()

# 1) Ensure every iOS skill has a matching root symlink.
for dir in "$SRC"/*/; do
  name=$(basename "$dir")
  link="$DEST/$name"
  # Skip if something already occupies the name — an existing symlink OR a real
  # root skill of the same name (which should win).
  if [[ -e "$link" || -L "$link" ]]; then continue; fi
  if $check_only; then
    missing+=("$name")
  else
    ln -s "../../$SRC/$name" "$link"
    created+=("$name")
  fi
done

# 2) Prune root symlinks we manage that have gone stale (source renamed/removed).
for link in "$DEST"/*; do
  [[ -L "$link" ]] || continue
  target=$(readlink "$link")
  [[ "$target" == ../../"$SRC"/* ]] || continue   # only our managed mirror links
  if [[ ! -e "$link" ]]; then                     # broken → source is gone
    if $check_only; then
      missing+=("(stale) $(basename "$link")")
    else
      rm "$link"
      pruned+=("$(basename "$link")")
    fi
  fi
done

if $check_only; then
  if (( ${#missing[@]} )); then
    echo "Root skill links OUT OF SYNC — run: scripts/sync-root-skill-links.sh"
    printf '  - %s\n' "${missing[@]}"
    exit 1
  fi
  echo "Root skill links in sync."
  exit 0
fi

echo "Synced root skill links: ${#created[@]} created, ${#pruned[@]} pruned."
if (( ${#created[@]} )); then printf '  + %s\n' "${created[@]}"; fi
if (( ${#pruned[@]} )); then printf '  - %s\n' "${pruned[@]}"; fi
