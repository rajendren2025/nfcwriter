#!/usr/bin/env bash
set -e
export ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

echo "Accepting licenses and installing SDK packages..."
yes | sdkmanager --licenses || true
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
echo "Done."
