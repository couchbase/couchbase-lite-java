//
// CouchbaseLite.java
//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.JavaExecutionService;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.support.Log;


public final class CouchbaseLite {
    // Utility class
    private CouchbaseLite() {}

    private static final String ERRORS_PROPERTIES_PATH = "/errors.properties";

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();

    /**
     * Initialize CouchbaseLite library. This method MUST be called before
     * using CouchbaseLite.
     */
    public static void init() {
        if (INITIALIZED.getAndSet(true)) { return; }

        NativeLibrary.load();

        MValue.registerDelegate(new MValueDelegate());

        Log.initLogging(loadErrorMessages());
    }

    /**
     * This method is for internal used only and will be removed in the future release.
     */
    public static ExecutionService getExecutionService() {
        ExecutionService executionService = EXECUTION_SERVICE.get();
        if (executionService == null) {
            EXECUTION_SERVICE.compareAndSet(null, new JavaExecutionService());
            executionService = EXECUTION_SERVICE.get();
        }
        return executionService;
    }

    static void requireInit(String message) {
        if (!INITIALIZED.get()) {
            throw new IllegalStateException(message + ".  Did you forget to call CouchbaseLite.init()?");
        }
    }

    static String getDbDirectoryPath() {
        requireInit("Database directory not initialized");
        return verifyDir(new File("").getAbsoluteFile());
    }

    static String getTmpDirectory(@NonNull String name) {
        requireInit("Temp directory not initialized");
        return getTmpDirectory(new File(System.getProperty("java.io.tmpdir")).getAbsolutePath(), name);
    }

    static String getTmpDirectory(String root, String name) {
        requireInit("Temp directory not initialized");
        return verifyDir(new File(root, name));
    }

    @VisibleForTesting
    static void reset() { INITIALIZED.set(false); }

    @VisibleForTesting
    @SuppressWarnings("unchecked")
    @NonNull
    static Map<String, String> loadErrorMessages() {
        final Properties errors = new Properties();
        try (InputStream is = CouchbaseLite.class.getResourceAsStream(ERRORS_PROPERTIES_PATH)){
            errors.load(is);
        } catch (IOException e) {
            Log.e(LogDomain.DATABASE, "Failed to load error messages!", e);
        }
        return (Map<String, String>) (Map) errors;
    }

    @NonNull
    private static String verifyDir(@NonNull File dir) {
        final String path = dir.getAbsolutePath();
        if ((dir.exists() || dir.mkdirs()) && dir.isDirectory()) { return path; }

        throw new IllegalStateException("Cannot create or access temp directory at " + path);
    }
}
