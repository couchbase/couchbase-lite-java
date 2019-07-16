//
// ConsoleLogger.java
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

import java.io.PrintStream;

/**
 * A class for sending log messages to standard output stream.  This is useful
 * for debugging during development, but is recommended to be disabled in production (the
 * file logger is both more durable and more efficient)
 */
public class ConsoleLogger extends AbstractConsoleLogger {
    @Override
    public void doLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        PrintStream ps = (level == LogLevel.ERROR) ? System.err : System.out;
        ps.println(level + "/CouchbaseLite/" + domain + ":" + message);
    }
}
