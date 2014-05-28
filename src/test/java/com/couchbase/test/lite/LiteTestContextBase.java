package com.couchbase.test.lite;

// https://github.com/couchbase/couchbase-lite-android/issues/285

import java.io.*;
import java.nio.file.*;

/**
 * Provides a platform specific way to create a safe temporary directory location since this is different in Java
 * and Android
 */
public class LiteTestContextBase {
    private File rootDirectory;

    public LiteTestContextBase() {
        try {
            rootDirectory = Files.createTempDirectory("couchbaselitetest").toFile();
        } catch (IOException e) {
            throw new RuntimeException("Could not create temp directory!", e);
        }
    }

    public File getRootDirectory() {
        return rootDirectory;
    }
}
