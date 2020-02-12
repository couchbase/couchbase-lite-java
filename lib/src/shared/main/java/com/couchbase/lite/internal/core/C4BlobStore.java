//
// C4BlobStore.java
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

import java.io.File;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLSliceResult;


/**
 * Blob Store API
 */
public class C4BlobStore extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Public static methods
    //-------------------------------------------------------------------------

    /**
     * Opens a BlobStore in a directory. If the flags allow creating, the directory will be
     * created if necessary.
     * NOTE: Call free() when finished using the BlobStore.
     *
     * @param dirPath The filesystem path of the directory holding the attachments.
     * @param flags   Specifies options like create, read-only
     * @return The BlobStore reference
     * @throws LiteCoreException for any error
     */
    @NonNull
    public static C4BlobStore open(@NonNull String dirPath, long flags) throws LiteCoreException {
        if (!dirPath.endsWith(File.separator)) { dirPath = dirPath + File.separator; }
        return new C4BlobStore(openStore(dirPath, flags), false);
    }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private boolean managedByDatabase;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4BlobStore(long dbHandle) throws LiteCoreException { this(getBlobStore(dbHandle), true); }

    private C4BlobStore(long handle, boolean managedByDatabase) {
        super(handle);
        this.managedByDatabase = managedByDatabase;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Deletes the BlobStore's blobs and directory, and (if successful) frees the object.
     */
    public void delete() throws LiteCoreException {
        final long handle = getPeerAndClear();
        if (handle == 0) { return; }

        // NOTE: deleteStore() native method release memory.
        deleteStore(handle);
    }

    /**
     * Closes/frees a BlobStore.
     */
    public void free() {
        if (managedByDatabase) { return; }

        final long handle = getPeerAndClear();
        if (handle == 0) { return; }

        freeStore(handle);
    }

    /**
     * Gets the content size of a blob given its key. Returns -1 if it doesn't exist.
     * WARNING: If the blob is encrypted, the return value is a conservative estimate that may
     * be up to 16 bytes larger than the actual size.
     */
    public long getSize(C4BlobKey blobKey) { return getSize(getPeer(), getBlobKeyPeer(blobKey)); }

    /**
     * Reads the entire contents of a blob into memory. Caller is responsible for freeing it.
     */
    @NonNull
    public FLSliceResult getContents(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        return new FLSliceResult(getContents(getPeer(), getBlobKeyPeer(blobKey)));
    }

    /**
     * Returns the path of the file that stores the blob, if possible. This call may fail with
     * error kC4ErrorWrongFormat if the blob is encrypted (in which case the file would be
     * unreadable by the caller) or with kC4ErrorUnsupported if for some implementation reason
     * the blob isn't stored as a standalone file.
     * Thus, the caller MUST use this function only as an optimization, and fall back to reading
     * he contents via the API if it fails.
     * Also, it goes without saying that the caller MUST not modify the file!
     */
    @Nullable
    public String getFilePath(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        return getFilePath(getPeer(), getBlobKeyPeer(blobKey));
    }

    /**
     * Stores a blob. The associated key will be written to `outKey`.
     */
    @NonNull
    public C4BlobKey create(@NonNull byte[] contents) throws LiteCoreException {
        return new C4BlobKey(create(getPeer(), contents));
    }

    /**
     * Deletes a blob from the store given its key.
     */
    public void delete(C4BlobKey blobKey) throws LiteCoreException { delete(getPeer(), getBlobKeyPeer(blobKey)); }

    /**
     * Opens a blob for reading, as a random-access byte stream.
     */
    @NonNull
    public C4BlobReadStream openReadStream(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        return new C4BlobReadStream(openReadStream(getPeer(), getBlobKeyPeer(blobKey)));
    }

    /**
     * Opens a write stream for creating a new blob. You should then call c4stream_write to
     * write the data, ending with c4stream_install to compute the blob's key and add it to
     * the store, and then c4stream_closeWriter.
     */
    @NonNull
    public C4BlobWriteStream openWriteStream() throws LiteCoreException {
        return new C4BlobWriteStream(openWriteStream(getPeer()));
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
    // private methods
    //-------------------------------------------------------------------------

    // !!! Really shouldn't be grabbing other objects by their handles...
    private long getBlobKeyPeer(@NonNull C4BlobKey blobKey) { return blobKey.getHandle(); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long getBlobStore(long db) throws LiteCoreException;

    private static native long openStore(String dirPath, long flags) throws LiteCoreException;

    private static native void deleteStore(long blobStore) throws LiteCoreException;

    private static native void freeStore(long blobStore);

    private static native long getSize(long blobStore, long blobKey);

    private static native long getContents(long blobStore, long blobKey) throws LiteCoreException;

    private static native String getFilePath(long blobStore, long blobKey) throws LiteCoreException;

    private static native long create(long blobStore, byte[] contents) throws LiteCoreException;

    private static native void delete(long blobStore, long blobKey) throws LiteCoreException;

    private static native long openReadStream(long blobStore, long blobKey) throws LiteCoreException;

    private static native long openWriteStream(long blobStore) throws LiteCoreException;
}
