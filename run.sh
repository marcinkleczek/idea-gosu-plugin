#!/bin/bash
# Launch a sandbox IDEA with the plugin installed (first run downloads the
# target IDE, ~1 GB, into ~/.gradle caches; your real IDE is untouched).
# Open samples/hello.gsp in the sandbox for a visual check.
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

./gradlew runIde
