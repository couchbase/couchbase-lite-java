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

import com.couchbase.lite.CBLError;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Base;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Error;
import com.couchbase.lite.internal.support.Log;


public class CBLStatus {
    public static CouchbaseLiteException convertError(C4Error c4err) {
        return convertException(c4err.getDomain(), c4err.getCode(), c4err.getInternalInfo());
    }

    public static CouchbaseLiteException convertException(LiteCoreException e) {
        return convertException(e.domain, e.code, null, e);
    }

    public static CouchbaseLiteException convertException(LiteCoreException e, String msg) {
        return convertException(e.domain, e.code, msg, e);
    }

    public static CouchbaseLiteException convertException(int domainCode, int statusCode, int internalInfo) {
        return ((domainCode == 0) || (statusCode == 0))
            ? convertException(domainCode, statusCode, null, null)
            : convertException(new LiteCoreException(
                domainCode,
                statusCode,
                C4Base.getMessage(domainCode, statusCode, internalInfo)));
    }

    public static CouchbaseLiteException convertException(
            int domainCode,
            int statusCode,
            String msg,
            LiteCoreException e) {
        final String message = (msg != null) ? msg : ((e != null) ? e.getMessage() : null);

        int code = statusCode;

        final String domain;
        switch (domainCode) {
            case C4Constants.ErrorDomain.LITE_CORE:
                domain = CBLError.Domain.CBLITE;
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
                domain = CBLError.Domain.CBLITE;
                code += CBLError.Code.NETWORK_BASE;
                break;
            case C4Constants.ErrorDomain.WEB_SOCKET:
                domain = CBLError.Domain.CBLITE;
                code += CBLError.Code.HTTP_BASE;
                break;
            default:
                domain = CBLError.Domain.CBLITE;
                // don't mess with the code, in case it is useful...
                Log.w(
                    LogDomain.DATABASE,
                    "Unable to map C4Error(%d,%d) to an CouchbaseLiteException",
                    domainCode,
                    statusCode);
        }
        return new CouchbaseLiteException(message, e, domain, code);
    }
}
