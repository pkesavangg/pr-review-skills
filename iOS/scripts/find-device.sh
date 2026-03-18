#!/usr/bin/env bash
# find-device.sh — Print the first connected physical iOS device ID.
#
# Usage: ./scripts/find-device.sh [SCHEME]
#   SCHEME defaults to meAppTests if not provided.
#
# On success: prints the device ID to stdout and exits 0.
# On failure: prints an error message to stderr and exits 1.

set -euo pipefail

SCHEME="${1:-meAppTests}"

DEVICE_ID=$(xcodebuild \
  -project meApp.xcodeproj \
  -scheme "$SCHEME" \
  -showdestinations 2>&1 \
  | grep "platform:iOS," \
  | grep -v Simulator \
  | grep -v "error:" \
  | head -1 \
  | sed 's/.*id:\([^,]*\).*/\1/' \
  | xargs)

if [[ -z "$DEVICE_ID" ]]; then
  echo "No physical device detected. Please connect and unlock your iPhone, then try again." >&2
  exit 1
fi

echo "$DEVICE_ID"
