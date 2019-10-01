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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
    // Utility class.
    private Log() { }

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

    /**
     * Setup logging.
     */
    public static void initLogging() {
        setC4LogLevel(LogDomain.ALL_DOMAINS, LogLevel.DEBUG);
        Log.i(LogDomain.DATABASE, "Couchbase Lite initialized: " + CBLVersion.getVersionInfo());
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void d(LogDomain domain, String msg) { sendToLoggers(LogLevel.DEBUG, domain, msg); }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void d(LogDomain domain, String msg, Throwable tr) {
        d(domain, msg + ".  Exception: %s", tr.toString());
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void d(LogDomain domain, String formatString, Object... args) {
        String msg;
        try { msg = String.format(Locale.ENGLISH, formatString, args); }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.DEBUG, domain, msg);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void d(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.DEBUG, domain, msg);
    }

    /**
     * Send a VERBOSE message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void v(LogDomain domain, String msg) { sendToLoggers(LogLevel.VERBOSE, domain, msg); }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void v(LogDomain domain, String msg, Throwable tr) {
        v(domain, msg + ".  Exception: %s", tr.toString());
    }

    /**
     * Send a VERBOSE message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void v(LogDomain domain, String formatString, Object... args) {
        String msg;
        try { msg = String.format(Locale.ENGLISH, formatString, args); }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.VERBOSE, domain, msg);
    }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void v(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.VERBOSE, domain, msg);
    }

    /**
     * Send an INFO message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void i(LogDomain domain, String msg) { sendToLoggers(LogLevel.INFO, domain, msg); }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void i(LogDomain domain, String msg, Throwable tr) {
        i(domain, msg + ".  Exception: %s", tr.toString());
    }

    public static void info(LogDomain domain, String msg) { i(domain, msg); }

    public static void info(LogDomain domain, String msg, Throwable tr) { i(domain, msg, tr); }

    /**
     * Send an INFO message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void i(LogDomain domain, String formatString, Object... args) {
        String msg;
        try { msg = String.format(Locale.ENGLISH, formatString, args); }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.INFO, domain, msg);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void i(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.INFO, domain, msg);
    }

    /**
     * Send a WARN message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void w(LogDomain domain, String msg) { sendToLoggers(LogLevel.WARNING, domain, msg); }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param tr     An exception to log
     */
    public static void w(LogDomain domain, Throwable tr) { w(domain, "Exception: %s", tr.toString()); }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void w(LogDomain domain, String msg, Throwable tr) { w(domain, "%s: %s", msg, tr.toString()); }

    /**
     * Send a WARN message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void w(LogDomain domain, String formatString, Object... args) {
        String msg;
        try { msg = String.format(Locale.ENGLISH, formatString, args); }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.WARNING, domain, msg);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void w(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.WARNING, domain, msg);
    }

    /**
     * Send an ERROR message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void e(LogDomain domain, String msg) { sendToLoggers(LogLevel.ERROR, domain, msg); }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void e(LogDomain domain, String msg, Throwable tr) { e(domain, "%s: %s", msg, tr.toString()); }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void e(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.ERROR, domain, msg);
    }

    /**
     * Send a ERROR message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void e(LogDomain domain, String formatString, Object... args) {
        String msg;
        try { msg = String.format(Locale.ENGLISH, formatString, args); }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.ERROR, domain, msg);
    }

    @NonNull
    public static LogLevel getLogLevelForC4Level(int c4Level) {
        final LogLevel level = LOG_LEVEL_FROM_C4.get(c4Level);
        return (level != null) ? level : LogLevel.INFO;
    }

    @NonNull
    public static String getC4DomainForLoggingDomain(LogDomain domain) {
        final String c4Domain = LOGGING_DOMAINS_TO_C4.get(domain);
        return (c4Domain != null) ? c4Domain : C4Constants.LogDomain.DATABASE;
    }

    @NonNull
    public static LogDomain getLoggingDomainForC4Domain(@NonNull String c4Domain) {
        final LogDomain domain = LOGGING_DOMAINS_FROM_C4.get(c4Domain);
        return (domain != null) ? domain : LogDomain.DATABASE;
    }

    public static void setC4LogLevel(EnumSet<LogDomain> domains, LogLevel level) {
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
