#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Builds PJSIP/PJSUA2 for Android (arm64-v8a, armeabi-v7a, x86_64) with:
#   - OpenSSL 3.x  → SIP over TLS, SRTP (SDES) crypto
#   - Opus         → wideband codec
#   - 16 KB page-size compatible ELF alignment (Google Play requirement)
#   - NO video, NO G.729 (licensing), NO bcg729
# Output is copied into android/telephony/sip-engine/pjsua2/{java,jniLibs}.
#
# Requirements: Linux or WSL2, bash, make, cmake, perl, swig ≥ 4, JDK 17, Android NDK r27+.
#   export ANDROID_NDK_ROOT=/path/to/android-ndk-r27d
#   scripts/pjsip/build-pjsip-android.sh
#
# LICENSING: PJSIP is GPLv2 (or commercial from Teluu). Distributing a closed-source app
# linked with PJSIP requires a commercial licence – see docs/LICENSING.md before release.
# -----------------------------------------------------------------------------
set -euo pipefail

PJSIP_VERSION="${PJSIP_VERSION:-2.16}"
OPENSSL_VERSION="${OPENSSL_VERSION:-3.0.15}"
OPUS_VERSION="${OPUS_VERSION:-1.5.2}"
API_LEVEL="${API_LEVEL:-29}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64}"
# Parallel jobs: low by default so the computer stays usable (JOBS=8 for a fast build).
JOBS="${JOBS:-2}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# Build in the Linux filesystem (building on /mnt/c under WSL is very slow).
WORK="${WORK:-$HOME/.cache/ivrtop-pjsip}"
OUT="$ROOT/android/telephony/sip-engine/pjsua2"
TOOLS="${TOOLS:-$HOME/.local/ivrtop-tools}"

# Auto-detect a Linux NDK r27+ when ANDROID_NDK_ROOT is not set.
if [ -z "${ANDROID_NDK_ROOT:-}" ]; then
  for d in "$HOME"/android-ndk-r2[7-9]* "$HOME"/Android/Sdk/ndk/2[7-9].* /opt/android-ndk-r2[7-9]*; do
    [ -d "$d/toolchains/llvm/prebuilt/linux-x86_64" ] && ANDROID_NDK_ROOT="$d"
  done
fi
: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT to a Linux NDK r27+ installation}"
export ANDROID_NDK_ROOT
echo "NDK: $ANDROID_NDK_ROOT"

# Portable cmake / JDK (no sudo needed) when they are not installed.
mkdir -p "$TOOLS"
if ! command -v cmake >/dev/null 2>&1; then
  CMAKE_VERSION="${CMAKE_VERSION:-3.31.6}"
  if [ ! -x "$TOOLS/cmake/bin/cmake" ]; then
    echo "Downloading portable cmake $CMAKE_VERSION…"
    curl -fsSL "https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-linux-x86_64.tar.gz" -o "$TOOLS/cmake.tgz"
    rm -rf "$TOOLS/cmake" && mkdir -p "$TOOLS/cmake" && tar xzf "$TOOLS/cmake.tgz" -C "$TOOLS/cmake" --strip-components=1 && rm "$TOOLS/cmake.tgz"
  fi
  export PATH="$TOOLS/cmake/bin:$PATH"
fi
if ! command -v javac >/dev/null 2>&1; then
  if [ ! -x "$TOOLS/jdk/bin/javac" ]; then
    echo "Downloading portable JDK 17 (Eclipse Temurin)…"
    curl -fsSL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -o "$TOOLS/jdk.tgz"
    rm -rf "$TOOLS/jdk" && mkdir -p "$TOOLS/jdk" && tar xzf "$TOOLS/jdk.tgz" -C "$TOOLS/jdk" --strip-components=1 && rm "$TOOLS/jdk.tgz"
  fi
  export JAVA_HOME="$TOOLS/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

HOST_TAG="linux-x86_64"
TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
PAGE_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

mkdir -p "$WORK" "$OUT/java" "$OUT/jniLibs"
cd "$WORK"

fetch() { # url file
  [ -f "$2" ] || curl -fsSL "$1" -o "$2"
}

