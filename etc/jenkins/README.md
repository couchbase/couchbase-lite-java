## Overview

CBL-Java supports three platforms including linux, windows, and macos (only 64 bits arch supported). CBL-Java contains native shared library for its JNI that needs to be built separately on each platform and merge them together into one single jar file along with the java classes. Moreover the distribution package for the linux platform will include the dependency libraries required by CBL-Java's JNI including libc++, libicu, and libz.

The sections below will outline the steps for Jenkins server to build CBL-Java on each platform and merge them together. The linux platform will need to be the main one as it also needs to package the additional dependecies.

Note that the environment variables referenced in this document are based on the CBL-Andriod's jenkins task (http://mobile.jenkins.couchbase.com/job/couchbase-lite-android-edition-build-post-25/configure).

## MacOS

### Preparation
1. Download and extract the tar ball into ${WORKSPACE}
2. echo "${RELEASE}" > "${WORKSPACE}/version.txt"

### Build Steps for EE
1. cd ${WORKSPACE}/couchbase-lite-java-ee"
2. ../couchbase-lite-java/etc/jenkins/build_macos.sh ${BLD_NUM} EE
3. The distribution zip file will be at build/distribution/couchbase-lite-java-ee-${RELEASE}-{BLD_NUM}.zip
4. Upload the zip file to a location that will be downloaded and used later.

### Build Steps for CE
1. cd ${WORKSPACE}/couchbase-lite-java"
2. ./etc/jenkins/build_macos.sh ${BLD_NUM} CE
3. The distribution zip file will be at build/distribution/couchbase-lite-java-${RELEASE}-{BLD_NUM}.zip
4. Upload the zip file to a location that will be downloaded and used later.

## Windows

### Preparation
1. Download and extract the tar ball into %WORKSPACE%
2. echo %RELEASE% > %WORKSPACE%\version.txt

### Build Steps for EE
1. cd %WORKSPACE%\couchbase-lite-java-ee"
2. ..\couchbase-lite-java\etc\jenkins\build_windows.bat 2017 %BLD_NUM% EE (Note: 2017 indicates the Visual Studio 2017 version, the value could be 2015, 2017 or 2019)
3. The distribution zip file will be at build\distribution\couchbase-lite-java-ee-%RELEASE%-%BLD_NUM%.zip
4. Upload the zip file to a location that will be downloaded and used later.

### Build Steps for CE
1. cd %WORKSPACE%\couchbase-lite-java"
2. .\etc\jenkins\build_windows.bat 2017 %BLD_NUM% CE (Note: 2017 indicates the Visual Studio 2017 is used, the version could be 2015, 2017 or 2019)
3. The distribution zip file will be at build\distribution\couchbase-lite-java-%RELEASE%-%BLD_NUM%.zip
4. Upload the zip file to a location that will be downloaded and used later.

## Linux (Main)

### Preparation
1. Download and extract the tar ball into ${WORKSPACE}
2. echo "${RELEASE}" > "${WORKSPACE}/version.txt"
3. Download the CBL-JAVA macos zip file and use couchbase-lite-java/etc/jenkins/extract_libs.sh <zip file> <path-to-couchbase-lite-java> to extract the file.
4. Download the CBL-JAVA windows zip file and use couchbase-lite-java/etc/jenkins/extract_libs.sh <zip file> <path-to-couchbase-lite-java> to extract the file.

The Step 3 and 4 will extract Lite-Core and JNI native libraries of macos and windows to couchbase-lite-java/lite-core directory.

### Build Steps for EE
1. cd ${WORKSPACE}/couchbase-lite-java-ee"
2. ../couchbase-lite-java/etc/jenkins/build_linux.sh ${BLD_NUM} EE
3. The build_linux.sh script will publish the build artifact to ci maven specified in the script file.
4. Start couchbase-lite-java-unit-tests jenkins task in order to test the built artifact. The couchbase-lite-java-unit-tests will need to be created and the script to run the test has not been developed yet.
5. If step 4 is successful, ../couchbase-lite-java/etc/jenkins/publish.sh ${BLD_NUM}. This will publish the artifact to the internal maven that can be consumed by QE.
6. The step 5 also generated a distribution zip file at build\distribution\couchbase-lite-java-ee-%RELEASE%-%BLD_NUM%.zip. Upload the zip file to the latestbuilds.service.couchbase.com server.

### Build Steps for CE
1. cd ${WORKSPACE}/couchbase-lite-java"
2. ./etc/jenkins/build_linux.sh ${BLD_NUM} CE
3. The build_linux.sh script will publish the build artifact to ci-maven specified in the script file.
4. Start couchbase-lite-java-unit-tests jenkins task in order to test the built artifact. The couchbase-lite-java-unit-tests will need to be created and the script to run the test has not been developed yet.
5. If step 4 is successful, ./etc/jenkins/publish.sh ${BLD_NUM}. This will publish the artifact to the internal maven that can be consumed by QE.
6. The step 5 also generated a distribution zip file at build\distribution\couchbase-lite-java-%RELEASE%-%BLD_NUM%.zip. Upload the zip file to the latestbuilds.service.couchbase.com server.