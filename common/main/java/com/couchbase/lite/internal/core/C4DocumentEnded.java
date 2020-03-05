//
// C4DocumentEnded.java
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

import android.support.annotation.NonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


/**
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
public class C4DocumentEnded {
    private String docID;               // Referenced from native code
    private String revID;               // Referenced from native code
    private int flags;                  // Referenced from native code
    @SuppressFBWarnings("UUF_UNUSED_FIELD")
    @SuppressWarnings("PMD.UnusedPrivateField")
    private long sequence;              // Referenced from native code
    private int errorDomain;            // Referenced from native code: C4Error.domain
    private int errorCode;              // Referenced from native code: C4Error.code
    private int errorInternalInfo;      // Referenced from native code: C4Error.internal_info
    private boolean errorIsTransient;   // Referenced from native code:

    // Called from native code
    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public C4DocumentEnded() { }

    public String getDocID() { return docID; }

    public String getRevID() { return revID; }

    public int getFlags() { return flags; }

    public int getErrorDomain() { return errorDomain; }

    public int getErrorCode() { return errorCode; }

    public int getErrorInternalInfo() { return errorInternalInfo; }

    public C4Error getC4Error() { return new C4Error(errorDomain, errorCode, errorInternalInfo); }

    public boolean errorIsTransient() { return errorIsTransient; }

    public boolean isConflicted() {
        return errorDomain == C4Constants.ErrorDomain.LITE_CORE
            && errorCode == C4Constants.LiteCoreError.CONFLICT;
    }

    @NonNull
    @Override
    public String toString() {
        return "C4DocumentEnded{id=" + docID + ",rev=" + revID + ",flags=" + flags
            + ",error=@" + errorDomain + "#" + errorCode + "(" + errorInternalInfo + "):" + errorIsTransient + "}";
    }
}