fetch "https://github.com/pjsip/pjproject/archive/refs/tags/${PJSIP_VERSION}.tar.gz" "pjproject-${PJSIP_VERSION}.tar.gz"
fetch "https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz" "openssl-${OPENSSL_VERSION}.tar.gz"
fetch "https://downloads.xiph.org/releases/opus/opus-${OPUS_VERSION}.tar.gz" "opus-${OPUS_VERSION}.tar.gz"

# Verify archives against pinned checksums when provided (recommended for CI):
#   export PJSIP_SHA256=... OPENSSL_SHA256=... OPUS_SHA256=...
verify() { [ -z "${2:-}" ] || echo "$2  $1" | sha256sum -c -; }
verify "pjproject-${PJSIP_VERSION}.tar.gz" "${PJSIP_SHA256:-}"
verify "openssl-${OPENSSL_VERSION}.tar.gz" "${OPENSSL_SHA256:-}"
verify "opus-${OPUS_VERSION}.tar.gz" "${OPUS_SHA256:-}"

openssl_target() {
  case "$1" in
    arm64-v8a) echo android-arm64 ;;
    armeabi-v7a) echo android-arm ;;
    x86_64) echo android-x86_64 ;;
  esac
}

for ABI in $ABIS; do
  echo "================ $ABI ================"
  PREFIX="$WORK/prefix/$ABI"
  mkdir -p "$PREFIX"

  # ---------- OpenSSL (static) ----------
  if [ ! -f "$PREFIX/lib/libssl.a" ]; then
    rm -rf "openssl-${OPENSSL_VERSION}" && tar xzf "openssl-${OPENSSL_VERSION}.tar.gz"
    (
      cd "openssl-${OPENSSL_VERSION}"
      export PATH="$TOOLCHAIN/bin:$PATH"
      # OpenSSL 3 refuses make variables (LDFLAGS=…) mixed with -D flags on the command line → use env.
      # -D__ANDROID_API__ pins the API level (otherwise Configure picks the NDK's highest one,
      # breaking minSdk 29); -Wno-macro-redefined silences the per-file redefinition warning.
      LDFLAGS="$PAGE_LDFLAGS" ./Configure "$(openssl_target "$ABI")" -D__ANDROID_API__="$API_LEVEL" -Wno-macro-redefined \
        no-shared no-tests no-ui-console --prefix="$PREFIX" --openssldir="$PREFIX/ssl"
      make -j"$JOBS" build_libs
      make install_dev
    )
  fi

  # ---------- Opus (static) ----------
  if [ ! -f "$PREFIX/lib/libopus.a" ]; then
    rm -rf "opus-${OPUS_VERSION}" && tar xzf "opus-${OPUS_VERSION}.tar.gz"
    cmake -S "opus-${OPUS_VERSION}" -B "opus-build-$ABI" \
      -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API_LEVEL" \
      -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DOPUS_BUILD_TESTING=OFF -DOPUS_BUILD_PROGRAMS=OFF \
      -DCMAKE_INSTALL_PREFIX="$PREFIX"
    cmake --build "opus-build-$ABI" -j"$JOBS"
    cmake --install "opus-build-$ABI"
  fi

  # ---------- PJSIP ----------
  rm -rf "pjproject-${PJSIP_VERSION}" && tar xzf "pjproject-${PJSIP_VERSION}.tar.gz"
  cd "pjproject-${PJSIP_VERSION}"
  cat > pjlib/include/pj/config_site.h <<'EOF'
/* IVRTOP Dialer – PJSIP Android configuration */
#define PJ_CONFIG_ANDROID 1
#include <pj/config_site_sample.h>

#undef  PJMEDIA_HAS_VIDEO
#define PJMEDIA_HAS_VIDEO               0
#define PJMEDIA_HAS_SRTP                1
#define PJMEDIA_HAS_G729_CODEC          0
#define PJMEDIA_HAS_BCG729              0
#define PJMEDIA_HAS_L16_CODEC           0
#define PJMEDIA_HAS_ILBC_CODEC          0
#define PJMEDIA_HAS_GSM_CODEC           0
#define PJMEDIA_HAS_SPEEX_CODEC         0
#define PJMEDIA_HAS_WEBRTC_AEC          1

