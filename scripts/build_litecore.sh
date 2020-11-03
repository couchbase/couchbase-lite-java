#!/bin/bash

MBEDTLS_DIR=vendor/mbedtls
MBEDTLS_LIB=library/libmbedcrypto.a

BUILD_TYPE="RelWithDebInfo"

cores=`getconf _NPROCESSORS_ONLN`
JOBS=`expr $cores + 1`

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

function usage() {
    echo "usage: $0 -e EE|CE [-d] [-l LiteCore|mbedcrypto]"
    echo "  -e|--edition    LiteCore edition: CE or EE."
    echo "  -d|--debug      Use build type 'Debug' instead of 'RelWithDebInfo'"
    echo "  -l|--lib        The library to build:  LiteCore (LiteCore + mbedcrypto) or mbedcrypto (mbedcrypto only). The default is LiteCore."
    echo
}

shopt -s nocasematch
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in 
      -e|--edition)
         EDITION="$2"
         shift
         shift
         ;;
      -d|--debug)
         BUILD_TYPE="Debug"
         shift
         ;;
      -l|--lib)
         TARGET="$2"
         shift
         shift
         ;;
      *)
         echo >&2 "Unrecognized option $key, aborting..."
         usage
         exit 1
         ;;
    esac
done

case "$EDITION" in
   CE)
      ENT="OFF"
      ;;
   EE)
      ENT="ON"
      ;;
   *)
      echo >&2 "Unrecognized or missing --edition option ($EDITION): aborting..."
      usage
      exit 1
      ;;
esac

case "$TARGET" in
   "" | LiteCore)
      LIB="LiteCore"
      ;;
   mbedcrypto)
      LIB="mbedcrypto"
      ;;
   *)
      echo >&2 "Unrecognized --lib option ($TARGET): aborting..."
      usage
      exit 1
      ;;
esac

case "$OSTYPE" in
   linux*)
      OS="linux"
      hash tar 2>/dev/null || { echo >&2 "Unable to locate tar, aborting..."; exit 1; }
      ;;
   darwin*)
      OS=macos
      hash unzip 2>/dev/null || { echo >&2 "Unable to locate unzip, aborting..."; exit 1; }
      ;;
   *)
      echo "Unsupported OS ($OSTYPE), aborting..."
      exit 1
esac

echo "=== Building: $LIB"
echo "  edition: $EDITION"
echo "  for: $OS"

OUTPUT_DIR="$SCRIPT_DIR/../lite-core/$OS/x86_64"
mkdir -p "$OUTPUT_DIR"

pushd "$SCRIPT_DIR/../../couchbase-lite-core/build_cmake" > /dev/null

rm -rf $OS
mkdir -p $OS
pushd $OS > /dev/null


case $OS in
   linux)
      # untested
      if [[ $LIB == LiteCore ]]; then
         cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=$BUILD_TYPE -DCMAKE_C_COMPILER_WORKS=1 -DCMAKE_CXX_COMPILER_WORKS=1 ../..

         make -j $JOBS LiteCore
         cp -f libLiteCore.so $OUTPUT_DIR

         make -j $JOBS mbedcrypto
         cp -f $MBEDTLS_DIR/$MBEDTLS_LIB $OUTPUT_DIR
      fi

      # works on centos6
      if [[ $LIB == mbedcrypto ]]; then
         cmake -DCMAKE_BUILD_TYPE=$BUILD_TYPE -DCMAKE_POSITION_INDEPENDENT_CODE=1 ../../$MBEDTLS_DIR
         make -j $JOBS mbedx509 mbedcrypto mbedtls
         cp -f $MBEDTLS_LIB $OUTPUT_DIR
      fi
      ;;

   # works on several OSX versions
   macos)
      if [[ $LIB == LiteCore ]]; then
         cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=$BUILD_TYPE ../..

         make -j $JOBS LiteCore
         strip -x libLiteCore.dylib
         cp -f libLiteCore.dylib $OUTPUT_DIR

         make -j $JOBS mbedcrypto
         cp -f $MBEDTLS_DIR/$MBEDTLS_LIB $OUTPUT_DIR
      fi

      if [[ $LIB == mbedcrypto ]]; then
         cmake -DCMAKE_BUILD_TYPE=$BUILD_TYPE -DCMAKE_POSITION_INDEPENDENT_CODE=1 ../../$MBEDTLS_DIR
         make -j $JOBS mbedx509 mbedcrypto mbedtls
         cp -f $MBEDTLS_LIB $OUTPUT_DIR
      fi
      ;;
esac

echo "=== Build complete"
find "$OUTPUT_DIR"
popd > /dev/null
popd > /dev/null

