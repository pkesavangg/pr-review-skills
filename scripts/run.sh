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
#   Android extra: at step 3 you can instead choose "Export APK to ~/Downloads"
#   to just assemble a debug/release .apk and copy it there — no device required.
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
#   scripts/run.sh android dev apk    # Android + dev, assemble APK → ~/Downloads (no device)
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

# ---- (Android only) action: install on a device, or export an APK ----
# Empty = resolve interactively inside run_android. `apk`/`export` = assemble an
# .apk and copy it to ~/Downloads instead of installing on a device.
ACTION="$(printf '%s' "${3:-}" | tr '[:upper:]' '[:lower:]')"
case "$ACTION" in
  ""|device|install|run) ACTION="" ;;
  apk|export) ACTION=apk ;;
  *) die "invalid action: $ACTION (use: apk)" ;;
esac
[[ "$ACTION" == "apk" && "$PLATFORM" != "android" ]] && die "apk export is Android-only"

printf "\n%s→ platform=%s%s  build=%s%s\n" "$dim" "$green$PLATFORM$reset" "$dim" "$green$BUILD$reset" "$reset"

# Pipe xcodebuild through xcbeautify when available, else raw.
xcrun_build_filter(){ if command -v xcbeautify >/dev/null 2>&1; then xcbeautify; else cat; fi; }

# Install an .app into a booted simulator, bounded by a timeout. `simctl install`
# produces no output and blocks *indefinitely* when the sim's SpringBoard is
# crash-looping (a corrupt CoreSimulator display state — the FBSDisplayMonitor
# boot crash seen on recent runtimes), so cap it and treat a stall as failure.
# Returns non-zero on timeout or a real install error.
sim_install(){
  local dev="$1" app="$2" pid rc watch
  xcrun simctl install "$dev" "$app" & pid=$!
  ( sleep 90; kill "$pid" 2>/dev/null ) & watch=$!
  wait "$pid" 2>/dev/null; rc=$?
  kill "$watch" 2>/dev/null
  return $rc
}

# --------------------------------------------------------------------------
# Android
# --------------------------------------------------------------------------

# Android release builds need google-services.json (a CI-injected secret) + a signing
# config, which a local checkout usually lacks. Offer to fall back to dev instead of a raw
# Gradle stack trace. Adjusts the global BUILD to dev on fallback; callers derive their own
# Gradle task from BUILD afterwards. No-op for dev builds.
android_require_release_or_fallback(){
  [[ "$BUILD" == "production" ]] || return 0
  local gs="$REPO_ROOT/Android/app/google-services.json"
  [[ -f "$gs" ]] && return 0
  printf "\n%s⚠ Android production builds can't run on a local checkout.%s\n" "$bold" "$reset"
  printf "  release requires google-services.json (a CI-injected secret) + a signing config.\n"
  printf "  %sMissing:%s %s\n" "$dim" "$reset" "$gs"
  local ans; read -rp "Build dev (debug) instead? [Y/n]: " ans; ans="${ans:-y}"
  case "$ans" in
    y|Y|yes) BUILD=dev; step "Falling back to dev (debug)" ;;
    *) die "release needs google-services.json — inject the CI secret to build release locally" ;;
  esac
}

# Assemble a standalone APK (no device required) and copy it to ~/Downloads.
export_android_apk(){
  android_require_release_or_fallback

  local task variant
  if [[ "$BUILD" == "dev" ]]; then task=":app:assembleDebug"; variant=debug
  else task=":app:assembleRelease"; variant=release; fi

  step "Building APK ($task) …"
  ( cd "$REPO_ROOT/Android" && ./gradlew "$task" ) || die "gradle $task failed"

  # The project stamps the filename with version + build date via onVariants (see
  # app/build.gradle.kts), so it isn't a fixed app-debug.apk — pick the newest .apk
  # in the variant output dir. androidTest APKs live in a sibling dir, so they're excluded.
  local out_dir="$REPO_ROOT/Android/app/build/outputs/apk/$variant"
  local apk; apk="$(ls -t "$out_dir"/*.apk 2>/dev/null | head -1)"
  [[ -n "$apk" && -f "$apk" ]] || die "no APK found in $out_dir"

  local downloads="$HOME/Downloads"
  [[ -d "$downloads" ]] || mkdir -p "$downloads" || die "couldn't create $downloads"
  local dest="$downloads/$(basename "$apk")"
  cp -f "$apk" "$dest" || die "couldn't copy APK to $downloads"

  printf "%s✓ %s APK → %s%s (%s)\n" "$green" "$BUILD" "$dest" "$reset" "$(du -h "$dest" | awk '{print $1}')"
}