/* Keep-alives for connection-oriented transports (NAT bindings, battery-friendly). */
#define PJSIP_TCP_KEEP_ALIVE_INTERVAL   60
#define PJSIP_TLS_KEEP_ALIVE_INTERVAL   60

/* Calls / accounts */
#undef  PJSUA_MAX_CALLS
#define PJSUA_MAX_CALLS                 8
#undef  PJSUA_MAX_ACC
#define PJSUA_MAX_ACC                   12
#undef  PJSUA_MAX_BUDDIES
#define PJSUA_MAX_BUDDIES               64

/* Required for 16 KB page size devices with older NDK headers */
#define __BIONIC_NO_PAGE_SIZE_MACRO     1
EOF

  export TARGET_ABI="$ABI"
  export APP_PLATFORM="android-$API_LEVEL"
  export CFLAGS="-O2 -fPIC -D__BIONIC_NO_PAGE_SIZE_MACRO"
  export LDFLAGS="$PAGE_LDFLAGS"
  ./configure-android --use-ndk-cflags \
    --with-ssl="$PREFIX" \
    --with-opus="$PREFIX" \
    --disable-video --disable-libyuv --disable-openh264 --disable-ffmpeg --disable-v4l2 \
    --disable-bcg729 --disable-g7221-codec --disable-speex-codec --disable-speex-aec --disable-ilbc-codec --disable-gsm-codec
  make dep
  # PJSIP 2.16 does not create the srtp "pjlib" object dir (srtp_err.o) → create it up front.
  TN="$(sed -n 's/^export TARGET_NAME *:= *//p' build.mak)"
  mkdir -p "third_party/build/srtp/output/libsrtp-$TN/pjlib"
  # PJSIP third_party output dirs race under -j → retry serially if the parallel build fails.
  make -j"$JOBS" || make

  # WebRTC AEC (bundled third_party/webrtc) stays enabled: PjsipEngine uses it as the
  # software fallback when the device has no platform AcousticEchoCanceler.
  # SWIG Java bindings + libpjsua2.so
  ( cd pjsip-apps/src/swig/java && make )
  SWIG_OUT="pjsip-apps/src/swig/java/android/pjsua2/src/main"
  mkdir -p "$OUT/jniLibs/$ABI"
  cp "$SWIG_OUT/jniLibs/$ABI/libpjsua2.so" "$OUT/jniLibs/$ABI/"
  # C++ runtime shipped alongside (loaded first by PjsipEngine.start()).
  case "$ABI" in
    arm64-v8a) TRIPLE=aarch64-linux-android ;;
    armeabi-v7a) TRIPLE=arm-linux-androideabi ;;
    x86_64) TRIPLE=x86_64-linux-android ;;
  esac
  cp "$TOOLCHAIN/sysroot/usr/lib/$TRIPLE/libc++_shared.so" "$OUT/jniLibs/$ABI/"
  rm -rf "$OUT/java/org" && cp -r "$SWIG_OUT/java/org" "$OUT/java/"
  cd "$WORK"
done

# ---------- 16 KB alignment check ----------
# Google Play requires 16 KB alignment for 64-bit ABIs only (armeabi-v7a is exempt; the NDK's
# 32-bit libc++_shared.so is 4 KB aligned).
echo "Checking ELF LOAD segment alignment of 64-bit libraries (must be 2**14 or higher)…"
for so in "$OUT"/jniLibs/arm64-v8a/*.so "$OUT"/jniLibs/x86_64/*.so; do
  [ -f "$so" ] || continue
  hdr="$("$TOOLCHAIN/bin/llvm-readelf" -lW "$so")"
  align="$(printf '%s\n' "$hdr" | awk '/LOAD/ {print $NF; exit}')"
  echo "  $so → $align"
  case "$align" in 0x4000|0x10000) ;; *) echo "ERROR: $so is not 16 KB aligned" >&2; exit 1 ;; esac
done

echo "PJSUA2 ready in $OUT"
