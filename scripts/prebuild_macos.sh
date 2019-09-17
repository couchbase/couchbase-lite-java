#!/bin/bash -e

pushd ../couchbase-lite-core/build_cmake

rm -rf macos
mkdir macos

pushd macos
core_count=`getconf _NPROCESSORS_ONLN`	
cmake -DCMAKE_BUILD_TYPE=RelWithDebInfo ../..	
make -j `expr $core_count + 1`	
dsymutil libLiteCore.dylib -o libLiteCore.dylib.dSYM	
strip -x libLiteCore.dylib
popd

pushd macos
make mbedcrypto
popd

popd
