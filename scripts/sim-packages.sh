#!/usr/bin/env bash
#
# sim-packages.sh — flip the two SPM packages that don't build on the iOS Simulator
# over to their `simulator-support` branch (and back).
#
#   ggWifiScalePackage      (normal branch: main) ─┐
#   ggBluetoothNativeLibrary (normal branch: dev)  ┴─► simulator-support
#
# These packages ship device-only code (real BLE / Wi-Fi radios) on their normal
# branches, so a Simulator build fails to compile/link them. Each package keeps a
# `simulator-support` branch with stubbed radios so the app builds and runs on the
# Simulator for checking layout across screen sizes.
#
# The branch lives in TWO files, both of which this script edits surgically:
#   iOS/meApp.xcodeproj/project.pbxproj                                  (the requirement)
#   iOS/meApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved  (branch + pinned revision)
#
# IMPORTANT: the simulator-support switch is a LOCAL, BUILD-ONLY change. Never commit
# these two files while in simulator mode — run `restore` first.
#
# Usage:
#   scripts/sim-packages.sh                 # == simulator  (switch to simulator-support)
#   scripts/sim-packages.sh simulator       # switch to simulator-support
#   scripts/sim-packages.sh simulator --resolve   # also run `xcodebuild -resolvePackageDependencies`
#   scripts/sim-packages.sh restore         # switch back to device branches (main / dev)
#   scripts/sim-packages.sh status          # show current branch of each package + mode
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IOS_DIR="$REPO_ROOT/iOS"
PBXPROJ="$IOS_DIR/meApp.xcodeproj/project.pbxproj"
RESOLVED="$IOS_DIR/meApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"

# Keep the backup inside the git dir so it's invisible to `git status` and never committed.
GIT_DIR="$(git -C "$REPO_ROOT" rev-parse --git-dir 2>/dev/null || echo "$REPO_ROOT/.git")"
case "$GIT_DIR" in /*) : ;; *) GIT_DIR="$REPO_ROOT/$GIT_DIR" ;; esac
BACKUP_DIR="$GIT_DIR/sim-packages-backup"

SIM_BRANCH="simulator-support"

# The two packages — name (as it appears in the pbxproj), git URL, Package.resolved
# identity (lowercased), and the branch each uses for normal device builds.
WIFI_NAME="ggWifiScalePackage"
WIFI_URL="https://github.com/gg-engineering/ggWifiScalePackage"
WIFI_IDENTITY="ggwifiscalepackage"
WIFI_DEV_BRANCH="main"

BT_NAME="ggBluetoothNativeLibrary"
BT_URL="https://github.com/gg-engineering/ggBluetoothNativeLibrary"
BT_IDENTITY="ggbluetoothnativelibrary"
BT_DEV_BRANCH="dev"

# ---- pretty output ----
bold=$'\033[1m'; dim=$'\033[2m'; green=$'\033[32m'; red=$'\033[31m'; yellow=$'\033[33m'; reset=$'\033[0m'
step(){ printf "\n%s==>%s %s\n" "$bold" "$reset" "$*"; }
info(){ printf "    %s\n" "$*"; }
die(){ printf "%serror:%s %s\n" "$red" "$reset" "$*" >&2; exit 1; }

command -v python3 >/dev/null 2>&1 || die "python3 not found (required to edit the project files)"
[[ -f "$PBXPROJ" ]]  || die "project.pbxproj not found at $PBXPROJ"
[[ -f "$RESOLVED" ]] || die "Package.resolved not found at $RESOLVED"

in_sim_mode(){ [[ -d "$BACKUP_DIR" ]]; }

# tip URL BRANCH -> first-line sha (empty on failure / branch missing / offline)
tip(){ git ls-remote --heads "$1" "$2" 2>/dev/null | awk 'NR==1{print $1}'; }

# read the branch currently set for a package in the pbxproj definition block
read_pbx_branch(){
  NAME="$1" PBXPROJ="$PBXPROJ" python3 - <<'PY'
import os, re
name = os.environ["NAME"]
with open(os.environ["PBXPROJ"], encoding="utf-8") as f:
    s = f.read()
# Anchor on the DEFINITION block ("NAME" */ = {), not the list / product-dep mentions.
m = re.search(r'XCRemoteSwiftPackageReference "' + re.escape(name)
              + r'" \*/ = \{.*?requirement = \{\s*branch = ([^;]+);', s, re.DOTALL)
