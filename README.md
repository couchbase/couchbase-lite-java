
Once finished, this will be a "portable java" version of the Couchbase Lite Android core.  More info [here](https://github.com/couchbase/couchbase-lite-android/wiki/Proposed-project-restructure).

# Run via command line

```
$ ./gradlew clean && ./gradlew test
```

# Run via IntelliJ CE 13

* Import project
* Define a new run target to run the tests
* Hit the play button

# Fixing the UnsatisfiedLinkError

As mentioned in the [sqlite4java docs](https://code.google.com/p/sqlite4java/wiki/UsingWithMaven), sqlite4java does not work "out of the box" with Maven.

Here's what I did to make this work on my OSX system:

```
$ cd ~/.gradle
$ cp ./caches/modules-2/files-2.1/com.almworks.sqlite4java/libsqlite4java-osx/0.282/e18dabd7fe70e37771457a63162fe6f028628ac9/libsqlite4java-osx-0.282.jnilib ./caches/modules-2/files-2.1/com.almworks.sqlite4java/sqlite4java/0.282/745a7e2f35fdbe6336922e0d492c979dbbfa74fb/
$ cd ./caches/modules-2/files-2.1/com.almworks.sqlite4java/sqlite4java/0.282/745a7e2f35fdbe6336922e0d492c979dbbfa74fb/
$ java -jar sqlite4java-0.282.jar -d
```

Now, instead of getting an `java.lang.UnsatisfiedLinkError: no sqlite4java-osx-x86_64-0.282 in java.library.path` it should output `[sqlite] Internal: loaded sqlite 3.7.10, wrapper 0.2`

