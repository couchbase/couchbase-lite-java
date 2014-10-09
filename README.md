This is a "portable java" version of the Couchbase Lite.  

To see how this fits into the overall architecture, see the [Couchbase Lite Project Structure](https://github.com/couchbase/couchbase-lite-android/wiki/Project-structure).

# Get the code
```
$ git clone <this repo>
$ git submodule init && git submodule update
```
# Build and test

### Requirements
* JDK 1.7+

* Toolchains for compiling native libraries as follows :

	| Operating System | Tool Chain       | Notes
	| ---------------- |:----------------:|-------------
	| Linux            | GCC and G++      |
	| Mac OSX          | GCC or Clang     | Install Command Line Tools for Xcode available at the [Apple Developer website](https://developer.apple.com/downloads). 
	| Windows          | Visual C++      | Install Visual C++ 2010 and later.

* To setup a Linux cross complier to compile both x86 and x86_64 linux native libraries on 64 bit machine, you may setup your toolchain as below :

	```
	$ sudo apt-get install gcc-multilib
	$ sudo apt-get install g++-multilib
	``` 

### Build and Test Steps

Note: Currently we are sharing the test suits with [Couchbase-lite-android](https://github.com/couchbase/couchbase-lite-android.git) project and the following steps will copy the test classes over and run the test suit.

1. Clone Couchbase-lite-android project<br>`$ clone https://github.com/couchbase/couchbase-lite-android.git` at the same folder where the couchbase-lite-java is located.
2. At your couchbase-lite-android folder, create the test configuration file<br>`$ cd couchbase-lite-andriod`<br>`$ cp src/androidTest/assets/test.properties src/androidTest/assets/local-test.properties`<br>Open src/androidTest/assets/local-test.properteis and edit replicationServer pointing to your Sync-Gateway (eg. 127.0.0.1 if you run the Sync-Gateway locally).
3. Install and Run Sync-Gateway (See [Getting Started With Sync Gateway](http://docs.couchbase.com/sync-gateway/)).<br>You can use a sample Sync-Gateway configuration to run the test suits from [here](https://friendpaste.com/5Xkuwge1Qx1D6DoIdFiQfc).
4. Go to your couchbase-lite-java, build the project and run the tests<br>`$ ./gradlew clean && ./gradlew test`

# Package the library

```
$ ./gradlew distZip
```
Note: The packaged file will be located at build/distributions.



