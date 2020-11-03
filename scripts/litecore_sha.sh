#!/bin/bash

function usage() {
    echo "usage: -e CE|EE [-o <path>] [-v]"
    echo "  -e|--edition      LiteCore edition, CE or EE."
    echo "  -o|--output-path  The output path to write the result to"
    echo "  -v|--verbose      Enable verbose output"
    echo
}

shopt -s nocasematch

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
hash git 2>/dev/null || { echo >&2 "Unable to locate git, aborting..."; exit 1; }
hash shasum 2>/dev/null || { echo >&2 "Unable to locate shasum, aborting..."; exit 1; }

while [[ $# -gt 0 ]]; do
    key="$1"

    case $key in
        -e|--edition)
        EDITION="$2"
        shift
        shift
        ;;
        -o|--out-path)
        OUTPUT_PATH="$2"
        shift
        shift
        ;;
        -v|--verbose)
        VERBOSE=true
        shift
        ;;
        *)
        echo >&2 "Unrecognized option $key, aborting..."
        usage
        exit 1
        ;;
    esac
done

if [ -z "$EDITION" ]; then
  echo >&2 "Missing --edition option, aborting..."
  usage
  exit 1
fi

pushd $SCRIPT_DIR/../../couchbase-lite-core > /dev/null
sha=`git rev-parse HEAD`
sha=${sha:0:40}
popd > /dev/null
if [ $VERBOSE ]; then echo "CE SHA: '$sha'"; fi

if [[ $EDITION == EE ]]; then
  pushd $SCRIPT_DIR/../../couchbase-lite-core-EE > /dev/null
    ee_sha=`git rev-parse HEAD`
    ee_sha=${ee_sha:0:40}
    if [ $VERBOSE ]; then echo "EE SHA: '$ee_sha'"; fi
    amalgamation="${sha}${ee_sha}"
    amalgamation=`echo -n $amalgamation | shasum -a 1`
    sha=${amalgamation:0:40}
    popd > /dev/null
fi

echo $sha
if [ ! -z "$OUTPUT_PATH" ]; then echo $sha > "$OUTPUT_PATH"; fi
