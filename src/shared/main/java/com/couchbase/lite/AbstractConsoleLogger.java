//
// AbstractConsoleLogger.java
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
import android.support.annotation.VisibleForTesting;

import java.util.EnumSet;

import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The base console logger class.
 */
abstract class AbstractConsoleLogger implements Logger {
    private EnumSet<LogDomain> logDomains = LogDomain.ALL_DOMAINS;
    private LogLevel logLevel = LogLevel.WARNING;

    // Singleton instance accessible from Database.log.getConsole()
    AbstractConsoleLogger() { }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if ((level.compareTo(logLevel) < 0) || (!logDomains.contains(domain))) { return; }
        doLog(level, domain, message);
    }

    @NonNull
    @Override
    public LogLevel getLevel() { return logLevel; }

    /**
     * Sets the overall logging level that will be written to the console log.
     *
     * @param level The lowest (most verbose) level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        Preconditions.checkArgNotNull(level, "level");

        if (logLevel == level) { return; }

        logLevel = level;
        C4Log.setCallbackLevel(logLevel);
    }

    /**
     * Gets the domains that will be considered for writing to the console log.
     *
     * @return The currently active domains
     */
    @NonNull
    public EnumSet<LogDomain> getDomains() { return logDomains; }

    /**
     * Sets the domains that will be considered for writing to the console log.
     *
     * @param domains The domains to make active
     */
    public void setDomains(@NonNull EnumSet<LogDomain> domains) {
        Preconditions.checkArgNotNull(domains, "domains");
        logDomains = (!domains.contains(LogDomain.ALL))
            ? domains
            : LogDomain.ALL_DOMAINS;
    }

    protected abstract void doLog(LogLevel level, @NonNull LogDomain domain, @NonNull String message);

    @VisibleForTesting
    void reset() {
        logDomains = LogDomain.ALL_DOMAINS;
        logLevel = LogLevel.WARNING;
    }
}
