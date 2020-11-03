#!/bin/bash
#
# Test Couchbase Lite Java, Community Edition
#
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
SUPPORT_DIR="${SCRIPT_DIR}/../../lite-core/support/linux/x86_64"
STATUS=0

function usage() {
    echo "Usage: $0 <build number> <reports path>"
    exit 1
}

if [ "$#" -ne 2 ]; then
    usage
fi

BUILD_NUMBER="$1"
if [ -z "${BUILD_NUMBER}" ]; then
    usage
fi

REPORTS="$2"
if [ -z "${REPORTS}" ]; then
    usage
fi

echo "======== TEST Couchbase Lite Java, Community Edition v`cat ../version.txt`-${BUILD_NUMBER}"
export LD_LIBRARY_PATH="${SUPPORT_DIR}/libc++:${LD_LIBRARY_PATH}"
export LD_LIBRARY_PATH="${SUPPORT_DIR}/libicu:${LD_LIBRARY_PATH}"
export LD_LIBRARY_PATH="${SUPPORT_DIR}/libz:${LD_LIBRARY_PATH}"
echo $LD_LIBRARY_PATH
find "${SUPPORT_DIR}/../../.."
./gradlew ciTest --info --console=plain || STATUS=5

echo "======== Publish reports"
pushd lib/build
zip -r "${REPORTS}/test-reports-linux" reports
popd

echo "======== TEST COMPLETE: ${STATUS}"
find "${REPORTS}"
exit $STATUS

