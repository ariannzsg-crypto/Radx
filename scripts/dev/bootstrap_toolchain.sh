#!/usr/bin/env bash
# Local-only helper (Windows): downloads a JDK 17, the Android SDK command-line
# tools and Gradle 8.9 into .tooling/ so the project can be compiled without
# Android Studio. Not part of the app; .tooling/ is git-ignored.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS="$ROOT/.tooling"
mkdir -p "$TOOLS"
cd "$TOOLS"

if [ ! -d "$TOOLS/jdk" ]; then
  echo "== downloading JDK 17 =="
  curl -fL -o jdk17.zip "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
  unzip -q jdk17.zip
  mv jdk-17* "$TOOLS/jdk"
  rm -f jdk17.zip
fi
echo "JDK: $("$TOOLS/jdk/bin/java" -version 2>&1 | head -1)"

if [ ! -d "$TOOLS/android-sdk" ]; then
  echo "== downloading Android cmdline-tools =="
  mkdir -p "$TOOLS/android-sdk/cmdline-tools"
  curl -fL -o cmdline.zip "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
  unzip -q cmdline.zip -d "$TOOLS/android-sdk/cmdline-tools"
  mv "$TOOLS/android-sdk/cmdline-tools/cmdline-tools" "$TOOLS/android-sdk/cmdline-tools/latest"
  rm -f cmdline.zip
fi

if [ ! -d "$TOOLS/gradle-8.9" ]; then
  echo "== downloading Gradle 8.9 =="
  curl -fL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  unzip -q gradle.zip
  rm -f gradle.zip
fi

echo "BOOTSTRAP_OK"
