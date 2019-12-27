//
// Log.java
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
package com.couchbase.lite.internal.support;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.FormatterClosedException;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.ConsoleLogger;
import com.couchbase.lite.Database;
import com.couchbase.lite.FileLogger;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;


/**
 * Couchbase Lite Internal Log Utility.
 */
public final class Log {
    private Log() { } // Utility class

    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    private static final String DEFAULT_MSG = "Unknown error";

    public static final Map<String, LogDomain> LOGGING_DOMAINS_FROM_C4;
    static {
        final Map<String, LogDomain> m = new HashMap<>();
        m.put(C4Constants.LogDomain.DATABASE, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.QUERY, LogDomain.QUERY);
        m.put(C4Constants.LogDomain.SYNC, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.SYNC_BUSY, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.BLIP, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.WEB_SOCKET, LogDomain.NETWORK);
        LOGGING_DOMAINS_FROM_C4 = Collections.unmodifiableMap(m);
    }

    public static final Map<LogDomain, String> LOGGING_DOMAINS_TO_C4;
    static {
        final Map<LogDomain, String> m = new HashMap<>();
        m.put(LogDomain.DATABASE, C4Constants.LogDomain.DATABASE);
        m.put(LogDomain.QUERY, C4Constants.LogDomain.QUERY);
        m.put(LogDomain.REPLICATOR, C4Constants.LogDomain.SYNC);
        m.put(LogDomain.NETWORK, C4Constants.LogDomain.WEB_SOCKET);
        LOGGING_DOMAINS_TO_C4 = Collections.unmodifiableMap(m);
    }

    public static final Map<Integer, LogLevel> LOG_LEVEL_FROM_C4;
    static {
        final Map<Integer, LogLevel> m = new HashMap<>();
        for (LogLevel level : LogLevel.values()) { m.put(level.getValue(), level); }
        LOG_LEVEL_FROM_C4 = Collections.unmodifiableMap(m);
    }

    private static volatile Map<String, String> errorMessages;

