#!/bin/bash -ex

function usage() {
    echo "usage: litecore_sha -e <VAL> [-o <VAL> -v]"
    echo "  -e|--edition <VAL>      LiteCore edition, CE or EE. The default is EE if couchbase-lite-core-EE exists, otherwise the default is CE".
    echo "  -o|--output-path <VAL>  The output path to write the result to"
    echo "  -v|--verbose            Enable verbose output"
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

if [[ $EDITION == EE ]]; then
  pushd $SCRIPT_DIR/../../couchbase-lite-core-EE > /dev/null
    first_sha=`git rev-parse HEAD`
    first_sha=${first_sha:0:40}
    if [ $VERBOSE ]; then
        echo "EE SHA is: '$first_sha'"
    fi
    popd > /dev/null
fi

pushd $SCRIPT_DIR/../../couchbase-lite-core > /dev/null
second_sha=`git rev-parse HEAD`
second_sha=${second_sha:0:40}
if [ $VERBOSE ]; then
    echo "Base SHA is: '$second_sha'"
fi
popd > /dev/null

if [[ $EDITION == EE ]]; then
    amalgamation="${second_sha}${first_sha}"
    final_sha=`echo -n $amalgamation | shasum -a 1`
    final_sha=${final_sha:0:40}
else
    final_sha="${second_sha}"
fi

echo $final_sha
if [ ! -z "$OUTPUT_PATH" ]; then
    echo $final_sha > "$OUTPUT_PATH"
fi