print(m.group(1) if m else "?")
PY
}

# apply_edits <pbx_spec> <res_spec>
#   pbx_spec: lines of  NAME<TAB>BRANCH
#   res_spec: lines of  IDENTITY<TAB>BRANCH<TAB>REVISION
apply_edits(){
  PBX_SPEC="$1" RES_SPEC="$2" PBXPROJ="$PBXPROJ" RESOLVED="$RESOLVED" python3 - <<'PY'
import os, re, json, sys

pbx_path, res_path = os.environ["PBXPROJ"], os.environ["RESOLVED"]

# --- project.pbxproj: set `branch = X;` inside each package's requirement block ---
with open(pbx_path, encoding="utf-8") as f:
    pbx = f.read()

for line in os.environ.get("PBX_SPEC", "").splitlines():
    if not line.strip():
        continue
    name, branch = line.split("\t")
    # Anchor on the definition block ("NAME" */ = {) so we don't match the
    # packageReferences list entry or a product dependency with the same name,
    # then lazily reach that block's own `requirement = { branch = ... ;`.
    pat = re.compile(
        r'(XCRemoteSwiftPackageReference "' + re.escape(name)
        + r'" \*/ = \{.*?requirement = \{\s*branch = )([^;]+)(;)',
        re.DOTALL,
    )
    pbx, n = pat.subn(lambda m: m.group(1) + branch + m.group(3), pbx, count=1)
    if n != 1:
        sys.stderr.write("pbxproj: could not find branch requirement for %s\n" % name)
        sys.exit(3)

with open(pbx_path, "w", encoding="utf-8") as f:
    f.write(pbx)

# --- Package.resolved: set state.branch + state.revision for each identity ---
with open(res_path, encoding="utf-8") as f:
    data = json.load(f)

targets = {}
for line in os.environ.get("RES_SPEC", "").splitlines():
    if not line.strip():
        continue
    identity, branch, revision = line.split("\t")
    targets[identity] = (branch, revision)

hits = set()
for pin in data.get("pins", []):
    ident = pin.get("identity")
    if ident in targets:
        branch, revision = targets[ident]
        pin["state"] = {"branch": branch, "revision": revision}
        hits.add(ident)

missing = set(targets) - hits
if missing:
    sys.stderr.write("Package.resolved: identity not found: %s\n" % ", ".join(sorted(missing)))
    sys.exit(3)

# Match Xcode's formatting: 2-space indent, sorted keys, `"key" : value` separator.
out = json.dumps(data, indent=2, sort_keys=True, separators=(",", " : "))
with open(res_path, "w", encoding="utf-8") as f:
    f.write(out + "\n")
PY
}

cmd_status(){
  step "SPM simulator packages — status"
  info "$WIFI_NAME → $(read_pbx_branch "$WIFI_NAME")   (device branch: $WIFI_DEV_BRANCH)"
  info "$BT_NAME → $(read_pbx_branch "$BT_NAME")   (device branch: $BT_DEV_BRANCH)"
  if in_sim_mode; then
    printf "\n    %smode: SIMULATOR%s — backup present; run '%sscripts/sim-packages.sh restore%s' before committing.\n" \
      "$yellow" "$reset" "$bold" "$reset"
  else
    printf "\n    %smode: DEVICE%s — normal branches.\n" "$green" "$reset"
  fi
}

