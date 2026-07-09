#!/usr/bin/env bash
#
# run.sh — interactive device runner for meApp (iOS + Android)
#
# Flow when run with no args:
#   1. Pick a platform (iOS / Android)
#   2. Pick a build     (dev [default] / production)
#   3. Pick a connected device from a numbered list
#   4. Build → install → launch the app on that device
#
# Build → config mapping
#   iOS:      dev = "Dev" configuration        production = "Production" configuration
#   Android:  dev = debug build type           production = release build type
#
# Usage:
#   scripts/run.sh                    # fully interactive
#   scripts/run.sh ios                # iOS, then prompt build + device
#   scripts/run.sh android dev        # Android + dev, then prompt device
#   scripts/run.sh ios production     # iOS + production, then prompt device
#
# Notes:
#   - iOS lists both connected physical devices and available simulators.
#   - Android production = release, which requires a signing config; if the
#     project has none configured, installRelease will fail (dev/debug is the
#     safe default for day-to-day work).
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---- project constants (from the Xcode project + Gradle config) ----
IOS_PROJECT="$REPO_ROOT/iOS/meApp.xcodeproj"
IOS_SCHEME="meApp"
IOS_APP_NAME="meApp"                       # PRODUCT_NAME → meApp.app
BUNDLE_ID_IOS="com.gurus.weight"
DERIVED="$REPO_ROOT/iOS/build/run-DerivedData"

APP_ID_ANDROID="com.dmdbrands.gurus.weight"
ANDROID_LAUNCHER=".MainActivity"

# ---- pretty output ----
bold=$'\033[1m'; dim=$'\033[2m'; green=$'\033[32m'; red=$'\033[31m'; reset=$'\033[0m'
step(){ printf "\n%s==>%s %s\n" "$bold" "$reset" "$*"; }
die(){ printf "%serror:%s %s\n" "$red" "$reset" "$*" >&2; exit 1; }

# ---- 1. platform ----
PLATFORM="$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')"
if [[ -z "$PLATFORM" ]]; then
  echo "${bold}Which platform?${reset}"
  echo "  1) iOS"
  echo "  2) Android"
  read -rp "Select [1]: " p; p="${p:-1}"
  case "$p" in
    1|ios) PLATFORM=ios ;;
    2|android) PLATFORM=android ;;
    *) die "invalid platform: $p" ;;
  esac
fi
[[ "$PLATFORM" == "ios" || "$PLATFORM" == "android" ]] || die "unknown platform: $PLATFORM"

# ---- 2. build variant (default: dev) ----
BUILD="$(printf '%s' "${2:-}" | tr '[:upper:]' '[:lower:]')"
if [[ -z "$BUILD" ]]; then
  echo
  echo "${bold}Which build?${reset}"
  echo "  1) dev ${dim}(default)${reset}"
  echo "  2) production"
  read -rp "Select [1]: " b; b="${b:-1}"
  case "$b" in
    1|dev) BUILD=dev ;;
    2|prod|production) BUILD=production ;;
    *) die "invalid build: $b" ;;
  esac
fi
[[ "$BUILD" == "prod" ]] && BUILD=production
[[ "$BUILD" == "dev" || "$BUILD" == "production" ]] || die "unknown build: $BUILD"

printf "\n%s→ platform=%s%s  build=%s%s\n" "$dim" "$green$PLATFORM$reset" "$dim" "$green$BUILD$reset" "$reset"

# Pipe xcodebuild through xcbeautify when available, else raw.
xcrun_build_filter(){ if command -v xcbeautify >/dev/null 2>&1; then xcbeautify; else cat; fi; }

