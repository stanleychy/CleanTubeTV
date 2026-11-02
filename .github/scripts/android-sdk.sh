#!/usr/bin/env bash
# Installs the exact Android SDK packages that the build uses (README: Requirements).
set -euo pipefail
sdkmanager="${ANDROID_HOME:?ANDROID_HOME is not set}/cmdline-tools/latest/bin/sdkmanager"
yes | "$sdkmanager" --licenses > /dev/null || true
"$sdkmanager" --install "platforms;android-36" "build-tools;36.0.0" "platform-tools" > /dev/null
echo "Android SDK packages: OK"
