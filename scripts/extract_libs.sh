#!/bin/bash
#
# Script for extracting LiteCore native libraries from CBL Java distribution zip files
# (macos and windows) and putting them into the canonical lite-core directory
# for inclusion in the final distribution package.
#
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
OUTPUT_DIR=$SCRIPT_DIR/../lite-core

function usage() {
    echo "usage: $0 <distribution url> <distribution name> <workspace path>"
    exit 1
}

if [ "$#" -ne 3 ]; then
    usage
fi

ZIP_URL="$1"
if [ -z "${ZIP_URL}" ]; then
    usage
fi

ZIP_FILE="$2"
if [ -z "${ZIP_FILE}" ]; then
    usage
fi

ZIP_DIR="$3"
if [ -z "${ZIP_DIR}" ]; then
    usage
fi

hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }
hash unzip 2>/dev/null || { echo >&2 "Unable to locate zip, aborting..."; exit 1; }

mkdir -p "${OUTPUT_DIR}"

rm -rf "${ZIP_DIR}"
mkdir -p "${ZIP_DIR}"
pushd "${ZIP_DIR}" > /dev/null

echo "=== Downloading: ${ZIP_URL}/${ZIP_FILE}"
curl -f -L "${ZIP_URL}/${ZIP_FILE}" -o "${ZIP_FILE}" || exit 1
unzip "${ZIP_FILE}"
rm -rf "${ZIP_FILE}"

jar -xf `find . -name 'couchbase-lite-java*.jar' -print` libs

cp -R "libs/"* "${OUTPUT_DIR}"

popd > /dev/null

rm -rf "${ZIP_DIR}"

