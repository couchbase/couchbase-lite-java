//
// C4BlobWriteStream.java
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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * An open stream for writing data to a blob.
 */
public class C4BlobWriteStream extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4BlobWriteStream(long handle) { super(handle); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Writes an entire byte array to the stream.
     *
     * @param bytes array of bytes to be written in its entirety
     * @throws LiteCoreException on write failure
     */
    public void write(@NonNull byte[] bytes) throws LiteCoreException {
        Preconditions.assertNotNull(bytes, "bytes");
        write(bytes, bytes.length);
    }

    /**
     * Writes the len bytes from the passed array, to the stream.
     *
     * @param bytes array of bytes to be written in its entirety.
     * @param len   the number of bytes to write
     * @throws LiteCoreException on write failure
     */
    public void write(@NonNull byte[] bytes, int len) throws LiteCoreException {
        Preconditions.assertNotNull(bytes, "bytes");
        if (len <= 0) { return; }
        write(getPeer(), bytes, len);
    }

    /**
     * Computes the blob-key (digest) of the data written to the stream. This should only be
     * called after writing the entire data. No more data can be written after this call.
     */
    @NonNull
    public C4BlobKey computeBlobKey() throws LiteCoreException { return new C4BlobKey(computeBlobKey(getPeer())); }

    /**
     * Adds the data written to the stream as a finished blob to the store.
     * If you skip this call, the blob will not be added to the store. (You might do this if you
     * were unable to receive all of the data from the network, or if you've called
     * c4stream_computeBlobKey and found that the data does not match the expected digest/key.)
     */
    public void install() throws LiteCoreException { install(getPeer()); }

    /**
     * Closes a blob write-stream. If c4stream_install was not already called, the temporary file
     * will be deleted without adding the blob to the store.
     */
    public void close() {
        final long handle = getPeerAndClear();
        if (handle == 0L) { return; }
        close(handle);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native void write(long writeStream, byte[] bytes, int len) throws LiteCoreException;

    private static native long computeBlobKey(long writeStream) throws LiteCoreException;

    private static native void install(long writeStream) throws LiteCoreException;

    private static native void close(long writeStream);
}
