pushd ../couchbase-lite-core/build_cmake
mkdir x64
pushd x64
"C:\Program Files\CMake\bin\cmake.exe" -G "Visual Studio 14 2015 Win64" ..\..
"C:\Program Files\CMake\bin\cmake.exe" --build . --target LiteCore --config RelWithDebInfo
popd
popd
