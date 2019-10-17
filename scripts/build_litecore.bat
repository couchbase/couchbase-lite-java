 @echo OFF

if "%2%" == "" (
    echo Usage: build_litecore.bat ^<VS Generator: 2015,2017,2019^> ^<CE or EE^> ^[LiteCore, mbedcrypto, all ^(default^)^]
    exit /B 1
)

if "%1%" == "2015" SET VS_GEN="Visual Studio 14 2015 Win64"
if "%1%" == "2017" SET VS_GEN="Visual Studio 15 2017 Win64"
if "%1%" == "2019" SET VS_GEN="Visual Studio 16 2019"
if %VS_GEN% == "" (
    echo Using an invalid VS Generator
    exit /B 1
)

SET entBuild=OFF
IF /I "%2%" == "ee" SET entBuild=ON

SET libs=LiteCore mbedcrypto
if "%3%" == "LiteCore"   SET libs=LiteCore
if "%3%" == "mbedcrypto" SET libs=mbedcrypto

pushd %~dp0
set scriptDir=%CD%
popd

SET liteCoreBuildDir=%scriptDir%\..\..\couchbase-lite-core\build_cmake

pushd %liteCoreBuildDir%
rmdir /Q /S x64
mkdir x64
pushd x64

echo Enterprise Edition : %entBuild% 
echo Build using %VS_GEN% ...
"C:\Program Files\CMake\bin\cmake.exe" -G %VS_GEN% -DBUILD_ENTERPRISE=%entBuild% ..\..

SET outputDir=%scriptDir%\..\lite-core\windows\x86_64
if not exist "%outputDir%" (
    mkdir %outputDir%
)

for %%l in (%libs%) do (
  echo Building %%l ...

  "C:\Program Files\CMake\bin\cmake.exe" --build . --target %lib% --config RelWithDebInfo
  
  if "%%l" == "LiteCore" (
    copy /y %liteCoreBuildDir%\x64\RelWithDebInfo\LiteCore.dll %outputDir%
    copy /y %liteCoreBuildDir%\x64\RelWithDebInfo\LiteCore.lib %outputDir%
  )

  if "%%l" == "mbedcrypto" (
    copy /y %liteCoreBuildDir%\x64\vendor\mbedtls\library\RelWithDebInfo\mbedcrypto.lib %outputDir%
  )
)

popd
popd

echo Build LiteCore Complete