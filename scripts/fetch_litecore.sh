#!/bin/bash

function usage() {
  echo "usage: fetch_litecore.sh -n <VAL> -v <VAL> -e <VAL> [-d]"
  echo "  -n|--nexus-repo <VAL>   The URL of the nexus repo containing LiteCore"
  echo "  -v|--variants <VAL>     The comma separated list of platform IDs (macos and linux) to download"
  echo "  -e|--edition <VAL>      LiteCore edition, CE or EE."
  echo "  -d|--debug              If set, download the debug versions of the binaries"
  echo
}

function choose_extension() {
  if [ "$1" = "linux" ]; then
    echo "tar.gz"
  else
    echo "zip"
  fi
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
  PLATFORM=$VARIANT
  if [ $PLATFORM == "macos" ]; then
    PLATFORM="macosx"
  fi

  echo "Fetching $PLATFORM..."
  EXTENSION=`choose_extension $PLATFORM`

  echo $NEXUS_REPO/couchbase-litecore-$PLATFORM/$SHA/couchbase-litecore-$PLATFORM-$SHA$SUFFIX.$EXTENSION
  curl -Lf $NEXUS_REPO/couchbase-litecore-$PLATFORM/$SHA/couchbase-litecore-$PLATFORM-$SHA$SUFFIX.$EXTENSION -o litecore-$PLATFORM$SUFFIX.$EXTENSION || exit 1
done

if [ -f "litecore-macosx$SUFFIX.zip" ]; then
  mkdir -p macos/x86_64
  unzip litecore-macosx$SUFFIX.zip
  mv -f lib/libLiteCore.dylib macos/x86_64
  rm -rf lib
  rm -f litecore-macosx$SUFFIX.zip
fi

if [ -f "litecore-linux$SUFFIX.tar.gz" ]; then
  mkdir -p linux/x86_64
  tar xf litecore-linux$SUFFIX.tar.gz
  mv -f lib/libLiteCore.so linux/x86_64

  # mv -f lib/libc++.so.1.0 libc++.so.1
  # mv -f lib/libc++abi.so.1.0 libc++abi.so.1
  # mv -f lib/libicudata.so.54.1 libicudata.so.54
  # mv -f lib/libicui18n.so.54.1 libicui18n.so.54
  # mv -f lib/libicuuc.so.54.1 libicuuc.so.54
  rm -rf lib
  rm -f litecore-linux$SUFFIX.tar.gz
fi

popd > /dev/null
