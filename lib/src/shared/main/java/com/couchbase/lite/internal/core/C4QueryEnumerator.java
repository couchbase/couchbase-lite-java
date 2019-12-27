//
// C4QueryEnumerator.java
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
import com.couchbase.lite.internal.fleece.FLArrayIterator;


public class C4QueryEnumerator {
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private final Object lock = new Object();

    @GuardedBy("lock")
    private long handle; // hold pointer to C4QueryEnumerator

    //-------------------------------------------------------------------------
    // Constructor
    /*-------------------------------------------------------------------------*/
    C4QueryEnumerator(long handle) { this.handle = handle; }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public boolean next() throws LiteCoreException {
        synchronized (lock) { return (handle != 0) && next(handle); }
    }

    public long getRowCount() throws LiteCoreException {
        synchronized (lock) { return (handle == 0) ? 0L : getRowCount(handle); }
    }

    public boolean seek(long rowIndex) throws LiteCoreException {
        synchronized (lock) { return (handle != 0) && seek(handle, rowIndex); }
    }

    public C4QueryEnumerator refresh() throws LiteCoreException {
        synchronized (lock) {
            if (handle == 0) { return null; }
            final long newHandle = refresh(handle);
            return (newHandle == 0) ? null : new C4QueryEnumerator(newHandle);
        }
    }

    public void close() {
        synchronized (lock) {
            if (handle != 0) { close(handle); }
        }
    }

    public void free() {
        final long hdl;
        synchronized (lock) {
            hdl = handle;
            handle = 0L;
        }

        if (hdl != 0L) { free(hdl); }
    }

    // NOTE: FLArrayIterator is member variable of C4QueryEnumerator. Not necessary to release.
    public FLArrayIterator getColumns() {
        synchronized (lock) { return (handle == 0) ? null : new FLArrayIterator(getColumns(handle)); }
    }

    // -- Accessor methods to C4QueryEnumerator --
    // C4QueryEnumerator
    // A query result enumerator
    // Created by c4db_query. Must be freed with c4queryenum_free.
    // The fields of this struct represent the current matched index row, and are valid until the
    // next call to c4queryenum_next or c4queryenum_free.

    public long getMissingColumns() {
        synchronized (lock) { return (handle == 0) ? 0L : getMissingColumns(handle); }
    }

    long getFullTextMatchCount() {
        synchronized (lock) { return (handle == 0) ? 0L : getFullTextMatchCount(handle); }
    }

    C4FullTextMatch getFullTextMatches(int idx) {
        synchronized (lock) { return (handle == 0) ? null : new C4FullTextMatch(getFullTextMatch(handle, idx)); }
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native boolean next(long handle) throws LiteCoreException;

    private static native long getRowCount(long handle) throws LiteCoreException;

    private static native boolean seek(long handle, long rowIndex) throws LiteCoreException;

    private static native long refresh(long handle) throws LiteCoreException;

    private static native void close(long handle);

    private static native void free(long handle);

    // FLArrayIterator columns
    // The columns of this result, in the same order as in the query's `WHAT` clause.
    private static native long getColumns(long handle);

    // uint64_t missingColumns
    // A bitmap where a 1 bit represents a column whose value is MISSING.
    // This is how you tell a missing property value from a value that's JSON 'null',
    // since the value in the `columns` array will be a Fleece `null` either way.
    private static native long getMissingColumns(long handle);

    // -- Accessor methods to C4QueryEnumerator --

    // uint32_t fullTextMatchCount
    // The number of full-text matches (i.e. the number of items in `fullTextMatches`)
    private static native long getFullTextMatchCount(long handle);

    // const C4FullTextMatch *fullTextMatches
    // Array with details of each full-text match
    private static native long getFullTextMatch(long handle, int idx);
}
