#!/bin/bash -ex

#
# CI build script for building couchbase-lite-java{-ee} on linux platforms.
#

LITE_CORE_REPO_URL="http://nexus.build.couchbase.com:8081/nexus/content/repositories/releases/com/couchbase/litecore"
MAVEN_URL="http://172.23.121.218/maven2/cimaven"

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
$CBL_JAVA_DIR/scripts/fetch_litecore.sh -n $LITE_CORE_REPO_URL -v linux -e $EDITION

echo "======== Building mbedcrypto ..."
$CBL_JAVA_DIR/scripts/build_litecore.sh -e $EDITION -l mbedcrypto

# Set load library path for building and testing:
SUPPORT_DIR="${CBL_JAVA_DIR}/lite-core/support/linux/x86_64"
export LD_LIBRARY_PATH="${SUPPORT_DIR}/libicu:${SUPPORT_DIR}/libz:${SUPPORT_DIR}/libc++"

# Set libc++ include and lib directory from cbdeps:
echo "LINUX_LIBCXX_INCDIR=${LIBCXX_INCDIR}/c++/v1" >> local.properties
echo "LINUX_LIBCXX_LIBDIR=${LIBCXX_LIBDIR}" >> local.properties

echo "======== Build Couchbase Lite Java, $EDITION v`cat ../version.txt`-${BUILD_NUMBER}"
./gradlew ciCheck -PbuildNumber="${BUILD_NUMBER}" --info || exit 1

echo "======== Publish build candidates to CI maven"
./gradlew ciPublish -PbuildNumber="${BUILD_NUMBER}" -PmavenUrl="${MAVEN_URL}" --info || exit 1

echo "======== BUILD COMPLETE"
