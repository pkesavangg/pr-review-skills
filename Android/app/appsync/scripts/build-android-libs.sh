#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
# Path resolution (4 levels up from MODULE_DIR):
#   MODULE_DIR = <workspace>/meApp/Android/app/appsync
#   WORK_DIR   = <workspace>
# Assumes wgApp4 repo is checked out as a sibling of meApp.
WORK_DIR="$(cd "${MODULE_DIR}/../../../.." && pwd)"
SRC_DIR="${APPSYNC_SRC_DIR:-${WORK_DIR}/wgApp4/appsync-plugin/src/ios/AppSync/C}"

if [[ ! -d "${SRC_DIR}" ]]; then
  echo "AppSync C source not found at: ${SRC_DIR}" >&2
  echo "Expected layout: <workspace>/meApp/ and <workspace>/wgApp4/ as siblings." >&2
  echo "Set APPSYNC_SRC_DIR to the directory containing jniBridge.c and retry." >&2
  exit 1
fi
JNILIBS_DIR="${MODULE_DIR}/src/main/jniLibs"

MIN_SDK="${MIN_SDK:-21}"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
LOCAL_PROPERTIES="${MODULE_DIR}/../../local.properties"
if [[ -f "${LOCAL_PROPERTIES}" ]]; then
  # Skip comments, trim CR/whitespace; take last uncommented sdk.dir line.
  LOCAL_SDK_DIR="$(sed -n 's/\r$//;/^[[:space:]]*#/d;s/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' \
    "${LOCAL_PROPERTIES}" | tail -n 1)"
fi
case "$(uname -s)" in
  Linux*)   DEFAULT_SDK="${HOME}/Android/Sdk"; DEFAULT_HOST="linux-x86_64" ;;
  MINGW*|MSYS*|CYGWIN*)
            DEFAULT_SDK="${LOCALAPPDATA}/Android/Sdk"; DEFAULT_HOST="windows-x86_64" ;;
  # NDK ships only darwin-x86_64 prebuilts; Apple Silicon runs them via Rosetta 2
  *)        DEFAULT_SDK="${HOME}/Library/Android/sdk"; DEFAULT_HOST="darwin-x86_64" ;;
esac
SDK_ROOT="${LOCAL_SDK_DIR:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${DEFAULT_SDK}}}}"
HOST_TAG="${HOST_TAG:-${DEFAULT_HOST}}"
NDK_BIN="${SDK_ROOT}/ndk/${NDK_VERSION}/toolchains/llvm/prebuilt/${HOST_TAG}/bin"

# Parallel arrays (bash 3.2-compatible): ABI_ORDER[i] ↔ ABI_CC[i].
# 64-bit first (need 16KB alignment), then 32-bit.
ABI_ORDER=(arm64-v8a                                x86_64                                 armeabi-v7a                                     x86)
ABI_CC=(   "aarch64-linux-android${MIN_SDK}-clang"  "x86_64-linux-android${MIN_SDK}-clang" "armv7a-linux-androideabi${MIN_SDK}-clang"      "i686-linux-android${MIN_SDK}-clang")

for i in "${!ABI_ORDER[@]}"; do
  if [[ ! -x "${NDK_BIN}/${ABI_CC[$i]}" ]]; then
    echo "NDK toolchain missing compiler for ${ABI_ORDER[$i]}: ${NDK_BIN}/${ABI_CC[$i]}" >&2
    echo "Set ANDROID_SDK_ROOT/ANDROID_HOME, NDK_VERSION, MIN_SDK, or HOST_TAG and retry." >&2
    exit 1
  fi
done

COMMON_SRC=(
  jniBridge.c
  detectBitString.c
  detectLocation.c
  hamming.c
  image.c
  medianFilter.c
)

for src in "${COMMON_SRC[@]}"; do
  if [[ ! -f "${SRC_DIR}/${src}" ]]; then
    echo "Missing source file: ${SRC_DIR}/${src}" >&2
    exit 1
  fi
done

# Absolute source paths — script is CWD-independent.
ABS_SRC=()
for src in "${COMMON_SRC[@]}"; do
  ABS_SRC+=("${SRC_DIR}/${src}")
done

# Clean only the ABI dirs this script owns (plus legacy armeabi from old PRs).
# Avoids clobbering unrelated files anyone else might drop into jniLibs/.
for abi in "${ABI_ORDER[@]}" armeabi; do
  rm -rf "${JNILIBS_DIR}/${abi}"
done
for abi in "${ABI_ORDER[@]}"; do
  mkdir -p "${JNILIBS_DIR}/${abi}"
done

COMMON_FLAGS=(
  -fPIC
  -std=c99
  -O2
  -DANDROID
  -ffunction-sections
  -fdata-sections
  -fstack-protector-strong
)

LINK_FLAGS=(
  -shared
  -Wl,--gc-sections
  -Wl,-z,relro
  -Wl,-z,now
  -llog
)

LINK_FLAGS_16KB=(
  -Wl,-z,max-page-size=16384
  -Wl,-z,common-page-size=16384
)

cc_for() {
  local target="$1"
  local i
  for i in "${!ABI_ORDER[@]}"; do
    if [[ "${ABI_ORDER[$i]}" == "${target}" ]]; then
      echo "${NDK_BIN}/${ABI_CC[$i]}"
      return
    fi
  done
  echo "Unknown ABI: ${target}" >&2
  exit 1
}

build_one() {
  local abi="$1"
  shift
  local cc out_file
  cc="$(cc_for "${abi}")"
  out_file="${JNILIBS_DIR}/${abi}/libappsync.so"
  "${cc}" "${COMMON_FLAGS[@]}" "${ABS_SRC[@]}" -o "${out_file}" "${LINK_FLAGS[@]}" "$@"
}

# 64-bit ABIs: 16KB page alignment required for Android 15+
build_one arm64-v8a "${LINK_FLAGS_16KB[@]}"
build_one x86_64    "${LINK_FLAGS_16KB[@]}"
# 32-bit ABIs: 4KB pages only, no 16KB alignment needed
build_one armeabi-v7a
build_one x86

READELF="${NDK_BIN}/llvm-readelf"

echo ""
echo "Built AppSync Android libraries (64-bit: 16KB alignment, 32-bit: 4KB alignment):"
for abi in "${ABI_ORDER[@]}"; do
  echo "  ${JNILIBS_DIR}/${abi}/libappsync.so"
  if [[ -x "${READELF}" ]]; then
    "${READELF}" -l "${JNILIBS_DIR}/${abi}/libappsync.so" 2>/dev/null | grep "LOAD" || true
  fi
done
