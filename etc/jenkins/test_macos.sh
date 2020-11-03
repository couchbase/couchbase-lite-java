#!/bin/bash
#
# Test Couchbase Lite Java, Community Edition for MacOS
#
function usage() {
    echo "Usage: $0 <build number> <reports path>"
    exit 1
}

if [ "$#" -ne 2 ]; then
    usage
fi

BUILD_NUMBER="$1"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

REPORTS="$2"
if [ -z "REPORTS" ]; then
    usage
fi

STATUS=0

echo "======== TEST Couchbase Lite Java, Community Edition v`cat ../version.txt`-${BUILD_NUMBER}"
./gradlew ciTest --info --console=plain || STATUS=5

echo "======== Publish reports"
pushd lib/build > /dev/null
zip -r "${REPORTS}/test-reports-macos" reports
popd > /dev/null

echo "======== TEST COMPLETE ${STATUS}"
find "${REPORTS}"
exit $STATUS

