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
 */
public final class FileLogger implements Logger {
    private LogFileConfiguration config;
    private LogLevel logLevel = LogLevel.WARNING;

    // The singleton instance is available from Database.log.getFile()
    FileLogger() { }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if ((config == null) || (level.compareTo(logLevel) < 0)) { return; }
        C4Log.log(Log.getC4DomainForLoggingDomain(domain), level.getValue(), message);
    }

    @NonNull
    @Override
    public LogLevel getLevel() {
        logLevel = Log.getLogLevelForC4Level(C4Log.getBinaryFileLevel());
        return logLevel;
    }

    /**
     * Sets the overall logging level that will be written to the logging files.
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        if (config == null) {
            throw new IllegalStateException("Cannot set the file logger's level before setting its configuration");
        }

        if (logLevel == level) { return; }

        logLevel = level;
        C4Log.setBinaryFileLevel(level.getValue());
    }

    /**
     * Gets the configuration currently in use on the file logger.
     * Note that once it is set, it can no longer be modified and doing so
     * will throw an exception
     *
     * @return The configuration currently in use
     */
    public LogFileConfiguration getConfig() { return config; }

    /**
     * Sets the configuration currently to use on the file logger.
     * Note that once it is set, it can no longer be modified and doing so
     * will throw an exception
     *
     * @param config The configuration to use
     */
    public void setConfig(@Nullable LogFileConfiguration config) {
        if (this.config != null) { throw new IllegalStateException("Attempt to reset the file logger config"); }

        if (config == null) { return; }

        final String logDirPath = config.getDirectory();

        final File logDir = new File(logDirPath);
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

        this.config = config.readOnlyCopy();

        C4Log.writeToBinaryFile(
            logDirPath,
            LogLevel.INFO.getValue(),
            config.getMaxRotateCount(),
            config.getMaxSize(),
            config.usesPlaintext(),
            CBLVersion.getVersionInfo());
    }

    @VisibleForTesting
    void reset() {
        logLevel = LogLevel.WARNING;
        config = null;
    }
}

