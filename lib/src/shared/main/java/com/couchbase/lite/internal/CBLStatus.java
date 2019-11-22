//
// CBLStatus.java
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
package com.couchbase.lite.internal;

import android.support.annotation.NonNull;

import com.couchbase.lite.CBLError;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Base;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Error;
import com.couchbase.lite.internal.support.Log;


public final class CBLStatus {
    private CBLStatus() {}

    @NonNull
    public static CouchbaseLiteException convertError(@NonNull C4Error c4err) {
        return (c4err == null)
            ? new CouchbaseLiteException((String) null)
            : convertException(c4err.getDomain(), c4err.getCode(), c4err.getInternalInfo());
    }

    @NonNull
    public static CouchbaseLiteException convertException(@NonNull LiteCoreException e) {
        return (e == null)
            ? new CouchbaseLiteException((String) null)
            : convertException(e.domain, e.code, null, e);
    }

    @NonNull
    public static CouchbaseLiteException convertException(@NonNull LiteCoreException e, String msg) {
        return (e == null)
            ? new CouchbaseLiteException(msg)
            : convertException(e.domain, e.code, msg, e);
    }

    public static CouchbaseLiteException convertException(int domainCode, int statusCode, int internalInfo) {
        return ((domainCode == 0) || (statusCode == 0))
            ? convertException(domainCode, statusCode, null, null)
            : convertException(domainCode, statusCode, C4Base.getMessage(domainCode, statusCode, internalInfo), null);
    }

    public static CouchbaseLiteException convertException(int domainCode, int statusCode, String msg, Exception e) {
        int code = statusCode;

        String domain = CBLError.Domain.CBLITE;
        switch (domainCode) {
            case C4Constants.ErrorDomain.LITE_CORE:
                break;
            case C4Constants.ErrorDomain.POSIX:
                domain = "POSIXErrorDomain";
                break;
            case C4Constants.ErrorDomain.SQLITE:
                domain = CBLError.Domain.SQLITE;
                break;
            case C4Constants.ErrorDomain.FLEECE:
                domain = CBLError.Domain.FLEECE;
                break;
            case C4Constants.ErrorDomain.NETWORK:
                code += CBLError.Code.NETWORK_BASE;
                break;
            case C4Constants.ErrorDomain.WEB_SOCKET:
                code += CBLError.Code.HTTP_BASE;
                break;
            default:
                Log.w(
                    LogDomain.DATABASE,
                    "Unable to map C4Error(%d,%d) to an CouchbaseLiteException",
                    domainCode,
                    statusCode);
        }

        return new CouchbaseLiteException(msg, e, domain, code);
    }
}
