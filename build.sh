#!/bin/bash
# Kaizen Tool M1 build script — native Java, no frameworks
# Usage: ./build.sh  -> produces KaizenTool-m1.apk (signed)
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
SDK=~/workspace/apk-analysis/sdk
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android-34-ext12/android.jar"
JDK="$SDK/jdk-17.0.20.1+1/bin"
export PATH="$JDK:$BT:$PATH"

APK_NAME="KaizenTool-v1.1-m4.apk"
KEYSTORE="$PROJ/kaizen.keystore"
STOREPASS="kaizen123"
# M3: Apache Commons Compress + deps (7z/tar support)
# Note: io 2.14.0 / codec 1.15 are the newest versions this d8 can handle.
LIBS="$PROJ/libs/commons-compress-1.26.0.jar:$PROJ/libs/xz-1.9.jar:$PROJ/libs/commons-io-2.14.0.jar:$PROJ/libs/commons-codec-1.15.jar"

cd "$PROJ"
rm -rf build && mkdir -p build/classes build/dex build/gen

echo "==> [1/5] Packaging resources (aapt)..."
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$ANDROID_JAR" \
  -J build/gen -F build/unsigned.apk

echo "==> [2/5] Compiling Java (javac)..."
find src -name "*.java" > build/sources.txt
find build/gen -name "*.java" >> build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
  -cp "$ANDROID_JAR:$LIBS" -d build/classes @build/sources.txt

echo "==> [3/5] Dexing (d8)..."
d8 --lib "$ANDROID_JAR" --output build/dex/ \
  $(find build/classes -name "*.class") \
  "$PROJ"/libs/commons-compress-1.26.0.jar \
  "$PROJ"/libs/xz-1.9.jar \
  "$PROJ"/libs/commons-io-2.14.0.jar \
  "$PROJ"/libs/commons-codec-1.15.jar
cp build/dex/classes.dex build/
(cd build && aapt add unsigned.apk classes.dex)
# v1.1-m3: nhúng 7zz native vào lib/arm64-v8a/ để PackageManager bung ra
# nativeLibraryDir với quyền exec đúng (chạy từ files/bin bị SELinux chặn).
mkdir -p build/nativelib/lib/arm64-v8a
cp "$PROJ/lib/arm64-v8a/lib7zz.so" build/nativelib/lib/arm64-v8a/
(cd build/nativelib && aapt add ../unsigned.apk lib/arm64-v8a/lib7zz.so)

echo "==> [4/5] Aligning (zipalign)..."
zipalign -f 4 build/unsigned.apk build/aligned.apk

echo "==> [5/5] Signing (apksigner)..."
if [ ! -f "$KEYSTORE" ]; then
  echo "    Creating keystore..."
  keytool -genkey -v -keystore "$KEYSTORE" -alias kaizen \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STOREPASS" -keypass "$STOREPASS" \
    -dname "CN=KaizenTool" 2>&1 | tail -1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$STOREPASS" \
  --out "$PROJ/$APK_NAME" build/aligned.apk
apksigner verify "$PROJ/$APK_NAME"

SIZE=$(du -h "$PROJ/$APK_NAME" | cut -f1)
echo ""
echo "BUILD OK: $PROJ/$APK_NAME ($SIZE)"
