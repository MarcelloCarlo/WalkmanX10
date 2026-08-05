#!/bin/bash
# Builds libwolfssljni.so for armeabi (ARMv5/ARMv6) using Docker + NDK r16b.
# Run from the project root: ./build-wolfssl.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JNI_DIR="$SCRIPT_DIR/app/src/main/jni"
OUTPUT_DIR="$JNI_DIR/libs/armeabi"

docker run --rm --platform linux/amd64 \
  -v "$JNI_DIR":/jni \
  -w /jni \
  ubuntu:20.04 bash -c '
    set -e
    apt-get update -qq && apt-get install -y -qq curl unzip git make file libncurses5 > /dev/null 2>&1

    echo ">>> Downloading NDK r16b..."
    curl -sL -o /tmp/ndk.zip \
      https://dl.google.com/android/repository/android-ndk-r16b-linux-x86_64.zip
    unzip -q /tmp/ndk.zip -d /tmp/
    rm /tmp/ndk.zip

    echo ">>> Cloning wolfSSL v5.7.6-stable..."
    rm -rf /jni/wolfssl
    git clone --depth 1 --branch v5.7.6-stable \
      https://github.com/wolfSSL/wolfssl.git /jni/wolfssl 2>/dev/null

    echo ">>> Building with ndk-build (armeabi)..."
    /tmp/android-ndk-r16b/ndk-build \
      NDK_PROJECT_PATH=/jni/.. \
      NDK_APPLICATION_MK=/jni/Application.mk \
      APP_BUILD_SCRIPT=/jni/Android.mk \
      NDK_LIBS_OUT=/jni/libs \
      NDK_OUT=/jni/obj \
      -j$(nproc)

    echo ">>> Build complete:"
    find /jni/libs -name "*.so" -exec ls -lh {} \;
    file /jni/libs/armeabi/libwolfssljni.so

    echo ">>> Cleaning up..."
    rm -rf /jni/wolfssl /jni/obj
  '

echo ""
echo "Output: $OUTPUT_DIR/libwolfssljni.so"
ls -lh "$OUTPUT_DIR/libwolfssljni.so"
