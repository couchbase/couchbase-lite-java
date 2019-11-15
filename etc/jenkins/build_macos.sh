#!/bin/bash -ex

#
# CI build script for building couchbase-lite-java{-ee} on macos platforms.
#

LITE_CORE_REPO_URL="http://nexus.build.couchbase.com:8081/nexus/content/repositories/releases/com/couchbase/litecore"

function usage() {
    echo "Usage: $0 <build number> <edition, CE or EE>"
    exit 1
}

if [ "$#" -ne 2 ]; then
    usage
fi

BUILD_NUMBER="$1"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

EDITION="$2"
if [ -z "$EDITION" ]; then
    usage
fi

touch local.properties

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
CBL_JAVA_DIR=$SCRIPT_DIR/../..

echo "======== Download Lite Core ..."
$CBL_JAVA_DIR/scripts/fetch_litecore.sh -n $LITE_CORE_REPO_URL -v macos -e $EDITION

echo "======== Building mbedcrypto ..."
$CBL_JAVA_DIR/scripts/build_litecore.sh -e $EDITION -l mbedcrypto

echo "======== Build Couchbase Lite Java ..."
./gradlew ciCheck -PbuildNumber="${BUILD_NUMBER}" || exit 1

echo "======== Create distribution zip for Couchbase Lite Java, $EDITION Edition v`cat ../version.txt`-${BUILD_NUMBER}"
./gradlew distZip -PbuildNumber="${BUILD_NUMBER}" || exit 1

echo "======== Distribion Zip :"
find lib/build/distributions -name "*.zip"
