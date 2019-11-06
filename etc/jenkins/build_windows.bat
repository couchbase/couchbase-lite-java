@echo OFF

:: CI build script for building couchbase-lite-java{-ee} on Windows platforms.

SET liteCoreRepoUrl="http://nexus.build.couchbase.com:8081/nexus/content/repositories/releases/com/couchbase/litecore"

if "%3%" == "" (
    echo Usage: build_windows.bat ^<VS Generator: 2015,2017,2019^> ^<BUILD_NUMBER^> ^<CE or EE^>
    exit /B 1
)

set vsGen=%1%
set buildNumber=%2%
set edition=%3%

echo "" > local.properties

pushd %~dp0
set scriptDir=%CD%
popd
set cblJavaDir=%scriptDir%\..\..

echo ======== Download Lite Core ...
powershell.exe -ExecutionPolicy Bypass -Command "%cblJavaDir%\scripts\fetch_litecore.ps1" %liteCoreRepoUrl% %edition%

echo ======== Building mbedcrypto ...
call %cblJavaDir%\scripts\build_litecore.bat %vsGen% %edition% mbedcrypto

echo ======== Build Couchbase Lite Java ...
call gradlew.bat ciCheckWindows -PbuildNumber=%buildNumber%

echo ======== Create distribution zip for Couchbase Lite Java, %edition% Edition, Build %buildNumber%
call gradlew.bat distZip -PbuildNumber=%buildNumber%

echo ======== Complete
