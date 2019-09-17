@echo off

if "%1%" == "" (
    echo Usage: prebuild_windows.bat ^<VS Generator: 2015,2017,2019^>
    exit /B 1
)

if "%1%" == "2015" SET VS_GEN="Visual Studio 14 2015 Win64"
if "%1%" == "2017" SET VS_GEN="Visual Studio 15 2017 Win64"
if "%1%" == "2019" SET VS_GEN="Visual Studio 16 2019"

if %VS_GEN% == "" (
    echo Using an invalid VS Generator
    exit /B 1
)

pushd ..\couchbase-lite-core\build_cmake
rmdir /Q /S x64
mkdir x64
pushd x64

echo Build using %VS_GEN% ...
"C:\Program Files\CMake\bin\cmake.exe" -G %VS_GEN% ..\..
"C:\Program Files\CMake\bin\cmake.exe" --build . --target LiteCore --config RelWithDebInfo
popd
popd
