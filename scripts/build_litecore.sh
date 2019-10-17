#!/bin/bash

function usage() {
    echo "usage: build_litecore -e <VAL> [-l <VAL>]"
    echo "  -e|--edition <VAL>   LiteCore edition, CE or EE. The default is EE if couchbase-lite-core-EE exists, otherwise the default is CE".
    echo "  -l|--libs <VAL>      The comma separated list of libraries to build. The libraries are LiteCore and mbedcrypto. The default is both."
    echo
}

shopt -s nocasematch

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

LIBS=(LiteCore mbedcrypto)

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in 
        -e|--edition)
        EDITION="$2"
        shift
        shift
        ;;
        -l|--libs)
        IFS=',' read -ra LIBS <<< "$2"
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
echo "Libraries: ${LIBS[*]}"

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
  CC=clang CXX=clang++ cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=RelWithDebInfo -DCMAKE_C_COMPILER_WORKS=1 -DCMAKE_CXX_COMPILER_WORKS=1 ../..
  for LIB in "${LIBS[@]}"; do
    make -j `expr $CORE_COUNT + 1` $LIB
  done
  
  for LIB in "${LIBS[@]}"; do
    if [[ $LIB == LiteCore ]]; then
      cp -f libLiteCore.so $OUTPUT_DIR
    fi
    if [[ $LIB == mbedcrypto ]]; then
      cp -f vendor/mbedtls/library/libmbedcrypto.a $OUTPUT_DIR
    fi
  done
fi

if [[ $OS == macos ]]; then
  cmake -DBUILD_ENTERPRISE=$ENT -DCMAKE_BUILD_TYPE=RelWithDebInfo ../..
  for LIB in "${LIBS[@]}"; do
    make -j `expr $CORE_COUNT + 1` $LIB
  done

  if [ -f "libLiteCore.dylib" ]; then
    strip -x libLiteCore.dylib
  fi

  echo "Copy libraries to $OUTPUT_DIR ..."
  for LIB in "${LIBS[@]}"; do
    if [[ $LIB == LiteCore ]]; then
      cp -f libLiteCore.dylib $OUTPUT_DIR
    fi
    if [[ $LIB == mbedcrypto ]]; then
      cp -f vendor/mbedtls/library/libmbedcrypto.a $OUTPUT_DIR
    fi
  done
fi

popd > /dev/null

popd > /dev/null

echo "Build LiteCore Complete"