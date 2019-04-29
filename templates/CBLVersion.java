//
// CBLVersion.java
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
package com.couchbase.lite.internal.core;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;


// THIS FILE IS AUTOMATICALLY CREATED FROM templates/CBLVersion.java
// Edit the version in that directory!
// Changes made to this file in the source directory will be lost.
// See the "copyVersion" task in the build file.
public class CBLVersion {
    private static final String USER_AGENT
        = "CouchbaseLite/@VERSION@ (%s) %s";
    private static final String VERSION_INFO
        = "CouchbaseLite Android v@VERSION@ (%s at @TIME@ on @HOST@) on %s";
    private static final String LIB_INFO
        = "@VARIANT@/@TYPE@ Build/@BUILD@ Commit/@COMMIT@ Core/%s";
    private static final String SYS_INFO
        = "Java; %s";

    private static final AtomicReference<String> userAgent = new AtomicReference<>();
    private static final AtomicReference<String> versionInfo = new AtomicReference<>();
    private static final AtomicReference<String> libInfo = new AtomicReference<>();
    private static final AtomicReference<String> sysInfo = new AtomicReference<>();

    public static String getUserAgent() {
        String agent = userAgent.get();

        if (agent == null) {
            agent = String.format(Locale.ENGLISH, USER_AGENT, getSysInfo(), getLibInfo());

            userAgent.compareAndSet(null, agent);
        }

        return agent;
    }

    // This is the full library build and environment information.
    public static String getVersionInfo() {
        String info = versionInfo.get();
        if (info == null) {
            info = String.format(Locale.ENGLISH, VERSION_INFO, getLibInfo(), getSysInfo());

            versionInfo.compareAndSet(null, info);
        }

        return info;
    }

    // This is the short library build information.
    public static String getLibInfo() {
        String info = libInfo.get();
        if (info == null) {
            info = String.format(Locale.ENGLISH, LIB_INFO, C4.getVersion());

            libInfo.compareAndSet(null, info);
        }

        return info;
    }

    // This is information about the system on which we are running.
    public static String getSysInfo() {
        String info = sysInfo.get();

        if (info == null) {
            info = String.format(Locale.ENGLISH, SYS_INFO, System.getProperty("os.name", "unknown"));

            sysInfo.compareAndSet(null, info);
        }

        return info;
    }
}
