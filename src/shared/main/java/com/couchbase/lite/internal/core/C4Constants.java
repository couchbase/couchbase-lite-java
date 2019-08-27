//
// C4Constants.java
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
package com.couchbase.lite.internal.core;

public final class C4Constants {
    private C4Constants() {}

    ////////////////////////////////////
    // c4Base.h
    ////////////////////////////////////
    public static final class LogLevel {
        private LogLevel() {}

        public static final int DEBUG = 0;
        public static final int VERBOSE = 1;
        public static final int INFO = 2;
        public static final int WARNING = 3;
        public static final int ERROR = 4;
        public static final int NONE = 5;
    }

    public static final class LogDomain {
        private LogDomain() {}

        public static final String DATABASE = "DB";
        public static final String QUERY = "Query";
        public static final String SYNC = "Sync";
        public static final String WEB_SOCKET = "WS";
        public static final String BLIP = "BLIP";
        public static final String SYNC_BUSY = "SyncBusy";
    }

    ////////////////////////////////////
    // c4Database.h
    ////////////////////////////////////

    // Boolean options for C4DatabaseConfig
    public static final class DatabaseFlags {
        private DatabaseFlags() {}

        public static final int CREATE = 1;            //< Create the file if it doesn't exist
        public static final int READ_ONLY = 2;         //< Open file read-only
        public static final int AUTO_COMPACT = 4;      //< Enable auto-compaction
        public static final int SHARED_KEYS = 0x10;    //< Enable shared-keys optimization at creation time
        public static final int NO_UPGRADE = 0x20;     //< Disable upgrading an older-version database
        public static final int NON_OBSERVABLE = 0x40; //< Disable c4DatabaseObserver
    }

    // Document versioning system (also determines database storage schema)
    public static final class DocumentVersioning {
        private DocumentVersioning() {}

        public static final int REVISION_TREES = 0;   //< CouchDB and Couchbase Mobile 1.x revision trees
        public static final int VERSION_VECTORS = 1;  //< Couchbase Mobile 2.x version vectors
    }

    // Encryption algorithms.
    public static final class EncryptionAlgorithm {
        private EncryptionAlgorithm() {}

        public static final int NONE = 0;      //< No encryption (default)
        public static final int AES256 = 1;    //< AES with 256-bit key
    }

    // Encryption key sizes (in bytes).
    public static final class EncryptionKeySize {
        private EncryptionKeySize() {}

        public static final int AES256 = 32;
    }

    ////////////////////////////////////
    // c4Document.h
    ////////////////////////////////////

    // Flags describing a document.
    // Note: Superset of DocumentFlags
    public static final class DocumentFlags {
        private DocumentFlags() {}

        public static final int DELETED = 0x01;         // The document's current revision is deleted.
        public static final int CONFLICTED = 0x02;      // The document is in conflict.
        public static final int HAS_ATTACHMENTS = 0x04; // One or more revisions have attachments.
        public static final int EXISTS = 0x1000;        // The document exists (i.e. has revisions.)
    }

    // Flags that apply to a revision.
    // Note: Same as Revision::Flags
    public static final class RevisionFlags {
        private RevisionFlags() {}

        public static final int DELETED = 0x01;         // Is this revision a deletion/tombstone?
        public static final int LEAF = 0x02;            // Is this revision a leaf (no children?)
        public static final int NEW = 0x04;             // Has this rev been inserted since decoding?
        public static final int HAS_ATTACHMENTS = 0x08; // Does this rev's body contain attachments?
        public static final int KEEP_BODY = 0x10;       // Revision's body should not be discarded when non-leaf
        public static final int IS_CONFLICT = 0x20;     // Unresolved conflicting revision; will never be current
        public static final int CLOSED = 0x40;          // Rev is the (deleted) end of a closed conflicting branch
        public static final int PURGED = 0x80;          // Revision is purged (this flag is never stored in the db)
    }

    ////////////////////////////////////
    // c4DocEnumerator.h
    ////////////////////////////////////

    // Flags for document iteration
    public static final class EnumeratorFlags {
        private EnumeratorFlags() {}

        public static final int DESCENDING = 0x01;
        public static final int INCLUDE_DELETED = 0x08;
        public static final int INCLUDE_NON_CONFLICTED = 0x10;
        public static final int INCLUDE_BODIES = 0x20;

        public static final int DEFAULT = INCLUDE_NON_CONFLICTED | INCLUDE_BODIES;
    }


    ////////////////////////////////////
    // c4Query.h
    ////////////////////////////////////

    // Types of indexes.
    public static final class IndexType {
        private IndexType() {}

        public static final int VALUE = 0;     //< Regular index of property value
        public static final int FULL_TEXT = 1; //< Full-text index
        public static final int GEO = 2;       //< Geospatial index of GeoJSON values (NOT YET IMPLEMENTED)
    }

