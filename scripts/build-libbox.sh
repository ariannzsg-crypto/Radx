#!/usr/bin/env bash
# Builds sing-box's libbox.aar (the proxy core) and drops it into app/libs/.
#
# Requirements:
#   - Go 1.21+          (https://go.dev/dl)
#   - JDK 17            (gomobile requires it; "openjdk 17" must be the active java)
#   - Android SDK + NDK (ANDROID_HOME set, NDK installed via sdkmanager)
#
# Usage: ./scripts/build-libbox.sh [sing-box-tag]
set -euo pipefail

SING_BOX_VERSION="${1:-v1.11.15}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.libbox-build"

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "ANDROID_HOME (or ANDROID_SDK_ROOT) must point to an Android SDK with an NDK." >&2
  exit 1
fi

mkdir -p "$ROOT/app/libs"

if [ ! -d "$WORK/sing-box/.git" ]; then
  rm -rf "$WORK"
  mkdir -p "$WORK"
  git clone --depth 1 --branch "$SING_BOX_VERSION" https://github.com/SagerNet/sing-box.git "$WORK/sing-box"
fi

cd "$WORK/sing-box"

# gomobile is vendored by the sing-box project and pinned in its Makefile.
go install -v github.com/sagernet/gomobile/cmd/gomobile@v0.1.4
go install -v github.com/sagernet/gomobile/cmd/gobind@v0.1.4
export PATH="$PATH:$(go env GOPATH)/bin"

gomobile init
go run ./cmd/internal/build_libbox -target android

cp libbox.aar "$ROOT/app/libs/libbox.aar"
echo "libbox.aar ($SING_BOX_VERSION) -> app/libs/libbox.aar"
