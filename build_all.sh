#!/bin/bash
set -e
set -o pipefail

handle_error() {
  echo "❌ Error occurred at line $1"
  exit 1
}

trap 'handle_error $LINENO' ERR

echo "🧹 Building monkey.jar..."
./gradlew clean makeJar > /dev/null 2>&1

echo "🛠 Building native components..."
sh ./build_native.sh > /dev/null 2>&1

echo "📦 Converting to DEX format..."
$ANDROID_HOME/build-tools/28.0.3/dx --dex --min-sdk-version=26 --output=monkeyq.jar monkey/build/libs/monkey.jar > /dev/null 2>&1

echo "✅ Done!"
