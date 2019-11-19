#!/bin/bash -ex

function usage() {
  echo "usage: fetch_litecore.sh -n <VAL> -v <VAL> -e <VAL> [-d]"
  echo "  -n|--nexus-repo <VAL>   The URL of the nexus repo containing LiteCore"
  echo "  -v|--variants <VAL>     The comma separated list of platform IDs (macos and linux) to download"
  echo "  -e|--edition <VAL>      LiteCore edition, CE or EE."
  echo "  -d|--debug              If set, download the debug versions of the binaries"
  echo
}

DEBUG_LIB=false
shopt -s nocasematch
while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in 
    -n|--nexus-repo)
      NEXUS_REPO="$2"
      shift
      shift
      ;;
    -v|--variants)
      IFS=',' read -ra VARIANTS <<< "$2"
      shift
      shift
      ;;
    -e|--edition)
        EDITION="$2"
        shift
        shift
        ;;
    -d|--debug-lib)
      DEBUG_LIB=true
      shift
      ;;
    *)
    echo >&2 "Unrecognized option $key, aborting..."
      usage
      exit 1
      ;;
  esac
done

if [ -z "$NEXUS_REPO" ]; then
  echo >&2 "Missing --nexus-repo option, aborting..."
  usage
  exit 1
fi

if [ -z "$VARIANTS" ]; then
  echo >&2 "Missing --variants option, aborting..."
  usage
  exit 1
fi

if [ -z "$EDITION" ]; then
  echo >&2 "Missing --edition option, aborting..."
  usage
  exit 1
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }
hash unzip 2>/dev/null || { echo >&2 "Unable to locate unzip, aborting..."; exit 1; }
hash tar 2>/dev/null || { echo >&2 "Unable to locate tar, aborting..."; exit 1; }

OUTPUT_DIR=$SCRIPT_DIR/../lite-core
mkdir -p $OUTPUT_DIR
pushd $OUTPUT_DIR > /dev/null

SHA=`$SCRIPT_DIR/litecore_sha.sh -e $EDITION`

SUFFIX=""
if [ $DEBUG ]; then
  SUFFIX="-debug"
fi

echo "Fetching LiteCore $EDITION for $SHA..."
if [ ${VARIANTS[0]} = "all" ]; then
  VARIANTS=("macos" "linux")
fi

for VARIANT in "${VARIANTS[@]}"; do
  if [ $VARIANT == "macos" ]; then
    PLATFORM="macosx"
    EXTENSION="zip"
  elif [ $VARIANT == "linux" ]; then
    PLATFORM="centos6"
    EXTENSION="tar.gz"
  fi

  echo "Fetching $PLATFORM..."
  PREFIX="couchbase-litecore"
  echo $NEXUS_REPO/couchbase-litecore-$PLATFORM/$SHA/$PREFIX-$PLATFORM-$SHA$SUFFIX.$EXTENSION
  curl -Lf $NEXUS_REPO/couchbase-litecore-$PLATFORM/$SHA/$PREFIX-$PLATFORM-$SHA$SUFFIX.$EXTENSION -o litecore-$PLATFORM$SUFFIX.$EXTENSION || exit 1
done

if [ -f "litecore-macosx$SUFFIX.zip" ]; then
  unzip litecore-macosx$SUFFIX.zip

  LIBLITECORE_DIR=macos/x86_64
  mkdir -p $LIBLITECORE_DIR && rm -rf $LIBLITECORE_DIR/*
  mv -f lib/libLiteCore.dylib $LIBLITECORE_DIR

  rm -rf lib
  rm -f litecore-macosx$SUFFIX.zip
fi

if [ -f "litecore-centos6$SUFFIX.tar.gz" ]; then
  tar xf litecore-centos6$SUFFIX.tar.gz

  LIBLITECORE_DIR=linux/x86_64
  mkdir -p $LIBLITECORE_DIR && rm -rf $LIBLITECORE_DIR/*
  mv -f lib/libLiteCore.so $LIBLITECORE_DIR

  LIBCXX_DIR=support/linux/x86_64/libc++
  mkdir -p $LIBCXX_DIR && rm -rf $LIBCXX_DIR/*
  mv -f lib/libc++*.* $LIBCXX_DIR

  LIBICU_DIR=support/linux/x86_64/libicu
  mkdir -p $LIBICU_DIR && rm -rf $LIBICU_DIR/*
  mv -f lib/libicu*.* $LIBICU_DIR
  rm -f $LIBICU_DIR/libicutest*.*

  LIBZ_DIR=support/linux/x86_64/libz
  mkdir -p $LIBZ_DIR && rm -rf $LIBZ_DIR/*
  mv -f lib/libz.so* $LIBZ_DIR

  rm -rf lib
  rm -f litecore-centos6$SUFFIX.tar.gz
fi

popd > /dev/null
