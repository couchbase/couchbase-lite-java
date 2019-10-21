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
package com.couchbase.lite.internal;

/**
 * Exceptions within the binding code.
 * This class is not part of the API.  It should be used only for
 * exceptions that are resolved within the bindings code.
 */
public class CBLInternalException extends Exception {
    public static final int FAILED_SELECTING_CONFLICTING_REVISION = -101;


    private final int code;

    public CBLInternalException(final int code) { this(code, null); }

    public CBLInternalException(final int code, final String message) { this(code, message, null); }

    public CBLInternalException(final int code, final String message, final Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() { return code; }
}
