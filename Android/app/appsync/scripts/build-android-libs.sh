#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
WORK_DIR="$(cd "${MODULE_DIR}/../../../.." && pwd)"
SRC_DIR="${APPSYNC_SRC_DIR:-${WORK_DIR}/wgApp4/appsync-plugin/src/ios/AppSync/C}"

if [[ ! -d "${SRC_DIR}" ]]; then
  echo "AppSync C source not found at: ${SRC_DIR}" >&2
  echo "Set APPSYNC_SRC_DIR to the directory containing jniBridge.c and retry." >&2
  exit 1
fi
JNILIBS_DIR="${MODULE_DIR}/src/main/jniLibs"

NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
LOCAL_PROPERTIES="${MODULE_DIR}/../../local.properties"
if [[ -f "${LOCAL_PROPERTIES}" ]]; then
  LOCAL_SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "${LOCAL_PROPERTIES}" | tail -n 1)"
fi
SDK_ROOT="${LOCAL_SDK_DIR:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/Library/Android/sdk}}}"
HOST_TAG="${HOST_TAG:-darwin-x86_64}"
NDK_BIN="${SDK_ROOT}/ndk/${NDK_VERSION}/toolchains/llvm/prebuilt/${HOST_TAG}/bin"

if [[ ! -x "${NDK_BIN}/aarch64-linux-android21-clang" ]]; then
  echo "NDK toolchain not found at: ${NDK_BIN}" >&2
  echo "Set ANDROID_SDK_ROOT/ANDROID_HOME, NDK_VERSION, or HOST_TAG and retry." >&2
  exit 1
fi

mkdir -p "${JNILIBS_DIR}/arm64-v8a" "${JNILIBS_DIR}/armeabi-v7a" "${JNILIBS_DIR}/x86" "${JNILIBS_DIR}/x86_64"

COMMON_SRC=(
  jniBridge.c
  detectBitString.c
  detectLocation.c
  hamming.c
  image.c
  medianFilter.c
)

COMMON_FLAGS=(
  -fPIC
  -shared
  -std=c99
  -O2
  -DANDROID
  -ffunction-sections
  -fdata-sections
)

LINK_FLAGS=(
  -Wl,--gc-sections
  -Wl,-z,relro
  -Wl,-z,now
  -Wl,-z,max-page-size=16384
  -Wl,-z,common-page-size=16384
  -llog
)

build_one() {
  local cc="$1"
  local out_file="$2"
  "${cc}" "${COMMON_FLAGS[@]}" "${COMMON_SRC[@]}" -o "${out_file}" "${LINK_FLAGS[@]}"
}

pushd "${SRC_DIR}" >/dev/null
build_one "${NDK_BIN}/aarch64-linux-android21-clang" "${JNILIBS_DIR}/arm64-v8a/libappsync.so"
build_one "${NDK_BIN}/armv7a-linux-androideabi21-clang" "${JNILIBS_DIR}/armeabi-v7a/libappsync.so"
build_one "${NDK_BIN}/i686-linux-android21-clang" "${JNILIBS_DIR}/x86/libappsync.so"
build_one "${NDK_BIN}/x86_64-linux-android21-clang" "${JNILIBS_DIR}/x86_64/libappsync.so"
popd >/dev/null

READELF="${NDK_BIN}/llvm-readelf"

echo ""
echo "Built AppSync Android libraries with 16KB page alignment:"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  echo "  ${JNILIBS_DIR}/${abi}/libappsync.so"
  if [[ -x "${READELF}" ]]; then
    "${READELF}" -l "${JNILIBS_DIR}/${abi}/libappsync.so" 2>/dev/null | grep "LOAD" || true
  fi
done
