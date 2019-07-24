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

import com.couchbase.lite.internal.ExecutionService;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Platform test class for Java.
 */
public abstract class PlatformBaseTest implements PlatformTest {

    @Override
    public void initCouchbaseLite() {
        CouchbaseLite.init();
    }

    @Override
    public String getDatabaseDirectory() {
        return CouchbaseLite.getDbDirectoryPath();
    }

    @Override
    public String getTempDirectory(String name) {
        return CouchbaseLite.getTmpDirectory(name);
    }

    @Override
    public boolean isAndroid() {
        return false;
    }

    @Override
    public boolean isAndroidEmulator() {
        return false;
    }

    @Override
    public boolean isDebugBuild() {
        // How to detect this if we need to run unit tests on release mode?
        return true;
    }

    @Override
    public int getSystemVersion() {
        return 0;
    }

    @Override
    public InputStream getAsset(String assetFile) {
        return getClass().getClassLoader().getResourceAsStream(assetFile);
    }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLite.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }

}
