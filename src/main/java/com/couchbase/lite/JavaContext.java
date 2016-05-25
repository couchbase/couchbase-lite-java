/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import com.couchbase.lite.storage.JavaSQLiteStorageEngineFactory;
import com.couchbase.lite.storage.SQLiteStorageEngineFactory;
import com.couchbase.lite.support.Version;

import java.io.File;

/**
 * This is the default couchbase lite context when running "portable java" (eg, non-Android platforms
 * such as Linux or OSX).
 *
 * If you are running on Android, you will want to use AndroidContext instead.  At the time of writing,
 * the AndroidContext is currently not available in the javadocs due to an issue in our build
 * infrastructure.
 */
public class JavaContext implements Context {
    private String subdir;

    public JavaContext(String subdir) {
        this.subdir = subdir;
    }

    public JavaContext() {
        this.subdir = "cblite";
    }

    @Override
    public File getFilesDir() {
        return new File(getRootDirectory(), subdir);
    }

    @Override
    public File getTempDir() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    @Override
    public void setNetworkReachabilityManager(NetworkReachabilityManager networkReachabilityManager) {

    }

    @Override
    public NetworkReachabilityManager getNetworkReachabilityManager() {
        return new FakeNetworkReachabilityManager();
    }

    @Override
    public SQLiteStorageEngineFactory getSQLiteStorageEngineFactory() {
        return new JavaSQLiteStorageEngineFactory();
    }

    public File getRootDirectory() {
        String rootDirectoryPath = System.getProperty("user.dir");
        return new File(rootDirectoryPath, "data/data/com.couchbase.lite.test/files");
    }

    class FakeNetworkReachabilityManager extends NetworkReachabilityManager {
        @Override
        public void startListening() {
        }

        @Override
        public void stopListening() {
        }

        @Override
        public boolean isOnline() {
            return true;
        }
    }

    @Override
    public String getUserAgent() {
        return String.format("CouchbaseLite/%s (Java %s/%s %s/%s)",
                Version.SYNC_PROTOCOL_VERSION,
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                Version.getVersionName(),
                Version.getCommitHash());
    }
}