run_android(){
  # Install & launch on a device (default) or just export an APK to ~/Downloads.
  if [[ -z "${ACTION:-}" ]]; then
    echo
    echo "${bold}What do you want to do?${reset}"
    echo "  1) Install & launch on a device ${dim}(default)${reset}"
    echo "  2) Export APK to ~/Downloads ${dim}(no device needed)${reset}"
    read -rp "Select [1]: " a; a="${a:-1}"
    case "$a" in
      1|device|install) ACTION=device ;;
      2|apk|export) ACTION=apk ;;
      *) die "invalid selection: $a" ;;
    esac
  fi
  if [[ "$ACTION" == "apk" ]]; then export_android_apk; return; fi

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

  # Android production = release, which this repo gates behind google-services.json (a
  # CI-injected secret) + a signing config; offer the dev/debug path on a local checkout.
  android_require_release_or_fallback
  local task
  if [[ "$BUILD" == "dev" ]]; then task=":app:installDebug"; else task=":app:installRelease"; fi

  step "Installing ($task) on ${labels[$((sel-1))]} …"

  # Install, teeing Gradle output to a log so we can recognise a signing-certificate collision:
  # a differently-signed build of the same applicationId already on the device (typically the
  # Play Store app) makes Android reject the install with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
  # Offer to uninstall it and retry once, instead of surfacing a raw Gradle stack trace.
  local gradle_log; gradle_log="$(mktemp "${TMPDIR:-/tmp}/run-android.XXXXXX")"
  local attempt rc ans
  for attempt in 1 2; do
    ( cd "$REPO_ROOT/Android" && ANDROID_SERIAL="$serial_sel" ./gradlew "$task" ) 2>&1 | tee "$gradle_log"
    rc=${PIPESTATUS[0]}
    [[ $rc -eq 0 ]] && break

    if [[ $attempt -eq 1 ]] && grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE\|signatures do not match" "$gradle_log"; then
      printf "\n%s⚠ %s is already installed with a different signature%s (likely the Play Store build).\n" \
        "$bold" "$APP_ID_ANDROID" "$reset"
      printf "  Android won't replace a store-signed app with this locally-signed build.\n"
      read -rp "Uninstall it and retry? Local app data is wiped; account data re-syncs on login. [Y/n]: " ans
      case "${ans:-y}" in
        y|Y|yes)
          step "Uninstalling $APP_ID_ANDROID …"
          "$adb" -s "$serial_sel" uninstall "$APP_ID_ANDROID" >/dev/null 2>&1 \
            || { rm -f "$gradle_log"; die "couldn't uninstall $APP_ID_ANDROID — remove it manually and re-run"; }
          continue
          ;;
        *) rm -f "$gradle_log"; die "aborted — uninstall $APP_ID_ANDROID to install a local build on this device" ;;
      esac
    fi

    rm -f "$gradle_log"
    die "gradle $task failed"
  done
  rm -f "$gradle_log"

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
# Physical devices: list every reachable one, not just those with a live tunnel.
# tunnelState is only "connected" while something is actively talking to the phone;
# an idle-but-usable device sits at "disconnected", and CoreDevice raises the tunnel
# on demand at install time. Filtering on == "connected" hid every idle device (the
# "my plugged-in phone isn't listed" reports). Instead key off the transport: a real
# device is on "wired" (USB) or "localNetwork" (Wi-Fi); "sameMachine" is a simulator
# and None means an offline/remembered pairing — both are not selectable here.
for d in (dev.get("result", {}) or {}).get("devices", []) or []:
    hw = d.get("hardwareProperties", {}) or {}
    cp = d.get("connectionProperties", {}) or {}
    if str(hw.get("platform", "")).lower() != "ios":   # skip watch/tv/mac
        continue
    transport = cp.get("transportType")
    if transport not in ("wired", "localNetwork"):     # skip simulators / offline pairings
        continue
    if cp.get("tunnelState") == "unavailable":         # skip unreachable devices
        continue
    udid = hw.get("udid") or d.get("identifier")
    if not udid or udid in sim_udids:                  # skip sims reported here
        continue
    name = (d.get("deviceProperties", {}) or {}).get("name") or "iOS device"
    wireless = transport == "localNetwork"
    tag = "wireless" if wireless else "wired"
    # live+wired first (safest default pick), then live-wireless, then idle, then
    # idle-wireless — all kept ahead of simulators (sort keys 1 and 2 below).
    rank = (0.0 if cp.get("tunnelState") == "connected" else 0.2) + (0.1 if wireless else 0.0)
    rows.append((rank, "device", udid, f"{name}  [device, {tag}]"))
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
    # `simctl boot` returns before the sim's system services (SpringBoard,
    # installd) are up. Installing into a half-booted sim races those services;
    # `bootstatus -b` blocks until boot actually completes.
    xcrun simctl bootstatus "$id_sel" -b >/dev/null 2>&1 || true

    # Sign the simulator build ad-hoc ("Sign to Run Locally"), exactly like Xcode does —
    # do NOT disable signing. CODE_SIGNING_ALLOWED=NO skips the CodeSign phase, so the app
    # ships with no `application-identifier` entitlement; on the Simulator that entitlement
    # is what provides the default Keychain access group. Without it, SecItemAdd fails with
    # errSecMissingEntitlement (-34018), the auth token is silently never stored, and the
    # app bounces back to login ("auto-logout") on the next auth check — while the same
    # build run from Xcode works. Automatic signing + the project's DEVELOPMENT_TEAM prefix
    # embeds application-identifier into the simulator entitlements without needing the real
    # signing certificate (Simulator signing is ad-hoc), so Keychain sessions persist.
    step "Building ($cfg, simulator) …"
    xcodebuild build \
      -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" -configuration "$cfg" \
      -destination "platform=iOS Simulator,id=$id_sel" \
      -derivedDataPath "$DERIVED" \
      -allowProvisioningUpdates \
      | xcrun_build_filter
    [[ ${PIPESTATUS[0]} -eq 0 ]] || die "xcodebuild failed"

    local app="$DERIVED/Build/Products/$cfg-iphonesimulator/$IOS_APP_NAME.app"
    [[ -d "$app" ]] || die "built app not found at $app"

    # `simctl install` hangs forever against a crash-looping SpringBoard (a
    # corrupt CoreSimulator state), so it's bounded by a timeout in sim_install.
    # If it stalls, fully restart CoreSimulator + reboot the device to clear the
    # bad state, then retry once — instead of the script silently hanging after
    # "Installing & launching …" without ever installing anything.
    step "Installing & launching …"
    local attempt
    for attempt in 1 2; do
      if sim_install "$id_sel" "$app"; then break; fi
      [[ $attempt -eq 2 ]] && die "simulator install failed twice — the sim looks unhealthy; erase it with: xcrun simctl erase $id_sel"
      printf "\n%s⚠ install stalled — the simulator looks unhealthy (SpringBoard may be crash-looping).%s\n" "$bold" "$reset"
      step "Restarting CoreSimulator and rebooting the device …"
      xcrun simctl shutdown "$id_sel" 2>/dev/null || true
      osascript -e 'quit app "Simulator"' >/dev/null 2>&1 || true
      killall -9 com.apple.CoreSimulator.CoreSimulatorService 2>/dev/null || true
      sleep 2
      open -a Simulator >/dev/null 2>&1 || true
      xcrun simctl boot "$id_sel" 2>/dev/null || true
      xcrun simctl bootstatus "$id_sel" -b >/dev/null 2>&1 || true
    done

    xcrun simctl launch "$id_sel" "$BUNDLE_ID_IOS" \
      || die "app installed but launch failed — open meApp from the simulator home screen"
  else
    step "Building ($cfg, device) …"
    # A cold `xcodebuild` only waits ~30s for the device's dev-services tunnel to
    # come up, whereas Xcode keeps the device warm and waits far longer. On a
    # just-reconnected or briefly-locked phone that 30s isn't enough and the build
    # dies with "Timed out waiting for all destinations". Give it Xcode-like
    # patience so the script is as reliable as building from the IDE.
    local build_log; build_log="$(mktemp "${TMPDIR:-/tmp}/run-ios.XXXXXX")"
    xcodebuild build \
      -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" -configuration "$cfg" \
      -destination "id=$id_sel" \
      -destination-timeout 120 \
      -derivedDataPath "$DERIVED" \
      -allowProvisioningUpdates \
      2>&1 | tee "$build_log" | xcrun_build_filter
    local rc=${PIPESTATUS[0]}
    if [[ $rc -ne 0 ]]; then
      # Classify the failure instead of blaming signing for everything — a locked
      # device, a not-yet-ready tunnel, and a missing team are entirely different
      # problems and each needs a different fix.
      if grep -q "Development services need to be enabled\|Ensure that the device is unlocked\|Timed out waiting for all destinations\|is not available because" "$build_log"; then
        rm -f "$build_log"
        die "device wasn't ready — unlock the iPhone and keep it unlocked, confirm Developer Mode is on (Settings ▸ Privacy & Security ▸ Developer Mode), then re-run. (This is usually transient: the device just needs to be warm.)"
      elif grep -q "requires a development team\|No signing certificate\|No profiles for\|Signing for .* requires" "$build_log"; then
        rm -f "$build_log"
        die "signing failed — set a valid Team for the meApp target in Xcode (Signing & Capabilities), then re-run"
      fi
      rm -f "$build_log"
      die "xcodebuild failed (see output above)"
    fi
    rm -f "$build_log"

    local app="$DERIVED/Build/Products/$cfg-iphoneos/$IOS_APP_NAME.app"
    [[ -d "$app" ]] || die "built app not found at $app"

    step "Installing & launching on device …"
    xcrun devicectl device install app --device "$id_sel" "$app"
    xcrun devicectl device process launch --device "$id_sel" "$BUNDLE_ID_IOS"
  fi
  printf "%s✓ launched %s (%s)%s\n" "$green" "$BUNDLE_ID_IOS" "$cfg" "$reset"
}

if [[ "$PLATFORM" == "android" ]]; then run_android; else run_ios; fi
