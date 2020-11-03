#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
BUILD_DIR="${SCRIPT_DIR}/../lite-core"

function usage() {
    echo "usage: $0 [-p darwin|centos6|centos7|linux]"
    echo "  -p|--platform  Core platform: darwin, centos6, centos7 or linux. Default inferred from current OS" 
    echo
}

shopt -s nocasematch
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in 
      -p|--platform)
         PLATFORM="$2"
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

if [ -z "${PLATFORM}" ]; then
   PLATFORM="${OSTYPE}"
fi

echo "=== Cleaning: ${BUILD_DIR}"
echo "  for: ${PLATFORM}"

mkdir -p "${BUILD_DIR}"
pushd "${BUILD_DIR}" > /dev/null
case "${PLATFORM}" in
   darwin*)
      rm -rf  macos
      ;;
   centos6)
      rm -rf support linux
      ;;
   centos7|linux*)
      rm -rf linux
      ;;
   *)
      echo "Unsupported platform: ${PLATFORM}. Aborting..."
      exit 1
esac

echo "=== Clean complete"
find .
popd > /dev/null

