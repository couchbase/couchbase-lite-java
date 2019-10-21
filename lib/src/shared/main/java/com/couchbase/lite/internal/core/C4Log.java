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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.couchbase.lite.ConsoleLogger;
import com.couchbase.lite.CouchbaseLite;
import com.couchbase.lite.Database;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;
import com.couchbase.lite.internal.support.Log;


public final class C4Log {
    private C4Log() {} // Utility class

    private static LogLevel callbackLevel;

    public static void setCallbackLevel(@NonNull LogLevel level) {
        setCallbackLevel(level, Database.log.getCustom());
    }

    // This method is called by reflection.  Don't change its name.
    private static void logCallback(String c4Domain, int c4Level, String message) {
        final LogLevel level = Log.getLogLevelForC4Level(c4Level);
        final LogDomain domain = Log.getLoggingDomainForC4Domain(c4Domain);

        final com.couchbase.lite.Log logger = Database.log;

        final ConsoleLogger console = logger.getConsole();
        console.log(level, domain, message);

        final Logger custom = logger.getCustom();
        if (custom != null) { custom.log(level, domain, message); }

        // This is necessary because there is no way to tell when the log level is set on a custom logger.
        // The only way to find out is to ask it.  As each new message comes in from Core,
        // we find the min level for the console and custom loggers and, if necessary, reset the callback level.
        setCallbackLevel(console.getLevel(), custom);
    }

    private static void setCallbackLevel(@NonNull LogLevel consoleLevel, @Nullable Logger customLogger) {
        LogLevel logLevel = consoleLevel;

        if (customLogger != null) {
            final LogLevel customLogLevel = customLogger.getLevel();
            if (customLogLevel.compareTo(logLevel) < 0) { logLevel = customLogLevel; }
        }

        if (C4Log.callbackLevel == logLevel) { return; }
        C4Log.callbackLevel = logLevel;

        // This cannot be done synchronously because it will deadlock
        // on the same mutex that is being held for this callback
        final int level = logLevel.getValue();
        CouchbaseLite.getExecutionService().getMainExecutor().execute(() -> setCallbackLevel(level));
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public static native void setLevel(String domain, int level);

    public static native int getBinaryFileLevel();

    public static native void setBinaryFileLevel(int level);

    public static native void log(String domain, int level, String message);

    public static native void writeToBinaryFile(
        String path,
        int level,
        int maxRotateCount,
        long maxSize,
        boolean usePlaintext,
        String header);

    private static native void setCallbackLevel(int level);
}
