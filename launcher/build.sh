#!/usr/bin/env bash
# Projector Home build script v0.2.0
# Plain SDK toolchain: aapt2 -> javac -> d8 -> zipalign -> apksigner (no Gradle).
#
#   ./build.sh                 dev build, signed with ~/.android/projectorhome-dev.keystore (auto-created)
#   RELEASE=1 KEYSTORE=/path/release.keystore KEYSTORE_PASS_FILE=/path/pass ./build.sh
#                              release build for publishing; keystore and password stay outside the repo
set -euo pipefail

VERSION="0.2.0"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
PLAT="$SDK/platforms/android-28/android.jar"
ALIAS="${KEY_ALIAS:-projectorhome}"

cd "$(dirname "$0")"

if [ "${RELEASE:-0}" = 1 ]; then
    : "${KEYSTORE:?RELEASE=1 needs KEYSTORE}"
    : "${KEYSTORE_PASS_FILE:?RELEASE=1 needs KEYSTORE_PASS_FILE}"
    KS="$KEYSTORE"; PASS_ARG="file:$KEYSTORE_PASS_FILE"
    OUT="build/ProjectorHome-$VERSION.apk"
else
    KS="$HOME/.android/projectorhome-dev.keystore"; PASS_ARG="pass:projectorhome-dev"
    OUT="build/ProjectorHome-$VERSION-dev.apk"
    if [ ! -f "$KS" ]; then
        mkdir -p "$(dirname "$KS")"
        keytool -genkeypair -keystore "$KS" -storepass projectorhome-dev -keypass projectorhome-dev \
            -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=Projector Home dev"
    fi
fi

# Intermediates live in build/tmp; finished APKs in build/ are kept across builds.
rm -rf build/tmp
mkdir -p build/tmp/classes build/tmp/dex

"$BT/aapt2" link -o build/tmp/base.apk -I "$PLAT" \
    --manifest AndroidManifest.xml --min-sdk-version 28 --target-sdk-version 28

javac --release 11 -Xlint:-options -classpath "$PLAT" -d build/tmp/classes $(find src -name '*.java')

"$BT/d8" --release --min-api 28 --lib "$PLAT" --output build/tmp/dex $(find build/tmp/classes -name '*.class')

cp build/tmp/base.apk build/tmp/unsigned.apk
(cd build/tmp/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/tmp/unsigned.apk build/tmp/aligned.apk

# Key password defaults to the keystore password (apksigner reads a pass file only once).
"$BT/apksigner" sign --ks "$KS" --ks-pass "$PASS_ARG" \
    --ks-key-alias "$ALIAS" --out "$OUT" build/tmp/aligned.apk
"$BT/apksigner" verify --print-certs "$OUT" | grep -E "certificate (DN|SHA-256)"
sha256sum "$OUT" | tee "$OUT.sha256"
echo "Built $OUT ($(stat -c %s "$OUT") bytes)"
