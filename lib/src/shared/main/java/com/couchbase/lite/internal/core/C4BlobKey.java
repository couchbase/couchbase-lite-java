//
// C4BlobKey.java
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;


/**
 * Blob Key
 * <p>
 * A raw SHA-1 digest used as the unique identifier of a blob.
 */
public class C4BlobKey extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /**
     * Decodes a string of the form "sha1-"+base64 into a raw key.
     */
    public C4BlobKey(@Nullable String str) throws LiteCoreException { this(fromString(str)); }

    C4BlobKey(long handle) { super(handle); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Encodes a blob key to a string of the form "sha1-"+base64.
     */
    @NonNull
    @Override
    public String toString() { return toString(getPeer()); }

    public void free() {
        final long handle = getPeerAndClear();
        if (handle == 0L) { return; }
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
    // package methods
    //-------------------------------------------------------------------------

    // !!!  Exposes the peer handle
    long getHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Decode a string of the form "sha1-"+base64 into a raw key
     */
    private static native long fromString(@Nullable String str) throws LiteCoreException;

    /**
     * Encodes a blob key to a string of the form "sha1-"+base64.
     */
    private static native String toString(long blobKey);

    /**
     * Release C4BlobKey
     */
    private static native void free(long blobKey);
}
