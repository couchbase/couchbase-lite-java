#!/bin/bash -e

pushd ../couchbase-lite-core/build_cmake

# Prepare linux folder:
rm -rf linux
mkdir -p linux
pushd linux

# Run cmake:
CC=clang CXX=clang++ cmake -DCMAKE_BUILD_TYPE=RelWithDebInfo -DCMAKE_EXE_LINKER_FLAGS="-fuse-ld=lld" ../..

# Run make:
core_count=`getconf _NPROCESSORS_ONLN`
make -j `expr $core_count + 1`

popd
popd
