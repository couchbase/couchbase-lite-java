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

import java.util.Locale;

import com.couchbase.lite.Database;
import com.couchbase.lite.Logger;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Log;


/**
 * Couchbase Lite Internal Log Utility.
 */
public final class Log {
    public static final int C4LOG_DEBUG = C4Constants.LogLevel.DEBUG;
    public static final int C4LOG_VERBOSE = C4Constants.LogLevel.VERBOSE;
    public static final int C4LOG_INFO = C4Constants.LogLevel.INFO;
    public static final int C4LOG_WARN = C4Constants.LogLevel.WARNING;
    public static final int C4LOG_ERROR = C4Constants.LogLevel.ERROR;
    public static final int C4LOG_NONE = C4Constants.LogLevel.NONE;
    private static final String DATABASE = C4Constants.LogDomain.DATABASE;
    private static final String QUERY = C4Constants.LogDomain.QUERY;
    private static final String SYNC = C4Constants.LogDomain.SYNC;
    private static final String WEB_SOCKET = C4Constants.LogDomain.WEB_SOCKET;

    /**
     * Send a VERBOSE message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void v(com.couchbase.lite.LogDomain domain, String msg) {
        sendToLoggers(com.couchbase.lite.LogLevel.VERBOSE, domain, msg);
    }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void v(com.couchbase.lite.LogDomain domain, String msg, Throwable tr) {
        v(domain, "Exception: %s", tr.toString());
    }

    /**
     * Send a VERBOSE message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void v(com.couchbase.lite.LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.VERBOSE, domain, msg);
    }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void v(com.couchbase.lite.LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.VERBOSE, domain, msg);
    }

    /**
     * Send an INFO message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void i(com.couchbase.lite.LogDomain domain, String msg) {
        sendToLoggers(com.couchbase.lite.LogLevel.INFO, domain, msg);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void i(com.couchbase.lite.LogDomain domain, String msg, Throwable tr) {
        i(domain, "Exception: %s", tr.toString());
    }

    public static void info(com.couchbase.lite.LogDomain domain, String msg) {
        i(domain, msg);
    }

    public static void info(com.couchbase.lite.LogDomain domain, String msg, Throwable tr) {
        i(domain, msg, tr);
    }

    /**
     * Send an INFO message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void i(com.couchbase.lite.LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.INFO, domain, msg);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void i(com.couchbase.lite.LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.INFO, domain, msg);
    }

    /**
     * Send a WARN message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void w(com.couchbase.lite.LogDomain domain, String msg) {
        sendToLoggers(com.couchbase.lite.LogLevel.WARNING, domain, msg);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param tr     An exception to log
     */
    public static void w(com.couchbase.lite.LogDomain domain, Throwable tr) {
        w(domain, "Exception: %s", tr.toString());
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void w(com.couchbase.lite.LogDomain domain, String msg, Throwable tr) {
        w(domain, "%s: %s", msg, tr.toString());
    }

    /**
     * Send a WARN message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void w(com.couchbase.lite.LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.WARNING, domain, msg);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void w(com.couchbase.lite.LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.WARNING, domain, msg);
    }

    /**
     * Send an ERROR message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void e(com.couchbase.lite.LogDomain domain, String msg) {
        sendToLoggers(com.couchbase.lite.LogLevel.ERROR, domain, msg);
    }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void e(com.couchbase.lite.LogDomain domain, String msg, Throwable tr) {
        e(domain, "%s: %s", msg, tr.toString());
    }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void e(com.couchbase.lite.LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.ERROR, domain, msg);
    }

    /**
     * Send a ERROR message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void e(com.couchbase.lite.LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.ERROR, domain, msg);
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void d(com.couchbase.lite.LogDomain domain, String msg) {
        sendToLoggers(com.couchbase.lite.LogLevel.DEBUG, domain, msg);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void d(com.couchbase.lite.LogDomain domain, String msg, Throwable tr) {
        d(domain, "Exception: %s", tr.toString());
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void d(com.couchbase.lite.LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.DEBUG, domain, msg);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void d(com.couchbase.lite.LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(com.couchbase.lite.LogLevel.DEBUG, domain, msg);
    }

    public static void setLogLevel(com.couchbase.lite.LogDomain domain, com.couchbase.lite.LogLevel level) {
        final int actualLevel = level.equals(com.couchbase.lite.LogLevel.NONE) ? C4LOG_NONE : C4LOG_DEBUG;
        switch (domain) {
            case ALL:
                enableLogging(DATABASE, actualLevel);
                enableLogging(QUERY, actualLevel);
                enableLogging(SYNC, actualLevel);
                enableLogging(WEB_SOCKET, actualLevel);
                enableLogging(C4Constants.LogDomain.BLIP, actualLevel);
                enableLogging(C4Constants.LogDomain.SYNC_BUSY, actualLevel);
                break;
            case DATABASE:
                enableLogging(DATABASE, actualLevel);
                break;

            case QUERY:
                enableLogging(QUERY, actualLevel);
                break;

            case REPLICATOR:
                enableLogging(SYNC, actualLevel);
                enableLogging(C4Constants.LogDomain.SYNC_BUSY, actualLevel);
                break;

            case NETWORK:
                enableLogging(C4Constants.LogDomain.BLIP, actualLevel);
                enableLogging(WEB_SOCKET, actualLevel);
                break;
        }
    }

    public static void enableLogging(String tag, int logLevel) {
        // LiteCore logging
        C4Log.setLevel(tag, logLevel);
    }

    private static void sendToLoggers(
        com.couchbase.lite.LogLevel level,
        com.couchbase.lite.LogDomain domain, String msg) {
        boolean fileSucceeded = false;
        boolean consoleSucceeded = false;
        try {
            // File logging:
            Database.LOG.getFile().log(level, domain, msg);
            fileSucceeded = true;

            // Console logging:
            Database.LOG.getConsole().log(level, domain, msg);
            consoleSucceeded = true;

            // Custom logging:
            final Logger custom = Database.LOG.getCustom();
            if (custom != null) {
                custom.log(level, domain, msg);
            }
        }
        catch (Exception e) {
            if (fileSucceeded) {
                Database.LOG.getFile()
                    .log(com.couchbase.lite.LogLevel.ERROR, com.couchbase.lite.LogDomain.DATABASE, e.toString());
            }

            if (consoleSucceeded) {
                Database.LOG.getConsole()
                    .log(com.couchbase.lite.LogLevel.ERROR, com.couchbase.lite.LogDomain.DATABASE, e.toString());
            }
        }
    }

    /**
     * private constructor.
     */
    private Log() { }
}
