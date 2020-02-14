//
// FLDictIterator.java
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


import android.support.annotation.Nullable;

import com.couchbase.lite.internal.core.C4NativePeer;


public class FLDictIterator extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    public FLDictIterator() { super(init()); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public long getCount() { return getCount(getPeer()); }

    public void begin(FLDict dict) {
        final long handle = getPeer();
        dict.withContent(hdl -> {
            begin(hdl, handle);
            return null;
        });
    }

    @Nullable
    public String getKeyString() { return getKeyString(getPeer()); }

    @Nullable
    public FLValue getValue() { return new FLValue(getValue(getPeer())); }

    public boolean next() { return next(getPeer()); }

    public void free() {
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
     * Create FLDictIterator instance
     *
     * @return long (FLDictIterator *)
     */
    private static native long init();

    /**
     * Returns the number of items remaining to be iterated, including the current one.
     *
     * @param itr (FLDictIterator *)
     */
    private static native long getCount(long itr);

    /**
     * Initializes a FLDictIterator struct to iterate over a dictionary.
     *
     * @param dict (FLDict)
     * @param itr  (FLDictIterator *)
     */
    private static native void begin(long dict, long itr);

    /**
     * Returns the key's string value.
     *
     * @param itr (FLDictIterator *)
     * @return key string
     */
    private static native String getKeyString(long itr);

    /**
     * Returns the current value being iterated over.
     *
     * @param itr (FLDictIterator *)
     * @return long (FLValue)
     */
    private static native long getValue(long itr);

    /**
     * Advances the iterator to the next value, or returns false if at the end.
     *
     * @param itr (FLDictIterator *)
     */
    private static native boolean next(long itr);

    /**
     * Free FLDictIterator instance
     *
     * @param itr (FLDictIterator *)
     */
    private static native void free(long itr);
}
