#!/usr/bin/env bash
#
# check-testtags.sh — enforce the testTag contract (MOB-1509).
#
# Bans inline-literal Compose testTags in Android app source: every interactive control must be
# tagged from a `TestTags` constant (single-sourced, byte-identical to iOS AccessibilityID) rather
# than a hand-written string literal. This is the Android mirror of the iOS SwiftLint rule
# `accessibility_identifier_string_literal` (iOS/.swiftlint.yml).
#
# Detekt has no SwiftLint-style regex-rule facility, so this runs as a CI check (android-lint job)
# and a lefthook pre-commit hook — the path the ticket explicitly allows ("... or CI check").
#
# A tag is a VIOLATION when a `.testTag("…")` / `testTag = "…"` argument is a pure string literal
# with no `TestTags` reference. Catalog-referencing interpolation
# (e.g. "${TestTags.Landing.AccountCardRow}_${id}") is allowed.
#
# Pre-existing literals (owned by the migration ticket MOB-1502) live in a baseline so this gates
# only NEW violations — the same ratchet pattern used for detekt (detekt-baseline.xml) and the
# JaCoCo coverage floor. As MOB-1502 migrates a literal to a constant, drop its baseline line.
#
# Usage:
#   scripts/check-testtags.sh                 # verify — exit 1 if any non-baselined literal exists
#   scripts/check-testtags.sh --update-baseline   # regenerate the baseline from the current tree
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$REPO_ROOT/Android/app/src/main"
BASELINE="$REPO_ROOT/Android/config/testtags-baseline.txt"

# Emit "<repo-relative-path>\t<tag>" for every pure-literal testTag (sorted, de-duplicated).
# - matches `.testTag("…"` and `testTag = "…"`
# - excludes TestTags.kt (where the constants themselves are declared)
# - excludes any line that references `TestTags` (constant / catalog-derived interpolation)
collect_violations() {
  grep -rnE '(\.testTag\(|testTag[[:space:]]*=)[[:space:]]*"' "$SRC_DIR" --include='*.kt' 2>/dev/null \
    | grep -v 'TestTags' \
    | while IFS= read -r line; do
        file="${line%%:*}"
        rel="${file#"$REPO_ROOT"/}"
        tag="$(printf '%s\n' "$line" | sed -E 's/.*testTag[^"]*"([^"]*)".*/\1/')"
        printf '%s\t%s\n' "$rel" "$tag"
      done \
    | sort -u
}

if [[ "${1:-}" == "--update-baseline" ]]; then
  {
    echo "# testTag inline-literal baseline (MOB-1509). Pre-existing literals awaiting migration"
    echo "# to TestTags constants under MOB-1502. Format: <path>\\t<tag>. Remove a line once the"
    echo "# literal is replaced by a constant. Regenerate: scripts/check-testtags.sh --update-baseline"
    collect_violations
  } > "$BASELINE"
  echo "Wrote baseline: $BASELINE ($(grep -cvE '^#' "$BASELINE") entries)"
  exit 0
fi

# Baseline pairs (comments/blank lines stripped).
baseline_pairs="$(grep -vE '^\s*#|^\s*$' "$BASELINE" 2>/dev/null || true)"

new_violations=""
while IFS= read -r pair; do
  [[ -z "$pair" ]] && continue
  if ! grep -qxF "$pair" <<<"$baseline_pairs"; then
    new_violations+="$pair"$'\n'
  fi
done <<<"$(collect_violations)"

if [[ -n "${new_violations//[$'\n']/}" ]]; then
  echo "✗ Inline-literal testTag(s) found — tag from a TestTags constant, not a string literal."
  echo "  (Android mirror of iOS SwiftLint accessibility_identifier_string_literal; see"
  echo "   Android/docs/accessibility-testtags-guide.md.)"
  echo ""
  printf '%s' "$new_violations" | while IFS=$'\t' read -r path tag; do
    [[ -z "$path" ]] && continue
    echo "  $path  →  \"$tag\""
  done
  echo ""
  echo "Fix: add a constant to TestTags.kt and reference it, e.g. Modifier.testTag(TestTags.X.Y)."
  exit 1
fi

echo "✓ No new inline-literal testTags."
