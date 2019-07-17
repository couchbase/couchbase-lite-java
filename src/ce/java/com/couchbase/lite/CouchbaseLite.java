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

import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.fleece.MValue;

import java.io.File;
import java.nio.file.Paths;

public final class CouchbaseLite {
    private CouchbaseLite() {
        NativeLibraryLoader.load();
        MValue.registerDelegate(new MValueDelegate());
    }

    public static void init() { }

    public static ExecutionService getExecutionService() {
        return null;
    }

    static String getDbDirectoryPath() {
        return Paths.get("").toAbsolutePath().toString();
    }

    static String getTmpDirectory(@NonNull String name) {
        String root = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
        return getTmpDirectory(root, name);
    }

    static String getTmpDirectory(String root, String name) {
        final File dir = new File(root, name);

        final String path = dir.getAbsolutePath();
        if ((dir.exists() || dir.mkdirs()) && dir.isDirectory()) { return path; }

        throw new IllegalStateException("Cannot create or access temp directory at " + path);
    }
}
