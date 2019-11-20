//
// FileLogger.java
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.io.File;

import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.support.Log;


/**
 * A logger for writing to a file in the application's storage so
 * that log messages can persist durably after the application has
 * stopped or encountered a problem.  Each log level is written to
 * a separate file.
 * Threading policy: This class is certain to be used from multiple
 * threads.  As long as it is thread safe, the various race conditions
 * are unlikely and the penalties very small.  "Volatile" ensures
 * the thread safety and the several races are tolerable.
 */
public final class FileLogger implements Logger {
    @Nullable
    private volatile LogFileConfiguration config;
    @Nullable
    private volatile String initializedPath;
    @NonNull
    private volatile LogLevel logLevel;

    // The singleton instance is available from Database.log.getFile()
    FileLogger() { reset(); }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if ((config == null) || (level.compareTo(logLevel) < 0)) { return; }
        C4Log.log(Log.getC4DomainForLoggingDomain(domain), level.getValue(), message);
    }

    @NonNull
    @Override
    public LogLevel getLevel() { return logLevel; }

    /**
     * Sets the overall logging level that will be written to the logging files.
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        if (config == null) {
            throw new IllegalStateException(Log.lookupStandardMessage("CannotSetLogLevel"));
        }

        if (logLevel == level) { return; }
        logLevel = level;

        if (!initLog()) { C4Log.setBinaryFileLevel(level.getValue()); }

        if (level == LogLevel.NONE) { Log.warn(); }
    }

    /**
     * Gets the configuration currently in use by the file logger.
     * Note that once a configuration has been installed in a logger,
     * the configuration is read-only and can no longer be modified.
     * An attempt to modify the configuration returned by this method will cause an exception.
     *
     * @return The configuration currently in use
     */
    @Nullable
    public LogFileConfiguration getConfig() { return config; }

    /**
     * Sets the configuration for use by the file logger.
     *
     * @param newConfig The configuration to use
     */
    public void setConfig(@Nullable LogFileConfiguration newConfig) {
        if (config == newConfig) { return; }

        if (newConfig == null) {
            config = null;
            Log.warn();
            return;
        }

        final File logDir = new File(newConfig.getDirectory());
        String errMsg = null;
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) { errMsg = "Cannot create log directory: " + logDir.getAbsolutePath(); }
        }
        else {
            if (!logDir.isDirectory()) { errMsg = logDir.getAbsolutePath() + " is not a directory"; }
            else if (!logDir.canWrite()) { errMsg = logDir.getAbsolutePath() + " is not writable"; }
        }

        if (errMsg != null) {
            Log.w(LogDomain.DATABASE, errMsg);
            return;
        }

        config = newConfig.readOnlyCopy();

        initLog();
    }

    @VisibleForTesting
    void reset() {
        config = null;
        initializedPath = null;
        logLevel = LogLevel.NONE;
    }

    private boolean initLog() {
        final LogLevel level = logLevel;
        final LogFileConfiguration cfg = config;

        if ((cfg == null) || (level == LogLevel.NONE)) { return false; }

        final String logDirPath = cfg.getDirectory();
        if (logDirPath.equals(initializedPath)) { return false; }
        initializedPath = logDirPath;

        C4Log.writeToBinaryFile(
            logDirPath,
            level.getValue(),
            cfg.getMaxRotateCount(),
            cfg.getMaxSize(),
            cfg.usesPlaintext(),
            CBLVersion.getVersionInfo());

        return true;
    }
}