    ////////////////////////////////////
    // c4Base.h
    ////////////////////////////////////

    // Error domains:
    public static final class ErrorDomain {
        private ErrorDomain() {}

        public static final int LITE_CORE = 1;    // Couchbase Lite Core error code (see below)
        public static final int POSIX = 2;        // errno (errno.h)
        public static final int SQLITE = 3;       // SQLite error; see "sqlite3.h"
        public static final int FLEECE = 4;       // Fleece error; see "FleeceException.h"
        public static final int NETWORK = 5;      // network error; see enum C4NetworkErrorCode, below
        public static final int WEB_SOCKET = 6;   // WebSocket close code (1000...1015) or HTTP error (300..599)
        public static final int MAX_ERROR_DOMAINS = WEB_SOCKET;
    }

    // LiteCoreDomain error codes:
    public static final class LiteCoreError {
        private LiteCoreError() {}

        public static final int ASSERTION_FAILED = 1;        // Internal assertion failure
        public static final int UNIMPLEMENTED = 2;           // Oops, an unimplemented API call
        public static final int UNSUPPORTED_ENCRYPTION = 3;  // Unsupported encryption algorithm
        public static final int BAD_REVISION_ID = 4;         // Invalid revision ID syntax
        public static final int CORRUPT_REVISION_DATA = 5;   // Revision contains corrupted/unreadable data
        public static final int NOT_OPEN = 6;                // Database/KeyStore/index is not open
        public static final int NOT_FOUND = 7;               // Document not found
        public static final int CONFLICT = 8;                // Document update conflict
        public static final int INVALID_PARAMETER = 9;       // Invalid function parameter or struct value
        public static final int UNEXPECTED_ERROR = 10;       // Internal unexpected C++ exception

        public static final int CANT_OPEN_FILE = 11;         // Database file can't be opened; may not exist
        public static final int IO_ERROR = 12;               // File I/O error
        public static final int MEMORY_ERROR = 13;           // Memory allocation failed (out of memory?)
        public static final int NOT_WRITABLE = 14;           // File is not writable
        public static final int CORRUPT_DATA = 15;           // Data is corrupted
        public static final int BUSY = 16;                   // Database is busy/locked
        public static final int NOT_IN_TRANSACTION = 17;     // Function must be called while in a transaction
        public static final int TRANSACTION_NOT_CLOSED = 18; // Database can't be closed while a transaction is open
        public static final int UNSUPPORTED = 19;            // Operation not supported in this database
        public static final int NOT_A_DATABASE_FILE = 20;    // File is not a database, or encryption key is wrong

        public static final int WRONG_FORMAT = 21;           // Database exists but not in the format/storage requested
        public static final int CRYPTO = 22;                 // Encryption/decryption error
        public static final int INVALID_QUERY = 23;          // Invalid query
        public static final int MISSING_INDEX = 24;          // No such index, or query requires a nonexistent index
        public static final int INVALID_QUERY_PARAM = 25;    // Unknown query param name, or param number out of range
        public static final int REMOTE_ERROR = 26;           // Unknown error from remote server
        public static final int DATABASE_TOO_OLD = 27;       // Database file format is older than what I can open
        public static final int DATABASE_TOO_NEW = 28;       // Database file format is newer than what I can open
        public static final int BAD_DOC_ID = 29;             // Invalid document ID
        public static final int CANT_UPGRADE_DATABASE = 30;  // Database can't be upgraded (unsupported dev version?)

        public static final int MAX_ERROR_CODES = CANT_UPGRADE_DATABASE;
    }

    /**
     * Network error codes (higher level than POSIX, lower level than HTTP.)
     */
    // (These are identical to the internal C++ NetworkError enum values in WebSocketInterface.hh.)
    public static final class NetworkError {
        private NetworkError() {}

        public static final int DNS_FAILURE = 1;                // DNS lookup failed
        public static final int UNKNOWN_HOST = 2;               // DNS server doesn't know the hostname
        public static final int TIMEOUT = 3;
        public static final int INVALID_URL = 4;
        public static final int TOO_MANY_REDIRECTS = 5;
        public static final int TLS_HANDSHAKE_FAILED = 6;
        public static final int TLS_CERT_EXPIRED = 7;
        public static final int TLS_CERT_UNTRUSTED = 8;         // Cert isn't trusted for other reason
        public static final int TLS_CLIENT_CERT_REQUIRED = 9;
        public static final int TLS_CLIENT_CERT_REJECTED = 10;  // 10
        public static final int TLS_CERT_UNKNOWN_ROOT = 11;     // Self-signed cert, or unknown anchor cert
        public static final int INVALID_REDIRECT = 12;          // Attempted redirect to invalid replication endpoint
    }
}
