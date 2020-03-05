//
// FLArrayIterator.java
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
package com.couchbase.lite.internal.fleece;

import com.couchbase.lite.internal.core.C4NativePeer;


public class FLArrayIterator extends C4NativePeer {
    private final boolean managed;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public FLArrayIterator() { this(init(), false); }

    public FLArrayIterator(long handle) { this(handle, true); }

    private FLArrayIterator(long handle, boolean managed) {
        super(handle);
        this.managed = managed;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public void begin(FLArray array) {
        final long handle = getPeer();
        array.withContent(hdl -> {
            begin(hdl, handle);
            return null;
        });
    }

    public boolean next() { return next(getPeer()); }

    public FLValue getValue() {
        final long hValue = getValue(getPeer());
        return hValue == 0L ? null : new FLValue(hValue);
    }

    public FLValue getValueAt(int index) {
        final long hValue = getValueAt(getPeer(), index);
        return hValue == 0L ? null : new FLValue(hValue);
    }

    public void free() {
        if (managed) { return; }

        final long handle = getPeerAndClear();
        if (handle == 0) { return; }

        free(handle);
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

    /**
     * Create FLArrayIterator instance
     *
     * @return long (FLArrayIterator *)
     */
    private static native long init();

    /**
     * Initializes a FLArrayIterator struct to iterate over an array.
     *
     * @param array (FLArray)
     * @param itr   (FLArrayIterator *)
     */
    private static native void begin(long array, long itr);

    /**
     * Returns the current value being iterated over.
     *
     * @param itr (FLArrayIterator *)
     * @return long (FLValue)
     */
    private static native long getValue(long itr);

    /**
     * @param itr    (FLArrayIterator *)
     * @param offset Array offset
     * @return long (FLValue)
     */
    private static native long getValueAt(long itr, int offset);

    /**
     * Advances the iterator to the next value, or returns false if at the end.
     *
     * @param itr (FLArrayIterator *)
     */
    private static native boolean next(long itr);

    /**
     * Free FLArrayIterator instance
     *
     * @param itr (FLArrayIterator *)
     */
    private static native void free(long itr);
}
