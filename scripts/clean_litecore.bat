echo ON
  
rem Clean the lite-core directory

pushd %~dp0
set scriptDir=%CD%
popd

SET buildDir=%scriptDir%\..\lite-core
if not exist "%buildDir%" (
    mkdir %buildDir%
)
pushd %buildDir%

rmdir /Q /S windows

popd

