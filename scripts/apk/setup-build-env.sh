#!/bin/sh
# Best-effort local Android build environment for this repo.
# The server ships JDK 8 only and an SDK with no platforms/, so parity work had
# no way to compile or run the JVM unit tests. This installs JDK 17 (already
# present via apt), Gradle 8.11.1 and the Android SDK bits AGP 8.9.2 needs,
# entirely inside /opt/android-buildenv so nothing outside this directory or the
# repo is touched.
set -eu

ROOT=/opt/android-buildenv
SDK="$ROOT/sdk"
GRADLE_VER=8.11.1
CMDLINE_VER=13114758
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export JAVA_HOME

mkdir -p "$ROOT" "$SDK"

echo "== gradle $GRADLE_VER =="
if [ ! -x "$ROOT/gradle-$GRADLE_VER/bin/gradle" ]; then
  curl -fsSL -o "$ROOT/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
  unzip -q -o "$ROOT/gradle.zip" -d "$ROOT"
fi
"$ROOT/gradle-$GRADLE_VER/bin/gradle" --version | head -6

echo "== android cmdline-tools =="
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -fsSL -o "$ROOT/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VER}_latest.zip"
  unzip -q -o "$ROOT/cmdline-tools.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo "== done =="
ls "$SDK/platforms" "$SDK/build-tools"
