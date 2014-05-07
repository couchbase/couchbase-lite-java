This is a "portable java" version of the Couchbase Lite.  

To see how this fits into the overall architecture, see the [Couchbase Lite Project Structure](https://github.com/couchbase/couchbase-lite-android/wiki/Project-structure).


# Get the code
```
$ git clone <this repo>
$ git submodule init && git submodule update
```

# Build and test

Currently we are sharing the test suits with [Couchbase-lite-android](https://github.com/couchbase/couchbase-lite-android.git) project. We are working on fixing some issues that will allow both CBLite Java and Android to run the test suits from a common place. Meanwhile, we need to copy over test suits manually from CBLite Android project and run the test as followings.

1. Clone Couchbase-lite-android project<br>`$ clone https://github.com/couchbase/couchbase-lite-android.git`
2. Copy the test suits from Couchbase-lite-android to Couchbase-lite-java<br>`$ cp -r [Couchbase-lite-android DIR]/src/androidTest/java [Couchbase-lite-java DIR]/src/test`<br>`$ cp -r [Couchbase-lite-android DIR]/src/androidTest/assets [Couchbase-lite-java DIR]/src/test/resources/assets`
3. Delete or comment out two test cases that require Android specific libraries (We are working on porting these two test cases): com.couchbase.lite.CollationTest and Base64Test.
4. Open build.gradle and decomment the following dependencies:<br>`testCompile group: 'commons-io', name: 'commons-io', version: '2.0.1'`<br>`compile group: 'org.json', name: 'json', version: '20090211'`
5. Create the test configuration file<br>`$ cd src/test/resources/assets`<br>`$ cp test.properties local-test.properties`<br>Open local-test.properteis and edit replicationServer pointing to your Sync-Gateway (eg. 127.0.0.1 if you run the Sync-Gateway locally).
6. Install and Run Sync-Gateway (See [Getting Started With Sync Gateway](http://docs.couchbase.com/sync-gateway/)).<br>You can use a sample Sync-Gateway configuration to run the test suits from [here](https://friendpaste.com/5Xkuwge1Qx1D6DoIdFiQfc).
7. Build the project and run the tests<br>`$ ./gradlew clean && ./gradlew test`

# Package the library

```
$ ./gradlew distZip
```
Note: The packaged file will be located at build/distributions.



