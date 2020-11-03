echo ON

rem Builds LiteCore (LiteCore + mbedcrypto) or mbedcrypto (mbedcrypto only). The default is LiteCore."

if "%2%" == "" (
    echo Usage: build_litecore.bat ^<VS Generator: 2015,2017,2019^> ^<Build type: CE or EE^> ^[mbedcrypto, LiteCore ^(default^)]
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

SET lib=LiteCore
IF /I "%3%" == "mbedcrypto"   SET lib=mbedcrypto

pushd %~dp0
set scriptDir=%CD%
popd

echo Building %lib%
echo Enterprise Edition : %entBuild%
echo Using %VS_GEN% ...

SET liteCoreBuildDir=%scriptDir%\..\..\couchbase-lite-core\build_cmake

pushd %liteCoreBuildDir%
rmdir /Q /S windows
mkdir windows
pushd windows

SET outputDir=%scriptDir%\..\lite-core\windows\x86_64
if not exist "%outputDir%" (
    mkdir %outputDir%
)

rem Untested
if "%lib%" == "LiteCore" (
    "C:\Program Files\CMake\bin\cmake.exe" -G %VS_GEN% -DBUILD_ENTERPRISE=%entBuild% -DCMAKE_BUILD_TYPE=RelWithDebInfo ..\.. || goto :error

    "C:\Program Files\CMake\bin\cmake.exe" --build . --config RelWithDebInfo --target LiteCore || goto :error
	   copy /y %liteCoreBuildDir%\windows\RelWithDebInfo\LiteCore.dll %outputDir%
	   copy /y %liteCoreBuildDir%\windows\RelWithDebInfo\LiteCore.lib %outputDir%
 
    "C:\Program Files\CMake\bin\cmake.exe" --build . --config RelWithDebInfo --target mbedcrypto || goto :error
	   copy /y %liteCoreBuildDir%\windows\library\RelWithDebInfo\mbedcrypto.lib %outputDir%
)

rem Works
if "%lib%" == "mbedcrypto" (
    "C:\Program Files\CMake\bin\cmake.exe" -G %VS_GEN% -DCMAKE_BUILD_TYPE=RelWithDebInfo ..\..\vendor\mbedtls || goto :error
 
    "C:\Program Files\CMake\bin\cmake.exe" --build . --config RelWithDebInfo --target mbedcrypto || goto :error
	   copy /y %liteCoreBuildDir%\windows\library\RelWithDebInfo\mbedcrypto.lib %outputDir%
)

popd
popd

echo Build LiteCore Complete
goto :eof

:error
echo Failed with error %ERRORLEVEL%.
exit /b %ERRORLEVEL%
