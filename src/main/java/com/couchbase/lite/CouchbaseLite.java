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

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.JavaExecutionService;
import com.couchbase.lite.internal.fleece.MValue;

public final class CouchbaseLite {
    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();

    /**
     * Initialize CouchbaseLite library. This method MUST be called before
     * using CouchbaseLite.
     */
    public static void init() {
        NativeLibrary.load();
        MValue.registerDelegate(new MValueDelegate());
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

    static String getDbDirectoryPath() {
        return Paths.get("").toAbsolutePath().toString();
    }

    static String getTmpDirectory(@NonNull String name) {
        final String root = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
        return getTmpDirectory(root, name);
    }

    static String getTmpDirectory(String root, String name) {
        final File dir = new File(root, name);

        final String path = dir.getAbsolutePath();
        if ((dir.exists() || dir.mkdirs()) && dir.isDirectory()) { return path; }

        throw new IllegalStateException("Cannot create or access temp directory at " + path);
    }
}
