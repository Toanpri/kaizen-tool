#!/bin/bash
# Build 7zz native cho Android arm64-v8a (dùng cho Kaizen Tool v1.1-m3+).
# Binary output: CPP/7zip/Bundles/Alone2/_o/7zz
# Copy vào: ../../assets/native7z/arm64-v8a/7zz
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NDK="$SCRIPT_DIR/android-ndk-r27d"
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

if [ ! -d "$NDK" ]; then
  echo "Tải NDK r27d về và giải nén vào $NDK trước:"
  echo "  https://dl.google.com/android/repository/android-ndk-r27d-linux.zip"
  exit 1
fi
if [ ! -d "$SCRIPT_DIR/CPP/7zip/Bundles/Alone2" ]; then
  echo "Tải 7-Zip source về và giải nén vào $SCRIPT_DIR trước:"
  echo "  https://www.7-zip.org/a/7z2604-src.tar.xz"
  exit 1
fi

cd "$SCRIPT_DIR/CPP/7zip/Bundles/Alone2"
# LIB2=-ldl: Android không có libpthread riêng (nằm trong libc).
# LDFLAGS=-static-libstdc++: không phụ thuộc libc++_shared.so trên máy user.
make -f makefile.gcc -j$(nproc) \
  CC=$TC/aarch64-linux-android21-clang \
  CXX=$TC/aarch64-linux-android21-clang++ \
  'LIB2=-ldl' \
  'LDFLAGS=-DNDEBUG -static-libstdc++'

echo ""
echo "OK: _o/7zz"
file _o/7zz
readelf -d _o/7zz | grep NEEDED
echo ""
echo "Copy vào assets:"
echo "  cp _o/7zz ../../assets/native7z/arm64-v8a/7zz"
