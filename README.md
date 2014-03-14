This is a "portable java" version of the Couchbase Lite.  

To see how this fits into the overall architecture, see the [Couchbase Lite Project Structure](https://github.com/couchbase/couchbase-lite-android/wiki/Project-structure).


# How to build and test

```
$ ./gradlew clean && ./gradlew build
$ ./gradlew test

```

# How to package the library

```
$ ./gradlew distZip

```
Note: The packaged file will be located at build/distributions.



