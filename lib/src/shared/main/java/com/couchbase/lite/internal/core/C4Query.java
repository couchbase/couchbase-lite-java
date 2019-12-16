//
// C4Query.java
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

import android.support.annotation.GuardedBy;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.AllocSlice;
import com.couchbase.lite.internal.fleece.FLSliceResult;


public class C4Query {
    private final Object lock = new Object();

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @GuardedBy("lock")
    private long handle; // hold pointer to C4Query


    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------
    C4Query(long db, String expression) throws LiteCoreException {
        handle = init(db, expression);
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public void free() {
        final long hdl;
        synchronized (lock) {
            hdl = handle;
            handle = 0;
        }

        internalFree(hdl);
    }

    //////// RUNNING QUERIES:

    public String explain() {
        synchronized (lock) { return (handle == 0L) ? null : explain(handle); }
    }

    //////// INDEXES:

    // - Creates a database index, to speed up subsequent queries.

    public C4QueryEnumerator run(C4QueryOptions options, AllocSlice parameters) throws LiteCoreException {
        AllocSlice params = null;
        try {
            params = parameters;
            if (params == null) { params = new FLSliceResult(); }

            synchronized (lock) {
                return (handle == 0)
                    ? null
                    : new C4QueryEnumerator(run(handle, options.isRankFullText(), params.getHandle()));
            }
        }
        finally {
            if (params != parameters) { params.free(); }
        }
    }

    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public byte[] getFullTextMatched(C4FullTextMatch match) throws LiteCoreException {
        synchronized (lock) { return (handle == 0L) ? null : getFullTextMatched(handle, match.handle); }
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        internalFree(handle);
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    int columnCount() {
        synchronized (lock) { return (handle == 0L) ? 0 : columnCount(handle); }
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void internalFree(long hdl) {
        if (hdl == 0L) { return; }
        free(hdl);
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    //////// DATABASE QUERIES:

    /**
     * Gets a fleece encoded array of indexes in the given database
     * that were created by `c4db_createIndex`
     */
    static native long getIndexes(long db) throws LiteCoreException;

    static native void deleteIndex(long db, String name) throws LiteCoreException;

    static native boolean createIndex(
        long db,
        String name,
        String expressionsJSON,
        int indexType,
        String language,
        boolean ignoreDiacritics)
        throws LiteCoreException;

    private static native long init(long db, String expression) throws LiteCoreException;

    /**
     * Free C4Query* instance
     *
     * @param handle (C4Query*)
     */
    private static native void free(long handle);

    /**
     * @param handle (C4Query*)
     * @return C4StringResult
     */
    private static native String explain(long handle);

    /**
     * Returns the number of columns (the values specified in the WHAT clause) in each row.
     *
     * @param handle (C4Query*)
     * @return the number of columns
     */
    private static native int columnCount(long handle);

    private static native long run(long handle, boolean rankFullText, /*FLSliceResult*/ long parameters)
        throws LiteCoreException;

    /**
     * Given a docID and sequence number from the enumerator, returns the text that was emitted
     * during indexing.
     */
    private static native byte[] getFullTextMatched(long handle, long fullTextMatch) throws LiteCoreException;
}
