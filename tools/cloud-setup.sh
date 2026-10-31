#!/usr/bin/env bash
# Prepares a clean Linux machine (a Claude Code cloud session) to build and test H-sib Reminder. Safe to run again.
# It installs JDK 17 and the Android SDK pieces this project needs, then writes local.properties (git-ignored).
# It never needs a secret: without app/google-services.json the app builds with Firebase switched off, which is what the tests expect.
set -euo pipefail

cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/android-sdk}"

# JDK 17
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q 'version "17'; then
  if command -v sudo >/dev/null 2>&1; then SUDO=sudo; else SUDO=; fi
  $SUDO apt-get update -y >/dev/null 2>&1 || true
  $SUDO apt-get install -y openjdk-17-jdk-headless unzip curl >/dev/null
fi
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export JAVA_HOME

# Android command line tools and the SDK platforms and build tools the project uses
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  curl -sSL -o "$tmp/tools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "platforms;android-37.0" "build-tools;36.0.0" >/dev/null

echo "sdk.dir=$SDK" > local.properties
chmod +x gradlew
echo "Ready. Use:  export ANDROID_HOME=$SDK JAVA_HOME=$JAVA_HOME  then  ./gradlew :core:test  or  ./gradlew :app:testDebugUnitTest --tests \"<class>\" -q"