    /**
     * Setup logging.
     */
    public static void initLogging(@NonNull Map<String, String> errorMessages) {
        C4Log.forceCallbackLevel(Database.log.getConsole().getLevel());
        setC4LogLevel(LogDomain.ALL_DOMAINS, LogLevel.DEBUG);

        Log.errorMessages = Collections.unmodifiableMap(errorMessages);

        Log.i(LogDomain.DATABASE, "Couchbase Lite initialized: " + CBLVersion.getVersionInfo());
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void d(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.DEBUG, domain, null, msg); }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void d(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.DEBUG, domain, err, msg);
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void d(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.DEBUG, domain, null, msg, args);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void d(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.DEBUG, domain, err, msg, args);
    }

    /**
     * Send a VERBOSE message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void v(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.VERBOSE, domain, null, msg); }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void v(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.VERBOSE, domain, err, msg);
    }

    /**
     * Send a VERBOSE message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void v(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.VERBOSE, domain, null, msg, args);
    }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void v(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.VERBOSE, domain, err, msg, args);
    }

    /**
     * Send an INFO message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.INFO, domain, null, msg); }

    public static void info(@NonNull LogDomain domain, @NonNull String msg) { i(domain, msg); }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.INFO, domain, err, msg);
    }

    public static void info(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        i(domain, msg, err);
    }

    /**
     * Send an INFO message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.INFO, domain, null, msg, args);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.INFO, domain, err, msg, args);
    }

    /**
     * Send a WARN message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void w(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.WARNING, domain, null, msg); }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void w(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.WARNING, domain, err, msg);
    }

    /**
     * Send a WARN message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void w(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.WARNING, domain, null, msg, args);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void w(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.WARNING, domain, err, msg, args);
    }

    /**
     * Send an ERROR message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void e(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.ERROR, domain, null, msg); }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void e(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.ERROR, domain, err, msg);
    }

    /**
     * Send a ERROR message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void e(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.ERROR, domain, null, msg, args);
    }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void e(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.ERROR, domain, err, msg, args);
    }

    @NonNull
    public static String lookupStandardMessage(@Nullable String msg) {
        if (msg == null) { return DEFAULT_MSG; }  // Don't let logging errors cause an abort
        final String message = (errorMessages == null) ? msg : errorMessages.get(msg);
        return (message == null) ? msg : message;
    }

    @NonNull
    public static String formatStandardMessage(@Nullable String msg, Object... args) {
        return String.format(Locale.ENGLISH, lookupStandardMessage(msg), args);
    }

    @NonNull
    public static LogLevel getLogLevelForC4Level(int c4Level) {
        final LogLevel level = LOG_LEVEL_FROM_C4.get(c4Level);
        return (level != null) ? level : LogLevel.INFO;
    }

    @NonNull
    public static String getC4DomainForLoggingDomain(@NonNull LogDomain domain) {
        final String c4Domain = LOGGING_DOMAINS_TO_C4.get(domain);
        return (c4Domain != null) ? c4Domain : C4Constants.LogDomain.DATABASE;
    }

    @NonNull
    public static LogDomain getLoggingDomainForC4Domain(@NonNull String c4Domain) {
        final LogDomain domain = LOGGING_DOMAINS_FROM_C4.get(c4Domain);
        return (domain != null) ? domain : LogDomain.DATABASE;
    }

    public static void setC4LogLevel(@NonNull EnumSet<LogDomain> domains, @NonNull LogLevel level) {
        final int c4Level = level.getValue();
        for (LogDomain domain : domains) {
            switch (domain) {
                case DATABASE:
                    C4Log.setLevel(C4Constants.LogDomain.DATABASE, c4Level);
                    break;

                case QUERY:
                    C4Log.setLevel(C4Constants.LogDomain.QUERY, c4Level);
                    break;

                case REPLICATOR:
                    C4Log.setLevel(C4Constants.LogDomain.SYNC, c4Level);
                    C4Log.setLevel(C4Constants.LogDomain.SYNC_BUSY, c4Level);
                    break;

                case NETWORK:
                    C4Log.setLevel(C4Constants.LogDomain.BLIP, c4Level);
                    C4Log.setLevel(C4Constants.LogDomain.WEB_SOCKET, c4Level);
                    break;
                default:
                    break;
            }
        }
    }

    public static void warn() {
        if (WARNED.getAndSet(true)) { return; }
        Log.w(
            LogDomain.DATABASE,
            "Database.log.getFile().getConfig() is now null: logging is disabled.  "
                + "Log files required for product support are not being generated.");
    }

    private static void log(
        @NonNull LogLevel level,
        @NonNull LogDomain domain,
        @Nullable Throwable err,
        @NonNull String msg,
        Object... args) {
        // Don't let logging errors cause a failure
        if (level == null) { level = LogLevel.INFO; }
        if (domain == null) { domain = LogDomain.DATABASE; }
        String message = lookupStandardMessage(msg);

        if ((args != null) && (args.length > 0)) { message = formatMessage(message, args); }

        if (err != null) {
            final StringWriter sw = new StringWriter();
            err.printStackTrace(new PrintWriter(sw));
            message += System.lineSeparator() + sw.toString();
        }

        sendToLoggers(level, domain, message);
    }

    private static String formatMessage(String msg, Object... args) {
        try { return String.format(Locale.ENGLISH, msg, args); }
        catch (IllegalFormatException | FormatterClosedException ignore) { }
        return msg;
    }

    private static void sendToLoggers(LogLevel level, LogDomain domain, String msg) {
        final com.couchbase.lite.Log logger = Database.log;

        // Console logging:
        final ConsoleLogger consoleLogger = logger.getConsole();
        Exception consoleErr = null;
        try { consoleLogger.log(level, domain, msg); }
        catch (Exception e) { consoleErr = e; }

        // File logging:
        final FileLogger fileLogger = logger.getFile();
        try {
            fileLogger.log(level, domain, msg);
            if (consoleErr != null) { consoleLogger.log(LogLevel.ERROR, LogDomain.DATABASE, consoleErr.toString()); }
        }
        catch (Exception e) {
            if (consoleErr == null) { fileLogger.log(LogLevel.ERROR, LogDomain.DATABASE, e.toString()); }
        }

        // Custom logging:
        final Logger custom = logger.getCustom();
        if (custom != null) {
            try { custom.log(level, domain, msg); }
            catch (Exception ignore) { }
        }
    }
}
