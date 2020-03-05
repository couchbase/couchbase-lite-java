//
// FLSliceResult.java
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
import com.couchbase.lite.internal.utils.Preconditions;


/*
 * Represent a block of memory returned from the API call. The caller takes ownership, and must
 * call free() method to release the memory except the managed() method is called to indicate
 * that the memory will be managed and released by the native code.
 */
public class FLSliceResult extends C4NativePeer implements AllocSlice {
    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    private final boolean isMemoryManaged;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public FLSliceResult() { this(false); }

    public FLSliceResult(boolean managed) { this(init(), managed); }

    public FLSliceResult(byte[] bytes) { this(initWithBytes(Preconditions.assertNotNull(bytes, "raw bytes"))); }

    public FLSliceResult(long handle) { this(handle, false); }

    /*
     * Allow the FLSliceResult in managed mode. In the managed mode, the IllegalStateException will be
     * thrown when the free() method is called and the finalize() will not throw the
     * IllegalStateException as the free() method is not called. Use this method when the
     * FLSliceResult will be freed by the native code.
     */
    FLSliceResult(long handle, boolean managed) {
        super(handle);
        this.isMemoryManaged = managed;
    }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    // !!!  Exposes the peer handle
    public long getHandle() { return getPeer(); }

    public byte[] getBuf() { return getBuf(getPeer()); }

    public long getSize() { return getSize(getPeer()); }

    public void free() {
        if (isMemoryManaged) { throw new IllegalStateException("Attempt to free a managed FLSliceResult"); }

        final long hdl = getPeerAndClear();
        if (hdl == 0L) { return; }

        free(hdl);
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        if ((!isMemoryManaged) && (get() != 0L)) {
            throw new IllegalStateException("FLSliceResult was not freed: " + this);
        }
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    private static native long init();

    private static native long initWithBytes(byte[] bytes);

    private static native void free(long slice);

    private static native byte[] getBuf(long slice);

    private static native long getSize(long slice);
}
