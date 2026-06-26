#!/usr/bin/env bash
# check-skill-links.sh — flag broken file references in the Claude AI-context.
#
# Scans CLAUDE.md (root/iOS/Android), iOS/.claude/orchestra.md, and every
# SKILL.md / README.md under */.claude/skills/, then verifies that:
#   - markdown links  [text](path)
#   - backticked paths  `path.md` / `path/SKILL.md` / `.claude/...`
# resolve to a real file. Each candidate is tried relative to the containing
# file's dir, the repo root, repo-root/iOS, and repo-root/Android.
#
# Exit 0 = all good, 1 = at least one broken reference. Safe in CI / pre-commit.
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT" || exit 2

# Files to scan (portable to bash 3.2 — no mapfile)
FILES=()
while IFS= read -r line; do
  [ -n "$line" ] && FILES+=("$line")
done < <(
  printf '%s\n' CLAUDE.md iOS/CLAUDE.md Android/CLAUDE.md iOS/.claude/orchestra.md
  find .claude/skills iOS/.claude/skills Android/.claude/skills \
       -type f \( -name 'SKILL.md' -o -name 'README.md' \) 2>/dev/null
)

broken=0
checked=0

is_skippable() {
  # URLs, anchors, placeholders, globs, prose, runtime-output paths — not links to existing files
  case "$1" in
    http://*|https://*|mailto:*|\#*|"") return 0 ;;
    *\{*|*\**|*\$*|*"<"*|*" "*) return 0 ;;
    tasks/*) return 0 ;;          # orchestra writes these at runtime; not pre-existing files
  esac
  return 1
}

resolve() {
  # $1 = candidate path, $2 = dir of the referencing file. Echo "ok" if found.
  local p="$1" base="$2"
  p="${p%%#*}"            # strip anchor
  p="${p#./}"
  for prefix in "$base" "$ROOT" "$ROOT/iOS" "$ROOT/Android"; do
    [ -e "$prefix/$p" ] && { echo ok; return; }
  done
  echo no
}

for f in "${FILES[@]}"; do
  [ -f "$f" ] || continue
  dir="$(dirname "$f")"

  # 1) markdown links [text](target.md) — always checked (a link must resolve)
  grep -oE '\]\([^)]+\)' "$f" 2>/dev/null | while IFS= read -r raw; do
    cand="${raw#\](}"; cand="${cand%)}"
    is_skippable "$cand" && continue
    case "$cand" in *.md) : ;; *) continue ;; esac   # scope: .md links (MOB-1007 ask)
    [ "$(resolve "$cand" "$dir")" = "no" ] && echo "BROKEN(link): $f -> $cand"
  done
  # 2) backticked paths `dir/file.md` — only when they contain a slash (a real path,
  #    not a prose mention of a skill name or a runtime-generated file like SKILL.md)
  grep -oE '`[^`]+`' "$f" 2>/dev/null | while IFS= read -r raw; do
    cand="${raw#\`}"; cand="${cand%\`}"
    case "$cand" in */*) : ;; *) continue ;; esac    # must look like a path
    is_skippable "$cand" && continue
    case "$cand" in *.md) : ;; *) continue ;; esac
    [ "$(resolve "$cand" "$dir")" = "no" ] && echo "BROKEN(path): $f -> $cand"
  done
done > /tmp/_skill_link_report.$$ 2>/dev/null

if [ -s /tmp/_skill_link_report.$$ ]; then
  cat /tmp/_skill_link_report.$$
  broken=$(wc -l < /tmp/_skill_link_report.$$ | tr -d ' ')
fi
rm -f /tmp/_skill_link_report.$$

if [ "$broken" -gt 0 ]; then
  echo "✗ $broken broken reference(s) found."
  exit 1
fi
echo "✓ No broken file references in Claude AI-context."
exit 0
