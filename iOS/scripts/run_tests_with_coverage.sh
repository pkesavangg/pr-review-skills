#!/usr/bin/env bash
set -euo pipefail

# Usage:
# 1) Interactive selection
#    CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
# 2) Direct non-interactive run
#    SCHEME="meAppTests 1" DEVICE_ID=<device-udid> CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh

PROJECT_PATH="iOS/meApp.xcodeproj"
CONFIGURATION="${CONFIGURATION:-Production}"
SCHEME="${SCHEME:-}"
DESTINATION="${DESTINATION:-}"
DEVICE_ID="${DEVICE_ID:-}"
DEVICE_NAME="${DEVICE_NAME:-}"
OUTPUT_PREFIX="coverage-report"
RESULT_DIR="iOS/build/coverage"
REPORT_DIR="iOS/meAppTests/Reports"
EXPORT_SCRIPT="iOS/scripts/export_coverage_reports.py"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_PATH="${IOS_ROOT}/meApp.xcodeproj"
RESULT_DIR="${IOS_ROOT}/build/coverage"
REPORT_DIR="${IOS_ROOT}/meAppTests/Reports"
EXPORT_SCRIPT="${IOS_ROOT}/scripts/export_coverage_reports.py"

mkdir -p "${RESULT_DIR}"

list_test_schemes() {
  local list_json
  list_json="$(xcodebuild -list -json -project "${PROJECT_PATH}" 2>/dev/null || true)"
  if [[ -z "${list_json}" ]]; then
    echo "Failed to list schemes for project '${PROJECT_PATH}'" >&2
    exit 1
  fi

  SCHEMES_JSON="${list_json}" python3 - <<'PY'
import json
import os
import re

raw = os.environ.get("SCHEMES_JSON", "")
try:
    data = json.loads(raw)
except Exception:
    raise SystemExit(1)

schemes = data.get("project", {}).get("schemes", []) or []
if not schemes:
    raise SystemExit(1)

test_schemes = [s for s in schemes if "test" in s.lower()]
chosen = test_schemes if test_schemes else schemes

seen = set()
for name in chosen:
    normalized = re.sub(r"\s+\([^)]* project\)$", "", name).strip()
    key = normalized.lower()
    if key in seen:
        continue
    seen.add(key)
    print(normalized)
PY
}

