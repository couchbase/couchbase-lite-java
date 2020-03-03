#!/bin/bash -ex

#
# The script for generating the final distribution zip file and publising the final artifact
# to the internal maven server.
#

MAVEN_URL="http://mobile.maven.couchbase.com/maven2/internalmaven"

function usage() {
    echo "Usage: $0 <build number>"
    exit 1
}

if [ "$#" -ne 1 ]; then
    usage
fi

BUILD_NUMBER="$1"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

echo "======== Publish candidate build to internal maven"
./gradlew ciPublish -PbuildNumber=${BUILD_NUMBER} -PmavenUrl=${MAVEN_URL} || exit 1

echo "======== Create distribution zip v`cat ../version.txt`-${BUILD_NUMBER}"
./gradlew distZip -PbuildNumber="${BUILD_NUMBER}"

echo "======== Distribion Zip :"
find lib/build/distributions -name "*.zip"
