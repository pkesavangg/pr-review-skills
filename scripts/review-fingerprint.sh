#!/usr/bin/env bash
#
# Print a stable fingerprint of the entire working tree — tracked changes plus
# untracked, non-ignored files — INDEPENDENT of what is currently staged.
#
# Used by the /self-review pass-marker and the pre-commit gate so that:
#   fingerprint now == fingerprint recorded at the last passing self-review
#   ⇔ nothing has changed since it was reviewed.
# Staging (`git add`) does NOT change the fingerprint; editing any file does.
#
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

# Seed from the real index so git's stat cache is preserved and only files that
# actually changed get re-hashed (keeps this fast on a large monorepo).
cp "$(git rev-parse --git-path index)" "$TMP" 2>/dev/null || true
GIT_INDEX_FILE="$TMP" git add -A >/dev/null 2>&1
GIT_INDEX_FILE="$TMP" git write-tree
