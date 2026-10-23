#!/bin/bash
# Optional legacy Gradle bootstrap; Java must already be installed.
# Falls back to a user-local Gradle distribution when needed.
set -euo pipefail
cd "$(dirname "$0")"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

GRADLE_VERSION="${GRADLE_VERSION:-9.7.1}"
CACHE_DIR="${GOSU_GSP_CACHE:-$HOME/.cache/gosu-gsp}"

# Prefer an explicit JAVA_HOME; otherwise look for a JDK 21 in common macOS locations.
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d /opt/homebrew/opt/openjdk@21 ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  elif [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  fi
fi

if ! command -v java >/dev/null; then
  echo "No Java found. Install a JDK 21 (e.g. 'brew install openjdk@21') and re-run."
  exit 1
fi

if ! command -v gradle >/dev/null; then
  LOCAL_GRADLE="$CACHE_DIR/gradle-$GRADLE_VERSION/bin/gradle"
  if [ ! -x "$LOCAL_GRADLE" ]; then
    echo "Downloading Gradle $GRADLE_VERSION (user-local, no root needed)..."
    mkdir -p "$CACHE_DIR"
    curl -L -o "$CACHE_DIR/gradle.zip" \
      "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    rm -rf "$CACHE_DIR/gradle-$GRADLE_VERSION"
    unzip -q -o "$CACHE_DIR/gradle.zip" -d "$CACHE_DIR"
  fi
  GRADLE_BIN="$LOCAL_GRADLE"
else
  GRADLE_BIN="gradle"
fi

if [ ! -f gradlew ]; then
  "$GRADLE_BIN" wrapper --gradle-version "$GRADLE_VERSION"
fi

chmod +x gradlew setup.sh build.sh test.sh run.sh
echo "Setup done. Java: $(java -version 2>&1 | head -n 1)"
