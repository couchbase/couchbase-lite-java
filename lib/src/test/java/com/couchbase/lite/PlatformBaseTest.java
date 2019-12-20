//
// PlatformBaseTest.java
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.support.Log;


/**
 * Platform test class for Java.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Java";
    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%&'()_+{}][=-.,;'ABCDEabcde";

    private String tmpDirPath;

    @Override
    public void initCouchbaseLite() { CouchbaseLite.init(); }

    // set up the file logger...
    @Override
    public void setupFileLogging() { }

    @Override
    public String getDatabaseDirectoryPath() { return CouchbaseLiteInternal.getDbDirectoryPath(); }

    @Override
    public String getScratchDirectoryPath(String name) {
        if (tmpDirPath == null) { tmpDirPath = CouchbaseLiteInternal.getTmpDirectoryPath(); }

        try {
            final File tmpDir = new File(tmpDirPath, name);
            if (tmpDir.exists() || tmpDir.mkdirs()) { return tmpDir.getCanonicalPath(); }
            throw new IOException("Could create tmp directory: " + name);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed creating temp directory");
        }
    }

    @Override
    public InputStream getAsset(String assetFile) {
        return getClass().getClassLoader().getResourceAsStream(assetFile);
    }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }

    @Override
    public void reloadStandardErrorMessages() {
        Log.initLogging(CouchbaseLiteInternal.loadErrorMessages());
    }
}
