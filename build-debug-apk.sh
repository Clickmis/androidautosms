#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed. Open this project in Android Studio or install Gradle 8.11.1+."
  exit 1
fi
gradle assembleDebug
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
