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

/*
 * Represent a block of memory returned from the API call. The caller takes ownership, and must
 * call free() method to release the memory except the managed() method is called to indicate
 * that the memory will be managed and released by the native code.
 */
public class FLSliceResult implements AllocSlice {
    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    private long handle; // hold pointer to FLSliceResult

    private boolean isMemoryManaged;

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    public FLSliceResult() { this.handle = init(); }

    public FLSliceResult(byte[] bytes) { this.handle = initWithBytes(bytes); }

    public FLSliceResult(long handle) {
        if (handle == 0) { throw new IllegalArgumentException("handle is 0"); }
        this.handle = handle;
    }

    public long getHandle() { return handle; }

    public byte[] getBuf() { return getBuf(handle); }

    public long getSize() { return getSize(handle); }

    /*
     * Allow the FLSliceResult in managed mode. In the managed mode, the IllegalStateException will be
     * thrown when the free() method is called and the finalize() will not throw the
     * IllegalStateException as the free() method is not called. Use this method when the
     * FLSliceResult will be freed by the native code.
     */
    public FLSliceResult managed() {
        isMemoryManaged = true;
        return this;
    }

    public void free() {
        if (isMemoryManaged) { throw new IllegalStateException("FLSliceResult was marked as memory managed."); }

        if (handle != 0L) {
            free(handle);
            handle = 0L;
        }
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        if (handle != 0L && !isMemoryManaged) {
            throw new IllegalStateException("FLSliceResult was finalized before freeing.");
        }
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    static native long init();

    static native long initWithBytes(byte[] bytes);

    static native void free(long slice);

    static native byte[] getBuf(long slice);

    static native long getSize(long slice);
}
