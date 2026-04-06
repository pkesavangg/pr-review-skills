#!/bin/bash
set -e

cd "$(dirname "$0")"

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

echo "Building debug APK..."
./gradlew assembleDebug

APK=$(find app/build/outputs/apk/debug -name "*.apk" -type f | head -1)

if [ -z "$APK" ]; then
    echo "Build failed — APK not found."
    exit 1
fi

echo "Found APK: $APK"

DEVICES=$("$ADB" devices | grep -w "device" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo "No device connected. APK built at: $APK"
    exit 1
fi

echo "Installing on device..."
"$ADB" install -r "$APK"

echo "Launching app..."
"$ADB" shell am start -n com.dmdbrands.gurus.weight/.MainActivity

echo "Done!"
