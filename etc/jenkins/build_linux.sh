#!/bin/bash
#
# Build Couchbase Lite Java, Community Edition for MacOS, Windows, Linux
# This script assumes the the OSX and Windows builds are available on latestbuilds
#
PRODUCT="couchbase-lite-java"
LATESTBUILDS_URL="http://latestbuilds.service.couchbase.com/builds/latestbuilds"
NEXUS_URL="http://nexus.build.couchbase.com:8081/nexus/content/repositories/releases/com/couchbase/litecore"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
TOOLS_DIR="${SCRIPT_DIR}/../../../couchbase-lite-java/scripts"

function usage() {
   echo "Usage: $0 <release version> <build number> <workspace path> <distro>"
   exit 1
}

if [ "$#" -ne 4 ]; then
   usage
fi

VERSION="$1"
if [ -z "${VERSION}" ]; then
   usage
fi

BUILD_NUMBER="$2"
if [ -z "${BUILD_NUMBER}" ]; then
   usage
fi

WORKSPACE="$3"
if [ -z "${WORKSPACE}" ]; then
   usage
fi

DISTRO="$4"
if [ -z "${DISTRO}" ]; then
   usage
fi

echo "======== BUILD Couchbase Lite Java, Community Edition v`cat ../version.txt`-${BUILD_NUMBER} (${DISTRO})"

echo "======== Static analysis ..."
touch local.properties
./gradlew ciCheck -PbuildNumber="${BUILD_NUMBER}" || exit 1

echo "======== Clean up ..." 
"${TOOLS_DIR}/clean_litecore.sh" -p "${DISTRO}"

echo "======== Download platform artifiacts ..."
for PLATFORM in macos windows; do
   ARTIFACT="${PRODUCT}-${VERSION}-${BUILD_NUMBER}-${PLATFORM}.zip"
   ARTIFACT_URL="${LATESTBUILDS_URL}/couchbase-lite-java/${VERSION}/${BUILD_NUMBER}"
   "${TOOLS_DIR}/extract_libs.sh" "${ARTIFACT_URL}" "${ARTIFACT}" "${WORKSPACE}/zip-tmp" || exit 1
done

echo "======== Download Lite Core ..."
"${TOOLS_DIR}/fetch_litecore.sh" -p "${DISTRO}" -e CE -n "${NEXUS_URL}"

echo "======== Build mbedcrypto ..."
"${TOOLS_DIR}/build_litecore.sh" -l mbedcrypto -e CE

echo "======== Build Java"
./gradlew ciBuild -PbuildNumber="${BUILD_NUMBER}" || exit 1

echo "======== BUILD COMPLETE"
find lib/build/distributions
