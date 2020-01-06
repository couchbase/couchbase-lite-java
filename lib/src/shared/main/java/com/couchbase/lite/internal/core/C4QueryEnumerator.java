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
import android.support.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLArrayIterator;


/**
 * C4QueryEnumerator
 * A query result enumerator
 * Created by c4db_query. Must be freed with c4queryenum_free.
 * The fields of this struct represent the current matched index row.
 * They are valid until the next call to c4queryenum_next or c4queryenum_free.
 */
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

    public C4QueryEnumerator refresh() throws LiteCoreException {
        synchronized (lock) {
            if (handle == 0) { return null; }
            final long newHandle = refresh(handle);
            return (newHandle == 0) ? null : new C4QueryEnumerator(newHandle);
        }
    }

    /**
     * FLArrayIterator columns
     * The columns of this result, in the same order as in the query's `WHAT` clause.
     * NOTE: FLArrayIterator is member variable of C4QueryEnumerator. Not necessary to release.
     */
    public FLArrayIterator getColumns() {
        synchronized (lock) { return (handle == 0) ? null : new FLArrayIterator(getColumns(handle)); }
    }

    /**
     * Returns a bitmap in which a 1 bit represents a column whose value is MISSING.
     * This is how you tell a missing property value from a value that is JSON 'null',
     * since the value in the `columns` array will be a Fleece `null` either way.
     */
    public long getMissingColumns() {
        synchronized (lock) { return (handle == 0) ? 0L : getMissingColumns(handle); }
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

    @VisibleForTesting
    public boolean seek(long rowIndex) throws LiteCoreException {
        synchronized (lock) { return (handle != 0) && seek(handle, rowIndex); }
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
    // package protected methods
    //-------------------------------------------------------------------------

    /**
     * Reutnr the number of full-text matches (i.e. the number of items in `getFullTextMatches`)
     */
    long getFullTextMatchCount() {
        synchronized (lock) { return (handle == 0) ? 0L : getFullTextMatchCount(handle); }
    }

    /**
     * Return an array of details of each full-text match
     */
    C4FullTextMatch getFullTextMatches(int idx) {
        synchronized (lock) { return (handle == 0) ? null : new C4FullTextMatch(getFullTextMatch(handle, idx)); }
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

    private static native long getColumns(long handle);

    private static native long getMissingColumns(long handle);

    private static native long getFullTextMatchCount(long handle);

    private static native long getFullTextMatch(long handle, int idx);
}
