//
// CBLError.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License")
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

import java.util.FormatterClosedException;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@SuppressWarnings("LineLength")
public final class CBLError {
    private static final AtomicReference<Map<String, String>> ERROR_MESSAGES = new AtomicReference<>();

    private CBLError() {}

    // Error Domain
    public static final class Domain {
        private Domain() {}

        public static final String CBLITE = "CouchbaseLite";
        public static final String SQLITE = "CouchbaseLite.SQLite";
        public static final String FLEECE = "CouchbaseLite.Fleece";
    }

    // Error Code
    public static final class Code {
        private Code() {}

        public static final int ASSERTION_FAILED = 1;                    // Internal assertion failure
        public static final int UNIMPLEMENTED = 2;                       // Oops, an unimplemented API call
        public static final int UNSUPPORTED_ENCRYPTION = 3;              // Unsupported encryption algorithm
        public static final int BAD_REVISIONID = 4;                      // Invalid revision ID syntax
        public static final int CORRUPT_REVISION_DATA = 5;               // Revision contains corrupted/unreadable data
        public static final int NOT_OPEN = 6;                            // Database/KeyStore/index is not open
        public static final int NOT_FOUND = 7;                           // Document not found
        public static final int CONFLICT = 8;                            // Document update conflict
        public static final int INVALID_PARAMETER = 9;                   // Invalid function parameter or struct value
        public static final int UNEXPECTED_ERROR = 10;                   // Internal unexpected C++ exception

        public static final int CANT_OPEN_FILE = 11;                     // Database file can't be opened; may not exist
        public static final int IO_ERROR = 12;                           // File I/O error
        public static final int MEMORY_ERROR = 13;                       // Memory allocation failed (out of memory?)
        public static final int NOT_WRITEABLE = 14;                      // File is not writable
        public static final int CORRUPT_DATA = 15;                       // Data is corrupted
        public static final int BUSY = 16;                               // Database is busy/locked
        public static final int NOT_IN_TRANSACTION = 17;                 // Function cannot be called while in a transaction
        public static final int TRANSACTION_NOT_CLOSED = 18;             // Database can't be closed while a transaction is open
        public static final int UNSUPPORTED = 19;                        // Operation not supported in this database
        public static final int NOT_A_DATABSE_FILE = 20;                 // File is not a database, or encryption key is wrong

        public static final int WRONG_FORMAT = 21;                       // Database exists but not in the format/storage requested
        public static final int CRYPTO = 22;                             // Encryption/decryption error
        public static final int INVALID_QUERY = 23;                      // Invalid query
        public static final int MISSING_INDEX = 24;                      // No such index, or query requires a nonexistent index
        public static final int INVALID_QUERY_PARAM = 25;                // Unknown query param name, or param number out of range
        public static final int REMOTE_ERROR = 26;                       // Unknown error from remote server
        public static final int DATABASE_TOO_OLD = 27;                   // Database file format is older than what I can open
        public static final int DATABASE_TOO_NEW = 28;                   // Database file format is newer than what I can open
        public static final int BAD_DOC_ID = 29;                        // Invalid document ID
        public static final int CANT_UPGRADE_DATABASE = 30;              // Database can't be upgraded (might be unsupported dev version)
        // Note: These are equivalent to the C4Error codes declared in LiteCore's c4Base.h

        // Network error codes (higher level than POSIX, lower level than HTTP.)
        public static final int NETWORK_BASE = 5000;                     // --- Network status codes start here
        public static final int DNS_FAILURE = 5001;                      // DNS lookup failed
        public static final int UNKNOWN_HOST = 5002;                     // DNS server doesn't know the hostname
        public static final int TIMEOUT = 5003;                          // socket timeout during an operation
        public static final int INVALID_URL = 5004;                      // the provided url is not valid
        public static final int TOO_MANY_REDIRECTS = 5005;               // too many HTTP redirects for the HTTP client to handle

        public static final int TLS_HANDSHAKE_FAILED = 5006;             // failure during TLS handshake process
        public static final int TLS_CERT_EXPIRED = 5007;                 // the provided tls certificate has expired
        public static final int TLS_CERT_UNTRUSTED = 5008;               // Cert isn't trusted for other reason
        public static final int TLS_CLIENT_CERT_REQUIRED = 5009;         // a required client certificate was not provided
        public static final int TLS_CLIENT_CERT_REJECTED = 5010;         // client certificate was rejected by the server
        public static final int TLS_CERT_UNKNOWN_ROOT = 5011;            // Self-signed cert, or unknown anchor cert

        public static final int INVALID_REDIRECT = 5012;                 // Attempted redirect to invalid replication endpoint

        public static final int HTTP_BASE = 10000;                       // ---- HTTP status codes start here
        public static final int HTTP_AUTH_REQUIRED = 10401;              // Missing or incorrect user authentication
        public static final int HTTP_FORBIDDEN = 10403;                  // User doesn't have permission to access resource
        public static final int HTTP_NOT_FOUND = 10404;                  // Resource not found
        public static final int HTTP_CONFLICT = 10409;                   // Update conflict
        public static final int HTTP_PROXY_AUTH_REQUIRED = 10407;        // HTTP proxy requires authentication
        public static final int HTTP_ENTITY_TOO_LARGE = 10413;           // Data is too large to upload
        public static final int HTTP_IM_A_TEAPOT = 10418;                // HTCPCP/1.0 error (RFC 2324)
        public static final int HTTP_INTERNAL_SERVER_ERROR = 10500;      // Something's wrong with the server
        public static final int HTTP_NOT_IMPLEMENTED = 10501;            // Unimplemented server functionality
        public static final int HTTP_SERVICE_UNAVAILABLE = 10503;        // Service is down temporarily(?)

        public static final int WEB_SOCKET_BASE = 11000;                 // ---- WebSocket status codes start here
        public static final int WEB_SOCKET_GOING_AWAY = 11001;           // Peer has to close, e.g. because host app is quitting
        public static final int WEB_SOCKET_PROTOCOL_ERROR = 11002;       // Protocol violation: invalid framing data
        public static final int WEB_SOCKET_DATA_ERROR = 11003;           // Message payload cannot be handled
        public static final int WEB_SOCKET_ABNORMAL_CLOSE = 11006;       // TCP socket closed unexpectedly
        public static final int WEB_SOCKET_BAD_MESSAGE_FORMAT = 11007;   // Unparsable WebSocket message
        public static final int WEB_SOCKET_POLICY_ERROR = 11008;         // Message violated unspecified policy
        public static final int WEB_SOCKET_MESSAGE_TOO_BIG = 11009;      // Message is too large for peer to handle
        public static final int WEB_SOCKET_MISSING_EXTENSION = 11010;    // Peer doesn't provide a necessary extension
        public static final int WEB_SOCKET_CANT_FULFILL = 11011;         // Can't fulfill request due to "unexpected condition"
        public static final int WEB_SOCKET_CLOSE_USER_TRANSIENT = 14001; // Recoverable messaging error
        public static final int WEB_SOCKET_CLOSE_USER_PERMANENT = 14002; // Non-recoverable messaging error
    }

    static void setErrorMessages(Map<String, String> errorMessages) {
        ERROR_MESSAGES.set(errorMessages);
    }

    static String lookupErrorMessage(String error, String... args) {
        String message = ERROR_MESSAGES.get().get(error);
        if (message == null) { return error; }

        try { return String.format(message, (Object[]) args); }
        catch (IllegalFormatException | FormatterClosedException ignore) { }

        return error;
    }
}
