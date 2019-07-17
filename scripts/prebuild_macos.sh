#!/bin/bash -e

pushd ../couchbase-lite-core/build_cmake
./scripts/build_macos.sh

pushd macos
make mbedcrypto
popd

popd
