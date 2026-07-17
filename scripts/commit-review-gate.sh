#!/usr/bin/env bash
#
# Commit-time review gate (Layer 2). Blocks a `git commit` made THROUGH Claude
# Code unless /self-review has PASSED on the exact current working tree, and
# tells the user precisely WHY it is blocked:
#   - self-review never ran on these changes
#   - self-review ran but reported NEEDS FIXES (echoes the outstanding items)
#   - self-review passed earlier but files changed since (lists what changed)
#
# Called by the PreToolUse hook in iOS/.claude/settings.json with the git
# command line as $1 (so --no-verify can bypass). Exit 0 = allow, 2 = block.
#
set -uo pipefail

CMD="${1:-}"

# Only gate git commits; ignore everything else.
echo "$CMD" | grep -q 'git commit' || exit 0
# Honor the explicit escape hatch.
echo "$CMD" | grep -q -- '--no-verify' && exit 0

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$ROOT" || exit 0
GITDIR=$(git rev-parse --absolute-git-dir)
MARKER="$GITDIR/self-review-pass"     # stores the reviewed working-tree SHA
REPORT="$GITDIR/self-review-report"   # human-readable last verdict + findings

CURRENT=$("$ROOT/scripts/review-fingerprint.sh" 2>/dev/null)

# Happy path: a pass-marker exists and matches the current tree → reviewed and
# unchanged → allow the commit.
if [ -n "$CURRENT" ] && [ -f "$MARKER" ] && [ "$(cat "$MARKER" 2>/dev/null)" = "$CURRENT" ]; then
  exit 0
fi

# Otherwise block, with a reason specific to the situation.
{
  echo "❌ COMMIT BLOCKED — /self-review has not passed on the current changes."
  echo
  if [ ! -f "$MARKER" ]; then
    if [ -f "$REPORT" ] && grep -q 'NEEDS FIXES' "$REPORT"; then
      echo "Reason: the last /self-review reported NEEDS FIXES. Outstanding items:"
      echo "--------------------------------------------------------------------"
      cat "$REPORT"
      echo "--------------------------------------------------------------------"
      echo "Fix these, then re-run /self-review until it reports PASS."
    else
      echo "Reason: /self-review has not been run on these changes yet."
      echo "Run /self-review — it checks lint, regression, security, issue coverage, and accessibility."
    fi
  else
    echo "Reason: files changed since the last passing /self-review, so it is now stale."
    REVIEWED=$(cat "$MARKER" 2>/dev/null)
    CHANGED=$(git diff --name-only "$REVIEWED" "$CURRENT" 2>/dev/null)
    if [ -n "$CHANGED" ]; then
      echo "Changed since it was reviewed:"
      echo "$CHANGED" | sed 's/^/  - /'
    fi
    echo "Re-run /self-review to review the new state."
  fi
  echo
  echo "Intentional bypass: add --no-verify to the git commit."
} >&2
exit 2
