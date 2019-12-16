#!/bin/bash

function usage() {
    echo "usage: build_litecore -e <VAL> [-l <VAL>] [-d]"
    echo "  -e|--edition CE|EE   LiteCore edition: CE or EE."
    echo "  -l|--lib <VAL>       The library to build:  LiteCore (LiteCore + mbedcrypto) or mbedcrypto (mbedcrypto only). The default is LiteCore."
    echo "  -d|--debug           Use build type 'Debug' instead of 'RelWithDebInfo'"
    echo
}

shopt -s nocasematch

MBEDTLS_DIR=vendor/mbedtls
MBEDTLS_LIB=crypto/library/libmbedcrypto.a

CORE_COUNT=`getconf _NPROCESSORS_ONLN`

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

LIB="LiteCore"

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in 
        -e|--edition)
        EDITION="$2"
        shift
        shift
        ;;
        -l|--lib)
        LIB="$2"
        shift
        shift
        ;;
        -d|--debug)
        DEBUG=1
        shift
        ;;
        *)
        echo >&2 "Unrecognized option $key, aborting..."
        usage
        exit 1
        ;;
    esac
done

if [ -z "$EDITION" ]; then
  echo >&2 "Missing --edition option, aborting..."
  usage
  exit 1
fi

if [ -z "$DEBUG" ]; then
  BUILD_TYPE="RelWithDebInfo"
else
  BUILD_TYPE="Debug"
fi

echo "LiteCore Edition: $EDITION"
echo "Library: $LIB"

ENT="OFF"
if [[ $EDITION == EE ]]; then
  ENT="ON"
fi

if [[ $OSTYPE == linux* ]]; then
  OS=linux
elif [[ $OSTYPE == darwin* ]]; then
  OS=macos
else
  echo "Unsupported OS ($OSTYPE), aborting..."
  exit 1
fi

set -o xtrace

pushd $SCRIPT_DIR/../../couchbase-lite-core/build_cmake > /dev/null

rm -rf $OS && mkdir -p $OS
pushd $OS > /dev/null

OUTPUT_DIR=$SCRIPT_DIR/../lite-core/$OS/x86_64
mkdir -p $OUTPUT_DIR

if [[ $OS == linux ]]; then
  if [[ $LIB == LiteCore ]]; then
    CC=clang CXX=clang++ cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=$BUILD_TYPE -DCMAKE_C_COMPILER_WORKS=1 -DCMAKE_CXX_COMPILER_WORKS=1 ../..

    make -j `expr $CORE_COUNT + 1` LiteCore
    cp -f libLiteCore.so $OUTPUT_DIR

    make -j `expr $CORE_COUNT + 1` mbedcrypto
    cp -f $MBEDTLS_DIR/$MBEDTLS_LIB $OUTPUT_DIR
  fi

  if [[ $LIB == mbedcrypto ]]; then
    CC=clang CXX=clang++ cmake -DCMAKE_BUILD_TYPE=$BUILD_TYPE -DCMAKE_POSITION_INDEPENDENT_CODE=1 ../../$MBEDTLS_DIR
    make -j `expr $CORE_COUNT + 1`
    cp -f $MBEDTLS_LIB $OUTPUT_DIR
  fi
fi

if [[ $OS == macos ]]; then
  if [[ $LIB == LiteCore ]]; then
    cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=$BUILD_TYPE ../..

    make -j `expr $CORE_COUNT + 1` LiteCore
    strip -x libLiteCore.dylib
    cp -f libLiteCore.dylib $OUTPUT_DIR

    make -j `expr $CORE_COUNT + 1` mbedcrypto
    cp -f $MBEDTLS_DIR/$MBEDTLS_LIB $OUTPUT_DIR
  fi

  if [[ $LIB == mbedcrypto ]]; then
    cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=$BUILD_TYPE -DCMAKE_POSITION_INDEPENDENT_CODE=1 ../../$MBEDTLS_DIR
    make -j `expr $CORE_COUNT + 1`
    cp -f $MBEDTLS_LIB $OUTPUT_DIR
  fi
fi

popd > /dev/null
popd > /dev/null

echo "Build $LIB Complete"
