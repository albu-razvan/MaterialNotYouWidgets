#!/bin/bash

set -euo pipefail

APP_NAME="MaterialNotYouWidgets"
SHOULD_SIGN=false
GRADLE_SIGNING_PROPS=()

# Signed build
KEYSTORE_PATH=""
KEYSTORE_PASSWORD=""
KEY_ALIAS=""
KEY_PASSWORD=""

usage() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -K, --keystore-path      Path to your keystore (.jks) file."
    echo "  -P, --keystore-password  Password for the keystore."
    echo "  -a, --key-alias          Alias for the key."
    echo "  -p, --key-password       Password for the key alias. If same as keystore password, you must still provide it."
    echo "  -h, --help               Display this help message and exit."
    exit 1
}

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -K|--keystore-path)
            KEYSTORE_PATH="$2"
            shift
            ;;
        -P|--keystore-password)
            KEYSTORE_PASSWORD="$2"
            shift
            ;;
        -a|--key-alias)
            KEY_ALIAS="$2"
            shift
            ;;
        -p|--key-password)
            KEY_PASSWORD="$2"
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            usage
            ;;
    esac
    shift
done

export SOURCE_DATE_EPOCH=$(git log -1 --format=%at)

echo ""
echo "Building with SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"
echo ""

if [ -n "${KEYSTORE_PATH:-}" ] && \
   [ -n "${KEYSTORE_PASSWORD:-}" ] && \
   [ -n "${KEY_ALIAS:-}" ] && \
   [ -n "${KEY_PASSWORD:-}" ]; then

    if [ ! -f "$KEYSTORE_PATH" ]; then
        echo "Error: Keystore file not found at: $KEYSTORE_PATH"
        exit 1
    fi

    echo "All signing credentials provided. Proceeding with a signed build."

    GRADLE_SIGNING_PROPS+=("-PkeystorePath=$(realpath "$KEYSTORE_PATH")")
    GRADLE_SIGNING_PROPS+=("-PkeystorePassword=$KEYSTORE_PASSWORD")
    GRADLE_SIGNING_PROPS+=("-PkeyAlias=$KEY_ALIAS")
    GRADLE_SIGNING_PROPS+=("-PkeyPassword=$KEY_PASSWORD")
    SHOULD_SIGN=true
else
    echo "One or more signing credentials were not provided. Skipping signed release builds."
    echo "To create a signed build, provide all four options: --keystore-path, --keystore-password, --key-alias, and --key-password."
fi

APP_VERSION_NAME=$(./gradlew -q :app:getVersionName)

if [ -z "$APP_VERSION_NAME" ]; then
    echo "WARNING: Could not determine app version name from Gradle. Using 'undefined'."
    APP_VERSION_NAME="undefined"
fi

FINAL_BUILD_DIR="build/v${APP_VERSION_NAME}"

echo ""
echo "Starting Gradle Builds..."
echo ""

./gradlew clean

# Debug APK
echo ""
echo "Building Debug APK..."
echo ""
./gradlew assembleDebug

mkdir -p "$FINAL_BUILD_DIR"
cp "app/build/outputs/apk/debug/app-debug.apk" \
   "${FINAL_BUILD_DIR}/${APP_NAME}-v${APP_VERSION_NAME}-debug.apk"

# Unsigned Release APK
echo ""
echo "Building Unsigned Release APK..."
echo ""
./gradlew assembleRelease

cp "app/build/outputs/apk/release/app-release-unsigned.apk" \
   "${FINAL_BUILD_DIR}/${APP_NAME}-v${APP_VERSION_NAME}-unsigned.apk"

# Signed Release APK (only if signing credentials provided)
if [ "${SHOULD_SIGN}" = true ]; then
  echo ""
  echo "Building Signed Release APK..."
  echo ""
  ./gradlew assembleRelease "${GRADLE_SIGNING_PROPS[@]}"

  cp "app/build/outputs/apk/release/app-release.apk" \
     "${FINAL_BUILD_DIR}/${APP_NAME}-v${APP_VERSION_NAME}.apk"
fi

echo ""
echo "Build process finished."
echo "All artifacts in: $FINAL_BUILD_DIR"
ls -lh "$FINAL_BUILD_DIR"
