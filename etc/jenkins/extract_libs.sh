#!/bin/bash

#
# The script for extracting LiteCore native libraries from the CBL Java distribution zip file and
# put them into couchbase-lite-java/lite-core for including into the final distribution package.
#

function usage() {
    echo "Usage: $0 <couchbase-lite-java distribution zip file> <couchbase-lite-java directory>"
    exit 1
}

if [ "$#" -ne 2 ]; then
    usage
fi

rm -rf extracted
unzip $1 -d extracted

pushd extracted > /dev/null
jar -xf `find . -name 'couchbase-lite-java*.jar' -print` libs
popd > /dev/null

mkdir -p $2/lite-core
cp -R extracted/libs/* $2/lite-core

rm -rf extracted