choose_scheme() {
  if [[ -n "${SCHEME}" ]]; then
    return
  fi

  local schemes=()
  local line
  while IFS= read -r line; do
    schemes+=("${line}")
  done < <(list_test_schemes)
  if [[ ${#schemes[@]} -eq 0 ]]; then
    echo "No schemes found in project '${PROJECT_PATH}'." >&2
    exit 1
  fi

  echo "Available test schemes:"
  local i
  for i in "${!schemes[@]}"; do
    printf "  %d) %s\n" "$((i + 1))" "${schemes[$i]}"
  done

  local choice
  read -r -p "Select scheme [1-${#schemes[@]}] (default 1): " choice
  choice="${choice:-1}"

  if ! [[ "${choice}" =~ ^[0-9]+$ ]] || (( choice < 1 || choice > ${#schemes[@]} )); then
    echo "Invalid scheme selection: ${choice}" >&2
    exit 1
  fi

  SCHEME="${schemes[$((choice - 1))]}"
}

list_destinations() {
  local raw
  raw="$(xcodebuild -showdestinations -project "${PROJECT_PATH}" -scheme "${SCHEME}" 2>/dev/null || true)"
  if [[ -z "${raw}" ]]; then
    echo "Unable to resolve destinations for scheme '${SCHEME}'." >&2
    exit 1
  fi

  DESTINATIONS_RAW="${raw}" python3 - <<'PY'
import os
import re

raw = os.environ.get("DESTINATIONS_RAW", "")
entries = []
for body in re.findall(r"\{([^{}]+)\}", raw):
    item = {}
    for piece in body.split(","):
        if ":" not in piece:
            continue
        key, value = piece.split(":", 1)
        item[key.strip()] = value.strip()
    if item:
        entries.append(item)


def valid(entry):
    entry_id = entry.get("id", "")
    return entry_id and "placeholder" not in entry_id


def version_key(value: str):
    nums = [int(part) for part in re.findall(r"\d+", value)]
    return tuple(nums) if nums else (0,)

rows = []
for e in entries:
    platform = e.get("platform", "")
    if platform != "iOS":
        continue
    if "arch" not in e:
        continue
    if not valid(e):
        continue

    name = e.get("name", "Unknown")
    os_version = e.get("OS", "")
    dest_id = e.get("id", "")
    kind = "device"
    rank = 0
    iphone_rank = 0 if "iphone" in name.lower() else 1
    rows.append((rank, iphone_rank, -1 * (version_key(os_version) or (0,))[0], name.lower(), platform, name, os_version, dest_id, kind))

rows = sorted(rows, key=lambda r: (r[0], r[1], r[3]))
for _, _, _, _, platform, name, os_version, dest_id, kind in rows:
    safe_os = os_version if os_version else "-"
    print(f"{platform}|{name}|{safe_os}|{dest_id}|{kind}")
PY
}

choose_destination() {
  if [[ -n "${DESTINATION}" ]]; then
    return
  fi

  local destinations=()
  local line
  while IFS= read -r line; do
    destinations+=("${line}")
  done < <(list_destinations)
  if [[ ${#destinations[@]} -eq 0 ]]; then
    echo "No connected physical iOS devices found for scheme '${SCHEME}'." >&2
    echo "Connect and trust a physical device, then retry." >&2
    exit 1
  fi

  local row platform name os_version dest_id kind

  if [[ -n "${DEVICE_ID}" ]]; then
    for row in "${destinations[@]}"; do
      IFS='|' read -r platform name os_version dest_id kind <<< "${row}"
      if [[ "${dest_id}" == "${DEVICE_ID}" ]]; then
        DESTINATION="platform=${platform},id=${dest_id}"
        return
      fi
    done
    echo "DEVICE_ID '${DEVICE_ID}' not found in available destinations." >&2
    exit 1
  fi

  if [[ -n "${DEVICE_NAME}" ]]; then
    local wanted_name
    wanted_name="$(printf '%s' "${DEVICE_NAME}" | tr '[:upper:]' '[:lower:]')"
    for row in "${destinations[@]}"; do
      IFS='|' read -r platform name os_version dest_id kind <<< "${row}"
      if [[ "$(printf '%s' "${name}" | tr '[:upper:]' '[:lower:]')" == "${wanted_name}" ]]; then
        DESTINATION="platform=${platform},id=${dest_id}"
        return
      fi
    done
    echo "DEVICE_NAME '${DEVICE_NAME}' not found in available destinations." >&2
    exit 1
  fi

  echo "Available physical iOS devices:"
  local i
  for i in "${!destinations[@]}"; do
    IFS='|' read -r platform name os_version dest_id kind <<< "${destinations[$i]}"
    if [[ -n "${os_version}" && "${os_version}" != "-" ]]; then
      printf "  %d) [%s] %s (OS %s) - %s\n" "$((i + 1))" "${kind}" "${name}" "${os_version}" "${dest_id}"
    else
      printf "  %d) [%s] %s - %s\n" "$((i + 1))" "${kind}" "${name}" "${dest_id}"
    fi
  done

  local choice
  read -r -p "Select destination [1-${#destinations[@]}] (default 1): " choice
  choice="${choice:-1}"

  if ! [[ "${choice}" =~ ^[0-9]+$ ]] || (( choice < 1 || choice > ${#destinations[@]} )); then
    echo "Invalid destination selection: ${choice}" >&2
    exit 1
  fi

  IFS='|' read -r platform name os_version dest_id kind <<< "${destinations[$((choice - 1))]}"
  DESTINATION="platform=${platform},id=${dest_id}"
}

choose_scheme

set_report_dir_from_scheme() {
  local lower_scheme
  lower_scheme="$(printf '%s' "${SCHEME}" | tr '[:upper:]' '[:lower:]')"
  if [[ "${lower_scheme}" == *"uitest"* ]]; then
    REPORT_DIR="${IOS_ROOT}/meAppUITests/Reports"
  else
    REPORT_DIR="${IOS_ROOT}/meAppTests/Reports"
  fi
  mkdir -p "${REPORT_DIR}"
}

set_report_dir_from_scheme
choose_destination

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RESULT_BUNDLE_PATH="${RESULT_DIR}/TestResults-${TIMESTAMP}.xcresult"

rm -f "${REPORT_DIR}/${OUTPUT_PREFIX}.md" "${REPORT_DIR}/${OUTPUT_PREFIX}.csv" "${REPORT_DIR}/${OUTPUT_PREFIX}.html"

echo "Using scheme: ${SCHEME}"
echo "Using configuration: ${CONFIGURATION}"
echo "Using destination: ${DESTINATION}"
echo "Result bundle: ${RESULT_BUNDLE_PATH}"

xcodebuild test \
  -project "${PROJECT_PATH}" \
  -scheme "${SCHEME}" \
  -configuration "${CONFIGURATION}" \
  -destination "${DESTINATION}" \
  -enableCodeCoverage YES \
  -resultBundlePath "${RESULT_BUNDLE_PATH}"

python3 "${EXPORT_SCRIPT}" \
  --xcresult "${RESULT_BUNDLE_PATH}" \
  --output-dir "${REPORT_DIR}" \
  --output-prefix "${OUTPUT_PREFIX}"
