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
package com.couchbase.lite.internal;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Base;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Among the other things that this class attempts to abstract away, is access to the file system.
 * On both Android, and in a Web Container, file system access is pretty problematic.
 * Among other things, some code make the tacit assumption that there is a single root directory
 * that contains both a scratch (temp) directory and the database directory.  The scratch directory
 * is also used, occasionally, as the home for log files.
 */
public final class CouchbaseLiteInternal {
    // Utility class
    private CouchbaseLiteInternal() {}

    private static final String ERRORS_PROPERTIES_PATH = "/errors.properties";
    private static final String TEMP_DIR_NAME = "CouchbaseLiteTemp";
    private static final String DEFAULT_ROOT_DIR_NAME = ".couchbase";

    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static final Object LOCK = new Object();

    @GuardedBy("lock")
    private static String dbDirPath;
    @GuardedBy("lock")
    private static String tmpDirPath;

    public static void init(@NonNull MValue.Delegate mValueDelegate, @Nullable String rootDirectoryPath) {
        Preconditions.assertNotNull(mValueDelegate, "mValueDelegate");

        if (INITIALIZED.getAndSet(true)) { return; }

        // This is complicated by the fact that we need the temp directory
        // in order to load the native libraries, but that the native libraries
        // need to know where the temp directory is...
        initDirectories(rootDirectoryPath);

        NativeLibrary.load();

        C4Base.debug();

        setC4TmpDirPath();

        MValue.registerDelegate(mValueDelegate);

        Log.initLogging(loadErrorMessages());
    }

    public static boolean isDebugging() { return false; }

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

    public static void requireInit(String message) {
        if (!INITIALIZED.get()) {
            throw new IllegalStateException(message + ".  Did you forget to call CouchbaseLite.init()?");
        }
    }

    @NonNull
    public static String makeDbPath(@Nullable String rootDir) {
        requireInit("Can't create DB path");
        return verifyDir(new File((rootDir == null) ? DEFAULT_ROOT_DIR_NAME : rootDir));
    }

    @NonNull
    public static String makeTmpPath(@Nullable String rootDir) {
        requireInit("Can't create tmp dir path");
        return verifyDir(new File((rootDir != null) ? rootDir : System.getProperty("java.io.tmpdir"), TEMP_DIR_NAME));
    }

    public static void setupDirectories(@Nullable String rootDirPath) {
        requireInit("Can't set root directory");

        synchronized (LOCK) {
            // remember the current tmp dir
            final String tmpPath = tmpDirPath;

            initDirectories(rootDirPath);

            // if the temp dir has changed, tell C4Base
            if (!Objects.equals(tmpPath, tmpDirPath)) { setC4TmpDirPath(); }
        }
    }

    @NonNull
    public static String getDbDirectoryPath() {
        requireInit("Database directory not initialized");
        synchronized (LOCK) { return dbDirPath; }
    }

    @NonNull
    public static String getTmpDirectoryPath() {
        requireInit("Database directory not initialized");
        synchronized (LOCK) { return tmpDirPath; }
    }

    @VisibleForTesting
    public static void reset() { INITIALIZED.set(false); }

    @VisibleForTesting
    @SuppressWarnings("unchecked")
    @NonNull
    public static Map<String, String> loadErrorMessages() {
        final Properties errors = new Properties();
        try (InputStream is = CouchbaseLiteInternal.class.getResourceAsStream(ERRORS_PROPERTIES_PATH)) {
            errors.load(is);
        }
        catch (IOException e) { Log.e(LogDomain.DATABASE, "Failed to load error messages!", e); }
        return (Map<String, String>) (Map) errors;
    }

    @NonNull
    private static String verifyDir(@NonNull File dir) {
        String path = dir.getAbsolutePath();
        try {
            path = dir.getCanonicalPath();

            if (!((dir.exists() || dir.mkdirs()) && dir.isDirectory())) {
                throw new IOException("Cannot create directory: " + path);
            }

            return path;
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot create or access temp directory at " + path, e);
        }
    }

    private static void initDirectories(@Nullable String rootDirPath) {
        final String dbPath = makeDbPath(rootDirPath);
        final String tmpPath = makeTmpPath(rootDirPath);

        synchronized (LOCK) {
            tmpDirPath = tmpPath;
            dbDirPath = dbPath;
        }
    }

    private static void setC4TmpDirPath() {
        synchronized (LOCK) { C4Base.setTempDir(tmpDirPath); }
    }
}
