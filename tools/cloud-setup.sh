#!/usr/bin/env bash
# Prepares a clean Linux machine (a Claude Code cloud session) to build and test H-sib Reminder. Safe to run again.
# It installs JDK 17 and the Android SDK pieces this project needs, then writes local.properties (git-ignored).
# It needs no secret: without app/google-services.json the app builds with Firebase switched off, which is what the tests expect.
# On the owner's Windows PC use tools\gw.ps1 instead; this script refuses to run there.
set -euo pipefail

if [ "$(uname -s)" != "Linux" ]; then
  echo "This script is for Linux cloud machines only. On the Windows PC use tools\\gw.ps1." >&2
  exit 1
fi

cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
JDKDIR="$HOME/jdk17"

# Use sudo only if it works without a password; otherwise download the JDK into the home folder.
can_root() { [ "$(id -u)" = 0 ] || { command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; }; }
as_root() { if [ "$(id -u)" = 0 ]; then "$@"; else sudo -n "$@"; fi; }

have_jdk17() { command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "17'; }

if ! have_jdk17; then
  if [ -x "$JDKDIR/bin/java" ]; then
    :
  elif can_root && command -v apt-get >/dev/null 2>&1; then
    as_root apt-get update -y >/dev/null 2>&1 || true
    as_root apt-get install -y openjdk-17-jdk-headless unzip curl >/dev/null || true
  fi
  if ! have_jdk17 && [ ! -x "$JDKDIR/bin/java" ]; then
    case "$(uname -m)" in aarch64|arm64) arch=aarch64 ;; *) arch=x64 ;; esac
    tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/jdk.tgz" "https://api.adoptium.net/v3/binary/latest/17/ga/linux/$arch/jdk/hotspot/normal/eclipse"
    mkdir -p "$JDKDIR"
    tar -xzf "$tmp/jdk.tgz" -C "$JDKDIR" --strip-components=1
    rm -rf "$tmp"
  fi
  if [ -x "$JDKDIR/bin/java" ]; then
    export JAVA_HOME="$JDKDIR"
    export PATH="$JAVA_HOME/bin:$PATH"
    mkdir -p "$HOME/.local/bin"
    ln -sf "$JDKDIR/bin/java" "$HOME/.local/bin/java"
  fi
fi
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  export JAVA_HOME
fi
command -v unzip >/dev/null 2>&1 || { echo "unzip is missing and could not be installed" >&2; exit 1; }

# Android command line tools and the SDK platforms and build tools the project uses
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "platforms;android-37.0" "build-tools;36.0.0" >/dev/null

echo "sdk.dir=$SDK" > local.properties
# Let ./gradlew find Java and the SDK without any exports, even when a tool refuses compound commands.
mkdir -p "$HOME/.gradle"
grep -q '^org.gradle.java.home=' "$HOME/.gradle/gradle.properties" 2>/dev/null || echo "org.gradle.java.home=$JAVA_HOME" >> "$HOME/.gradle/gradle.properties"
chmod +x gradlew
echo "Ready. Java: $JAVA_HOME   SDK: $SDK"
echo "Next, as separate plain commands:   ./gradlew --version    then    ./gradlew :core:test -q"
echo "If './gradlew' says JAVA_HOME is not set, run:  export JAVA_HOME=$JAVA_HOME  (and  export ANDROID_HOME=$SDK)"