# --------------------------------------------------------------------------
# Android
# --------------------------------------------------------------------------
run_android(){
  local adb; adb="$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")"
  [[ -x "$adb" ]] || die "adb not found (install Android platform-tools)"

  step "Connected Android devices"
  local serials=() labels=() serial model
  while IFS='|' read -r serial model; do
    [[ -z "$serial" ]] && continue
    serials+=("$serial")
    labels+=("${model:-$serial}")
  done < <("$adb" devices -l | awk 'NR>1 && $2=="device"{
      m=""; for(i=1;i<=NF;i++){ if($i ~ /^model:/){ m=substr($i,7) } }
      print $1"|"m }')

  [[ ${#serials[@]} -gt 0 ]] || die "no connected devices/emulators (check: adb devices)"

  local i
  for i in "${!serials[@]}"; do
    printf "  %d) %s  %s(%s)%s\n" "$((i+1))" "${labels[$i]}" "$dim" "${serials[$i]}" "$reset"
  done
  local sel; read -rp "Select device [1]: " sel; sel="${sel:-1}"
  [[ "$sel" =~ ^[0-9]+$ ]] && (( sel>=1 && sel<=${#serials[@]} )) || die "invalid selection"
  local serial_sel="${serials[$((sel-1))]}"

  local task
  if [[ "$BUILD" == "dev" ]]; then task=":app:installDebug"; else task=":app:installRelease"; fi

  step "Installing ($task) on ${labels[$((sel-1))]} …"
  ( cd "$REPO_ROOT/Android" && ANDROID_SERIAL="$serial_sel" ./gradlew "$task" ) \
    || die "gradle $task failed"

  step "Launching $APP_ID_ANDROID …"
  "$adb" -s "$serial_sel" shell am start -n "$APP_ID_ANDROID/$APP_ID_ANDROID$ANDROID_LAUNCHER" >/dev/null \
    || "$adb" -s "$serial_sel" shell monkey -p "$APP_ID_ANDROID" -c android.intent.category.LAUNCHER 1 >/dev/null
  printf "%s✓ launched on %s%s\n" "$green" "${labels[$((sel-1))]}" "$reset"
}

# --------------------------------------------------------------------------
# iOS
# --------------------------------------------------------------------------
run_ios(){
  command -v xcodebuild >/dev/null 2>&1 || die "xcodebuild not found (install Xcode)"
  local cfg; if [[ "$BUILD" == "dev" ]]; then cfg="Dev"; else cfg="Production"; fi

  step "Connected iOS devices & simulators"
  local sim_json dev_json
  sim_json="$(xcrun simctl list devices available --json 2>/dev/null || echo '{}')"
  dev_json="$(xcrun devicectl list devices --quiet --json-output - 2>/dev/null || echo '{}')"

  # kind<TAB>id<TAB>label  — physical devices first, then booted sims, then others
  # (capture into a variable first; a heredoc nested inside <(...) breaks some bash builds)
  local ios_list
  ios_list="$(SIM_JSON="$sim_json" DEV_JSON="$dev_json" python3 <<'PY'
import json, os
def load(v):
    try: return json.loads(os.environ.get(v, "") or "{}")
    except Exception: return {}
sim, dev = load("SIM_JSON"), load("DEV_JSON")
# udids of simulators — devicectl also reports booted sims, so dedupe against these
sim_udids = {s.get("udid") for r, ds in (sim.get("devices", {}) or {}).items()
             for s in (ds or [])}
rows = []  # (sort, kind, id, label)
# physical devices: only those currently connected (tunnelState == connected)
for d in (dev.get("result", {}) or {}).get("devices", []) or []:
    hw = d.get("hardwareProperties", {}) or {}
    cp = d.get("connectionProperties", {}) or {}
    if str(hw.get("platform", "")).lower() != "ios":   # skip watch/tv/mac
        continue
    if cp.get("tunnelState") != "connected":           # skip paired-but-offline
        continue
    udid = hw.get("udid") or d.get("identifier")
    if not udid or udid in sim_udids:                  # skip sims reported here
        continue
    name = (d.get("deviceProperties", {}) or {}).get("name") or "iOS device"
    rows.append((0, "device", udid, f"{name}  [device]"))
# simulators
for runtime, devs in (sim.get("devices", {}) or {}).items():
    if "iOS" not in runtime:
        continue
    os_ver = runtime.split(".")[-1].replace("iOS-", "").replace("-", ".")
    for s in devs or []:
        if not s.get("isAvailable", True):
            continue
        booted = s.get("state") == "Booted"
        tag = " (Booted)" if booted else ""
        rows.append((1 if booted else 2, "sim", s.get("udid"),
                     f"{s.get('name')}  iOS {os_ver} [sim]{tag}"))
for _, kind, ident, label in sorted(rows, key=lambda r: r[0]):
    print(f"{kind}\t{ident}\t{label}")
PY
)"

  local kinds=() ids=() labels=() kind id label
  while IFS=$'\t' read -r kind id label; do
    [[ -z "$id" ]] && continue
    kinds+=("$kind"); ids+=("$id"); labels+=("$label")
  done <<< "$ios_list"

  [[ ${#ids[@]} -gt 0 ]] || die "no simulators or connected devices found"

  local i
  for i in "${!ids[@]}"; do
    printf "  %d) %s  %s(%s)%s\n" "$((i+1))" "${labels[$i]}" "$dim" "${ids[$i]}" "$reset"
  done
  local sel; read -rp "Select device [1]: " sel; sel="${sel:-1}"
  [[ "$sel" =~ ^[0-9]+$ ]] && (( sel>=1 && sel<=${#ids[@]} )) || die "invalid selection"
  local kind_sel="${kinds[$((sel-1))]}" id_sel="${ids[$((sel-1))]}"

  if [[ "$kind_sel" == "sim" ]]; then
    step "Booting simulator …"
    open -a Simulator >/dev/null 2>&1 || true
    xcrun simctl boot "$id_sel" 2>/dev/null || true

    step "Building ($cfg, simulator) …"
    xcodebuild build \
      -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" -configuration "$cfg" \
      -destination "platform=iOS Simulator,id=$id_sel" \
      -derivedDataPath "$DERIVED" \
      CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO \
      | xcrun_build_filter
    [[ ${PIPESTATUS[0]} -eq 0 ]] || die "xcodebuild failed"

    local app="$DERIVED/Build/Products/$cfg-iphonesimulator/$IOS_APP_NAME.app"
    [[ -d "$app" ]] || die "built app not found at $app"

    step "Installing & launching …"
    xcrun simctl install "$id_sel" "$app"
    xcrun simctl launch "$id_sel" "$BUNDLE_ID_IOS"
  else
    step "Building ($cfg, device) …"
    xcodebuild build \
      -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" -configuration "$cfg" \
      -destination "id=$id_sel" \
      -derivedDataPath "$DERIVED" \
      -allowProvisioningUpdates \
      | xcrun_build_filter
    [[ ${PIPESTATUS[0]} -eq 0 ]] || die "xcodebuild failed (device builds need a valid signing team)"

    local app="$DERIVED/Build/Products/$cfg-iphoneos/$IOS_APP_NAME.app"
    [[ -d "$app" ]] || die "built app not found at $app"

    step "Installing & launching on device …"
    xcrun devicectl device install app --device "$id_sel" "$app"
    xcrun devicectl device process launch --device "$id_sel" "$BUNDLE_ID_IOS"
  fi
  printf "%s✓ launched %s (%s)%s\n" "$green" "$BUNDLE_ID_IOS" "$cfg" "$reset"
}

if [[ "$PLATFORM" == "android" ]]; then run_android; else run_ios; fi
