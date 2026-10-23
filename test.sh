#!/bin/bash
# Run unit and IntelliJ editor integration tests.
set -euo pipefail
cd "$(dirname "$0")"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

# Prefer an explicit JAVA_HOME; otherwise look for a JDK 21 in common macOS locations.
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d /opt/homebrew/opt/openjdk@21 ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  elif [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  fi
fi

./gradlew test
