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
public class C4QueryEnumerator extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4QueryEnumerator(long handle) { super(handle); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public boolean next() throws LiteCoreException { return next(getPeer()); }

    public long getRowCount() throws LiteCoreException { return getRowCount(getPeer()); }

    public C4QueryEnumerator refresh() throws LiteCoreException {
        final long newHandle = refresh(getPeer());
        return (newHandle == 0) ? null : new C4QueryEnumerator(newHandle);
    }

    /**
     * FLArrayIterator columns
     * The columns of this result, in the same order as in the query's `WHAT` clause.
     * NOTE: FLArrayIterator is member variable of C4QueryEnumerator. Not necessary to release.
     */
    public FLArrayIterator getColumns() { return new FLArrayIterator(getColumns(getPeer())); }

    /**
     * Returns a bitmap in which a 1 bit represents a column whose value is MISSING.
     * This is how you tell a missing property value from a value that is JSON 'null',
     * since the value in the `columns` array will be a Fleece `null` either way.
     */
    public long getMissingColumns() { return getMissingColumns(getPeer()); }

    public void close() { withPeerVoid(C4QueryEnumerator::close); }

    public void free() {
        final long handle = getPeerAndClear();
        if (handle == 0L) { return; }
        close();
        free(handle);
    }

    @VisibleForTesting
    public boolean seek(long rowIndex) throws LiteCoreException { return seek(getPeer(), rowIndex); }

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
     * Return the number of full-text matches (i.e. the number of items in `getFullTextMatches`)
     */
    long getFullTextMatchCount() { return getFullTextMatchCount(getPeer()); }

    /**
     * Return an array of details of each full-text match
     */
    C4FullTextMatch getFullTextMatches(int idx) { return new C4FullTextMatch(getFullTextMatch(getPeer(), idx)); }

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
