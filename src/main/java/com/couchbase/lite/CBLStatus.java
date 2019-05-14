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
package com.couchbase.lite;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.couchbase.lite.internal.core.C4Base;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Error;
import com.couchbase.lite.internal.support.Log;


class CBLStatus {
    private static final List<String> ERROR_DOMAINS;

    static {
        final List<String> l = Arrays.asList(new String[C4Constants.ErrorDomain.MAX_ERROR_DOMAINS + 1]);
        // code is a Couchbase Lite Core error code (see below)
        l.set(C4Constants.ErrorDomain.LITE_CORE, CBLError.Domain.CBLITE);
        // code is an errno (errno.h)
        l.set(C4Constants.ErrorDomain.POSIX, "POSIXErrorDomain");
        // code is a SQLite error; see "sqlite3.h">"
        l.set(C4Constants.ErrorDomain.SQLITE, CBLError.Domain.SQLITE);
        // code is a Fleece error; see "FleeceException.h"
        l.set(C4Constants.ErrorDomain.FLEECE, CBLError.Domain.FLEECE);
        // code is a network error; see enum C4NetworkErrorCode, below
        l.set(C4Constants.ErrorDomain.NETWORK, CBLError.Domain.CBLITE);
        // code is a WebSocket close code (1000...1015) or HTTP error (300..599)
        l.set(C4Constants.ErrorDomain.WEB_SOCKET, CBLError.Domain.CBLITE);
        ERROR_DOMAINS = Collections.unmodifiableList(l);
    }

    static CouchbaseLiteException convertError(C4Error c4err) {
        return convertException(c4err.getDomain(), c4err.getCode(), c4err.getInternalInfo());
    }

    static CouchbaseLiteException convertException(LiteCoreException e) {
        return convertException(e.domain, e.code, null, e);
    }

    static CouchbaseLiteException convertException(int domainCode, int statusCode, int internalInfo) {
        if (domainCode != 0 && statusCode != 0) {
            return convertException(new LiteCoreException(
                domainCode,
                statusCode,
                C4Base.getMessage(domainCode, statusCode, internalInfo)));
        }
        else { return convertException(domainCode, statusCode, null, null); }
    }

    static CouchbaseLiteException convertException(
        int domainCode,
        int statusCode,
        String message,
        LiteCoreException err) {
        int code = statusCode;
        if (domainCode == C4Constants.ErrorDomain.NETWORK) { code += CBLError.Code.NETWORK_BASE; }
        else if (domainCode == C4Constants.ErrorDomain.WEB_SOCKET) { code += CBLError.Code.HTTP_BASE; }

        String domain = (domainCode >= ERROR_DOMAINS.size()) ? null : ERROR_DOMAINS.get(domainCode);
        if (domain == null) {
            Log.w(
                LogDomain.DATABASE,
                "Unable to map C4Error(%d,%d) to an CouchbaseLiteException",
                domainCode,
                statusCode);
            domain = CBLError.Domain.CBLITE;
            code = CBLError.Code.UNEXPECTED_ERROR;
        }

        message = message != null ? message : (err != null ? err.getMessage() : null);
        return new CouchbaseLiteException(message, err, domain, code);
    }
}