cmd_simulator(){
  local resolve=0
  [[ "${1:-}" == "--resolve" ]] && resolve=1

  step "Switching SPM packages to '$SIM_BRANCH' for Simulator builds"

  info "Resolving tip revisions from GitHub …"
  local wifi_rev bt_rev
  wifi_rev="$(tip "$WIFI_URL" "$SIM_BRANCH")"
  bt_rev="$(tip "$BT_URL" "$SIM_BRANCH")"
  [[ -n "$wifi_rev" ]] || die "branch '$SIM_BRANCH' not found on $WIFI_NAME (or you're offline)."
  [[ -n "$bt_rev"   ]] || die "branch '$SIM_BRANCH' not found on $BT_NAME (or you're offline)."

  # Back up the exact device-branch state ONCE, so `restore` is byte-precise
  # (the dev/main tips can drift from the committed pins, so recompute-on-restore
  # would leave a spurious Package.resolved diff — the backup avoids that).
  if ! in_sim_mode; then
    mkdir -p "$BACKUP_DIR"
    cp "$PBXPROJ"  "$BACKUP_DIR/project.pbxproj"
    cp "$RESOLVED" "$BACKUP_DIR/Package.resolved"
    info "Backed up device-branch project state."
  else
    info "Already in simulator mode — re-applying (existing backup kept)."
  fi

  local pbx_spec res_spec
  pbx_spec="$(printf '%s\t%s\n%s\t%s\n' "$WIFI_NAME" "$SIM_BRANCH" "$BT_NAME" "$SIM_BRANCH")"
  res_spec="$(printf '%s\t%s\t%s\n%s\t%s\t%s\n' \
    "$WIFI_IDENTITY" "$SIM_BRANCH" "$wifi_rev" \
    "$BT_IDENTITY"   "$SIM_BRANCH" "$bt_rev")"
  apply_edits "$pbx_spec" "$res_spec" || die "failed to edit the project files"

  info "$WIFI_NAME → $SIM_BRANCH @ ${wifi_rev:0:10}"
  info "$BT_NAME → $SIM_BRANCH @ ${bt_rev:0:10}"

  if [[ $resolve -eq 1 ]]; then
    step "Resolving package dependencies (xcodebuild) …"
    if xcodebuild -resolvePackageDependencies -project "$IOS_DIR/meApp.xcodeproj" -scheme meApp >/dev/null 2>&1; then
      info "resolved."
    else
      printf "    %s! resolve step failed — Xcode will resolve on the next build.%s\n" "$yellow" "$reset"
    fi
  fi

  printf "\n%s✓ Simulator packages ready.%s Build/run on a Simulator now — e.g. %s./scripts/run.sh ios dev%s\n" \
    "$green" "$reset" "$bold" "$reset"
  printf "%s⚠ Do NOT commit project.pbxproj / Package.resolved with these branches.%s Run %sscripts/sim-packages.sh restore%s when done.\n" \
    "$yellow" "$reset" "$bold" "$reset"
}

cmd_restore(){
  step "Restoring SPM packages to device branches ($WIFI_NAME→$WIFI_DEV_BRANCH, $BT_NAME→$BT_DEV_BRANCH)"
  if in_sim_mode; then
    cp "$BACKUP_DIR/project.pbxproj"  "$PBXPROJ"
    cp "$BACKUP_DIR/Package.resolved" "$RESOLVED"
    rm -rf "$BACKUP_DIR"
    info "Restored the exact pre-switch project state from backup."
  else
    printf "    %sNo backup found — rewriting branches directly (revisions come from current tips and may differ from the committed pins).%s\n" \
      "$yellow" "$reset"
    local wifi_rev bt_rev
    wifi_rev="$(tip "$WIFI_URL" "$WIFI_DEV_BRANCH")"
    bt_rev="$(tip "$BT_URL" "$BT_DEV_BRANCH")"
    [[ -n "$wifi_rev" && -n "$bt_rev" ]] \
      || die "couldn't resolve device-branch revisions (offline?). Restore manually: git checkout -- '$PBXPROJ' '$RESOLVED'"
    local pbx_spec res_spec
    pbx_spec="$(printf '%s\t%s\n%s\t%s\n' "$WIFI_NAME" "$WIFI_DEV_BRANCH" "$BT_NAME" "$BT_DEV_BRANCH")"
    res_spec="$(printf '%s\t%s\t%s\n%s\t%s\t%s\n' \
      "$WIFI_IDENTITY" "$WIFI_DEV_BRANCH" "$wifi_rev" \
      "$BT_IDENTITY"   "$BT_DEV_BRANCH"   "$bt_rev")"
    apply_edits "$pbx_spec" "$res_spec" || die "failed to edit the project files"
  fi
  printf "\n%s✓ Device packages restored.%s\n" "$green" "$reset"
}

ACTION="${1:-simulator}"
[[ $# -gt 0 ]] && shift
case "$ACTION" in
  sim|simulator|on)   cmd_simulator "$@" ;;
  restore|device|off) cmd_restore ;;
  status|state)       cmd_status ;;
  -h|--help|help)     grep '^#' "$0" | sed 's/^# \{0,1\}//' | sed '1d' ;;
  *) die "unknown action: '$ACTION' (use: simulator | restore | status)" ;;
esac
