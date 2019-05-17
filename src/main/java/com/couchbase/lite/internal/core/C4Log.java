//
// C4Log.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.couchbase.lite.Database;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;


public class C4Log {
    private static final Map<String, com.couchbase.lite.LogDomain> DOMAIN_OBJECTS;
    static {
        final Map<String, com.couchbase.lite.LogDomain> m = new HashMap<>();
        m.put(C4Constants.LogDomain.DATABASE, com.couchbase.lite.LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.QUERY, com.couchbase.lite.LogDomain.QUERY);
        m.put(C4Constants.LogDomain.SYNC, com.couchbase.lite.LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.SYNC_BUSY, com.couchbase.lite.LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.BLIP, com.couchbase.lite.LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.WEB_SOCKET, com.couchbase.lite.LogDomain.NETWORK);
        DOMAIN_OBJECTS = Collections.unmodifiableMap(m);
    }

    private static LogLevel currentLevel = LogLevel.WARNING;

    static void logCallback(String domainName, int level, String message) {
        recalculateLevels();

        com.couchbase.lite.LogDomain domain = com.couchbase.lite.LogDomain.DATABASE;
        if (DOMAIN_OBJECTS.containsKey(domainName)) {
            domain = DOMAIN_OBJECTS.get(domainName);
        }

        Database.log.getConsole().log(LogLevel.values()[level], domain, message);
        final Logger customLogger = Database.log.getCustom();
        if (customLogger != null) {
            customLogger.log(LogLevel.values()[level], domain, message);
        }
    }

    private static void recalculateLevels() {
        final Logger customLogger = Database.log.getCustom();

        LogLevel callbackLevel = Database.log.getConsole().getLevel();
        if ((customLogger != null) && (customLogger.getLevel().compareTo(callbackLevel) < 0)) {
            callbackLevel = customLogger.getLevel();
        }

        if (currentLevel == callbackLevel) { return; }
        currentLevel = callbackLevel;

        final LogLevel finalLevel = callbackLevel;

        //!!! EXECUTOR
        Executors.newSingleThreadExecutor().execute(() -> {
            // This cannot be done synchronously because it will deadlock
            // on the same mutex that is being held for this callback
            setCallbackLevel(finalLevel.getValue());
        });
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public static native void setLevel(String domain, int level);

    public static native void log(String domain, int level, String message);

    public static native int getBinaryFileLevel();

    public static native void setBinaryFileLevel(int level);

    public static native void writeToBinaryFile(
        String path,
        int level,
        int maxRotateCount,
        long maxSize,
        boolean usePlaintext,
        String header);

    public static native void setCallbackLevel(int level);
}
