package com.couchbase.test.lite;

import com.couchbase.lite.storage.JavaSQLiteStorageEngineFactory;
import com.couchbase.lite.storage.SQLiteStorageEngineFactory;

import java.io.*;

/**
 * Provides a platform specific way to create a safe temporary directory location since this is different in Java
 * and Android
 *
 * Reference issue : https://github.com/couchbase/couchbase-lite-android/issues/285
 */
public class LiteTestContextBase {
    private File rootDirectory;

    public LiteTestContextBase() {
        rootDirectory = new File("couchbaselitetest");
        if (!rootDirectory.exists()) {
            rootDirectory.mkdir();
        }
        
        // ensure to have the absolute file path
        rootDirectory = rootDirectory.getAbsoluteFile();
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public SQLiteStorageEngineFactory getSQLiteStorageEngineFactory() {
        return new JavaSQLiteStorageEngineFactory();
    }
}
