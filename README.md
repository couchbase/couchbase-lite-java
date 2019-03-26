
= Proto Couchbase Lite Java

This is the source for the Couchbase Lite Java product,
a pure Java implementation of Couchbase Lite to be run
on the JVM

It is a completely new implementation, utterly divergent from
the 1.x project that formerly occupied this space.

The shared source (src/main/{cpp, java} and the accompanying
CMake files, are used by the Couchbase Lite Android and
Android-EE products.

The CMake tools here may not work at all.  Only CMakeLists.txt
is actually being used/tested and it only from the Android products.

The code in src/ce/java is near minimum necessary to get
this project to compile.  It is in no way functional, yet.

This project does not bind any version of core.  An attempt
to use it in a running app will fail during class loading
when it attempts to load the core-lite native library.

