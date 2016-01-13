/**
 * Created by Pasin Suriyentrakorn.
 *
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
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

package com.couchbase.lite.storage;

import com.couchbase.lite.internal.database.DatabasePlatformSupport;

public class JavaSQLiteStorageEngine extends SQLiteStorageEngineBase {
    static {
        System.out.println("JavaSQLiteStorageEngine");
    }
    @Override
    protected DatabasePlatformSupport getDatabasePlatformSupport() {
        return new JavaPlatformSupport();
    }

    private class JavaPlatformSupport implements DatabasePlatformSupport {
        @Override
        public boolean isMainThread() {
            // No standard way to check if the current thread is the main thread in Java.
            return false;
        }
    }

    @Override
    protected String getICUDatabasePath() {
        return null;
    }
}
