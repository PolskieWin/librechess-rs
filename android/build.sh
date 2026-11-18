#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_JAR="${HOME}/.android-sdk/android.jar"

if [ ! -f "${ANDROID_JAR}" ]; then
    echo "Error: android.jar not found at ${ANDROID_JAR}"
    exit 1
fi

cd "${ROOT_DIR}"

echo "1. Generating R.java with aapt..."
aapt package -m -J android/src -M android/AndroidManifest.xml -S android/res -A android/assets -I "${ANDROID_JAR}"

echo "2. Compiling Java sources with javac..."
mkdir -p android/bin/classes android/bin/dex
rm -rf android/bin/classes/* android/bin/dex/* android/bin/*.apk
javac -cp "${ANDROID_JAR}" -d android/bin/classes android/src/com/polskie/librechess/*.java

echo "3. Converting bytecode to DEX with d8..."
d8 --output android/bin/dex android/bin/classes/com/polskie/librechess/*.class

echo "4. Packaging APK resources with aapt..."
aapt package -f -M android/AndroidManifest.xml -S android/res -A android/assets -0 wav -I "${ANDROID_JAR}" -F android/bin/unaligned.apk

echo "5. Adding classes.dex to APK..."
(cd android/bin/dex && aapt add ../unaligned.apk classes.dex)

echo "6. Aligning APK with zipalign..."
zipalign -f 4 android/bin/unaligned.apk android/bin/aligned.apk

echo "7. Signing APK with apksigner..."
apksigner sign --ks android/bin/debug.keystore --ks-pass pass:android --ks-key-alias debug --out android/bin/librechess-rs.apk android/bin/aligned.apk

echo "8. Verifying APK signature..."
apksigner verify android/bin/librechess-rs.apk

echo "Build successful: android/bin/librechess-rs.apk"
ls -lh android/bin/librechess-rs.apk
