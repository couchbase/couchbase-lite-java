#!/bin/bash -ex

function usage() {
    echo "usage: build_litecore -e <VAL> [-l <VAL>]"
    echo "  -e|--edition <VAL>   LiteCore edition: CE or EE. The default is EE if couchbase-lite-core-EE exists, otherwise the default is CE".
    echo "  -l|--lib <VAL>       The library to build:  LiteCore (LiteCore + mbedcrypto) or mbedcrypto (mbedcrypto only). The default is LiteCore."
    echo
}

shopt -s nocasematch

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

pushd $SCRIPT_DIR/../../couchbase-lite-core/build_cmake > /dev/null

rm -rf $OS && mkdir -p $OS
pushd $OS > /dev/null

CORE_COUNT=`getconf _NPROCESSORS_ONLN`

OUTPUT_DIR=$SCRIPT_DIR/../lite-core/$OS/x86_64
mkdir -p $OUTPUT_DIR

if [[ $OS == linux ]]; then
  if [[ $LIB == LiteCore ]]; then
    CC=clang CXX=clang++ cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=RelWithDebInfo -DCMAKE_C_COMPILER_WORKS=1 -DCMAKE_CXX_COMPILER_WORKS=1 ../..

    make -j `expr $CORE_COUNT + 1` LiteCore
    cp -f libLiteCore.so $OUTPUT_DIR

    make -j `expr $CORE_COUNT + 1` mbedcrypto
    cp -f vendor/mbedtls/library/libmbedcrypto.a $OUTPUT_DIR
  fi

  if [[ $LIB == mbedcrypto ]]; then
    CC=clang CXX=clang++ cmake -DCMAKE_BUILD_TYPE=RelWithDebInfo -DCMAKE_C_COMPILER_WORKS=1 -DCMAKE_CXX_COMPILER_WORKS=1 ../../vendor/mbedtls
    make -j `expr $CORE_COUNT + 1`
    cp -f library/libmbedcrypto.a $OUTPUT_DIR
  fi
fi

if [[ $OS == macos ]]; then
  if [[ $LIB == LiteCore ]]; then
    cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=RelWithDebInfo ../..

    make -j `expr $CORE_COUNT + 1` LiteCore
    strip -x libLiteCore.dylib
    cp -f libLiteCore.dylib $OUTPUT_DIR

    make -j `expr $CORE_COUNT + 1` mbedcrypto
    cp -f vendor/mbedtls/library/libmbedcrypto.a $OUTPUT_DIR
  fi

  if [[ $LIB == mbedcrypto ]]; then
    cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=RelWithDebInfo ../../vendor/mbedtls
    make -j `expr $CORE_COUNT + 1`
    cp -f library/libmbedcrypto.a $OUTPUT_DIR
  fi
fi

popd > /dev/null

popd > /dev/null

echo "Build $LIB Complete"
