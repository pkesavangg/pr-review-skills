#!/usr/bin/env bash
# docs-freshness-check.sh
# Remind which maintained docs may be stale after a source change, by mapping
# changed source paths -> the doc that documents them.
#
#   scripts/docs-freshness-check.sh <file>   # check ONE file (used by the Claude Code hook)
#   scripts/docs-freshness-check.sh          # scan the whole working tree (manual / CI)
#
# Each hit is labelled by change size: NEW FILE (structural) / major / minor.
# In single-file (hook) mode each (doc, tier) is flagged at most once per day so it
# never nags — but a later major/new hit still alerts after an earlier minor one.
# This is a reminder, not a gate — it always exits 0.
#
# The source->doc map lives in doc_for() below. When you add or move a maintained
# doc, update that function (and the same table in the update-architecture skill).

set -uo pipefail

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$ROOT" || exit 0

SINGLE=0
if [ "$#" -ge 1 ] && [ -n "${1:-}" ]; then
  SINGLE=1
  # The hook passes an absolute file_path; normalise to repo-relative.
  FILES="${1#"$ROOT"/}"
else
  FILES=$( { git diff --name-only; git diff --cached --name-only; } 2>/dev/null | sort -u )
fi
[ -z "$FILES" ] && exit 0

# True if the path is a NEW file (untracked or staged-add). architecture.md is a
# structural inventory — it needs a row only when a feature/service/model/DI file is
# ADDED, not when an existing one is edited. Other docs flag on any change.
is_new_file() {
  case "$(git status --porcelain -- "$1" 2>/dev/null | head -1)" in
    '??'*|A*) return 0 ;;
    *)        return 1 ;;
  esac
}

# A change of >= this many added+deleted lines is treated as "major" (documentable).
# Below it is "minor" — reminded softly. Tune to taste.
MAJOR_LINES=20

# Every doc-mapped change also mirrors to the Confluence "meApp - Development" page,
# which tracks architecture + CI/CD & automation. (Test-infra and release changes have
# their own Confluence pages, updated manually — see docs/confluence.md.) The
# /update-confluence skill reads the page and drafts the edit; writes need approval.
CONF_PAGE='meApp - Development (page 1552482315)'

# Total added+deleted lines for a file across unstaged + staged diffs (0 if none/binary).
change_size() {
  { git diff --numstat -- "$1"; git diff --cached --numstat -- "$1"; } 2>/dev/null \
    | awk '{ a=$1; d=$2; if (a=="-") a=0; if (d=="-") d=0; s+=a+d } END { print s+0 }'
}

# Classify a changed file: "new" (structural), "major", or "minor". Echoes "tier|label".
classify() {
  if is_new_file "$1"; then
    printf 'new|NEW FILE — document it'
    return
  fi
  local n; n=$(change_size "$1")
  if [ "${n:-0}" -ge "$MAJOR_LINES" ]; then
    printf 'major|major change (%s lines) — update the doc' "$n"
  else
    printf 'minor|minor change (%s lines) — update only if behaviour/schema changed' "$n"
  fi
}

# Echo the doc a changed path affects, or nothing. High-confidence mappings only —
# over-flagging is cheap (it's a reminder); silently-stale docs are the failure we fix.
doc_for() {
  case "$1" in
    # --- never flag: docs, markdown, build output, and test sources ---
    docs/*|*.md)                                                 return 0 ;;
    *DerivedData*|*SourcePackages*|*/build/*|*/.build/*)         return 0 ;;
    *Tests/*|*/src/test/*|*/src/androidTest/*)                   return 0 ;;
    # --- persistence schema (SwiftData models + Room/proto storage) ---
    iOS/meApp/Domain/Models/DB/*)                                printf 'docs/database-schema.md' ;;
    *data/storage/*|*/main/proto/*)                              printf 'docs/database-schema.md' ;;
    # --- Phase 2 product model ---
    *ProductType*|*ProductSelection*)                            printf 'docs/product-types-current-state.md' ;;
    # --- multi-account switching flow ---
    *AccountSwitch*)                                             printf 'docs/account-switching-flow.md' ;;
    # --- dashboard latest-vs-average graph behaviour ---
    *BaseGraphView*|*BaseSectionViewModel*|*/Managers/Graph/*)   printf 'docs/dashboard-hybrid-latest-vs-average.md' ;;
    # --- repo automation surfaces ---
    .circleci/*)                                                 printf 'docs/circleci.md' ;;
    .lefthook.yml|scripts/*|.claude/*|iOS/.claude/*|Android/.claude/*) printf 'docs/automation.md' ;;
    # --- iOS structural change -> architecture.md (via /update-architecture) ---
    iOS/meApp/Features/*|iOS/meApp/Core/DI/*|iOS/meApp/Data/Services/*|iOS/meApp/Core/Services/ServiceRegistry.swift) printf 'iOS/architecture.md' ;;
    *)                                                           return 0 ;;
  esac
}

STAMP=$(date +%Y%m%d)
HITS=""
SAW_BIG=0
CONF=0
while IFS= read -r f; do
  [ -z "$f" ] && continue
  doc=$(doc_for "$f")
  [ -z "$doc" ] && continue

  info=$(classify "$f")
  tier="${info%%|*}"
  label="${info#*|}"

  # architecture.md is an inventory: only a newly-added structural file warrants a row.
  if [ "$doc" = "iOS/architecture.md" ] && [ "$tier" != "new" ]; then
    continue
  fi

  # Dedup per (doc, tier) per day so a minor note never suppresses a later major/new one.
  if [ "$SINGLE" -eq 1 ]; then
    marker="/tmp/meapp_docflag_${STAMP}_$(printf '%s' "${doc}_${tier}" | tr '/. ' '___')"
    [ -f "$marker" ] && continue
    : > "$marker"
  fi

  [ "$tier" != "minor" ] && SAW_BIG=1
  CONF=1
  HITS="${HITS}   • ${doc}  <-  ${f}   [${label}]"$'\n'
done <<EOF
$FILES
EOF

if [ -n "$HITS" ]; then
  printf '📝 Docs check — these changes map to a maintained doc:\n'
  printf '%s' "$HITS"
  if [ "$SAW_BIG" -eq 1 ]; then
    printf 'A NEW/major change is documentable — update the doc(s) above now (or run /update-architecture).\n'
  else
    printf 'Only minor changes — update the doc(s) above if behaviour/schema actually changed.\n'
  fi
fi

# Confluence mirror: any mapped change also warrants a look at the Development hub page.
# Shown at most once per day in hook mode so it stays a nudge, not a nag.
if [ "$CONF" -eq 1 ]; then
  show_conf=1
  if [ "$SINGLE" -eq 1 ]; then
    conf_marker="/tmp/meapp_confflag_${STAMP}"
    [ -f "$conf_marker" ] && show_conf=0
    : > "$conf_marker"
  fi
  if [ "$show_conf" -eq 1 ]; then
    printf '🌐 Also mirror this to Confluence → %s. Run /update-confluence (it drafts the edit; you approve before it writes).\n' "$CONF_PAGE"
  fi
fi
exit 0
