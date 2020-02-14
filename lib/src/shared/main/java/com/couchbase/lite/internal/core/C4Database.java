//
// C4Database.java
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
import android.support.annotation.VisibleForTesting;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;


@SuppressWarnings({"PMD.GodClass", "PMD.ExcessivePublicCount", "PMD.TooManyMethods", "PMD.ExcessiveParameterList"})
public class C4Database extends C4NativePeer {
    public static void copyDb(
        String sourcePath,
        String destinationPath,
        int flags,
        String storageEngine,
        int versioning,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException {
        copy(sourcePath, destinationPath, flags, storageEngine, versioning, algorithm, encryptionKey);
    }

    public static void rawFreeDocument(long rawDoc) throws LiteCoreException { rawFree(rawDoc); }

    public static void deleteDbAtPath(String path) throws LiteCoreException { deleteAtPath(path); }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private final boolean shouldRetain; // true -> not release native object, false -> release by free()

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    public C4Database(
        String path,
        int flags,
        String storageEngine,
        int versioning,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException {
        this(open(path, flags, storageEngine, versioning, algorithm, encryptionKey), false);
    }

    public C4Database(long handle, boolean shouldRetain) {
        super(handle);
        this.shouldRetain = shouldRetain;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // - Lifecycle

    public void free() {
        if (shouldRetain) { return; }

        final long handle = getPeerAndClear();
        if (handle == 0) { return; }

        free(handle);
    }

    public void close() throws LiteCoreException { close(getPeer()); }

    public void delete() throws LiteCoreException { delete(getPeer()); }

    public void rekey(int keyType, byte[] newKey) throws LiteCoreException { rekey(getPeer(), keyType, newKey); }

    // - Accessors

    public String getPath() { return getPath(getPeer()); }

    public long getDocumentCount() { return getDocumentCount(getPeer()); }

    @VisibleForTesting
    public long getLastSequence() { return getLastSequence(getPeer()); }

    public long nextDocExpiration() { return nextDocExpiration(getPeer()); }

    public int purgeExpiredDocs() { return purgeExpiredDocs(getPeer()); }

    public void purgeDoc(String docID) throws LiteCoreException { purgeDoc(getPeer(), docID); }

    @VisibleForTesting
    public int getMaxRevTreeDepth() { return getMaxRevTreeDepth(getPeer()); }

    @VisibleForTesting
    public void setMaxRevTreeDepth(int maxRevTreeDepth) { setMaxRevTreeDepth(getPeer(), maxRevTreeDepth); }

    @VisibleForTesting
    public byte[] getPublicUUID() throws LiteCoreException { return getPublicUUID(getPeer()); }

    @VisibleForTesting
    public byte[] getPrivateUUID() throws LiteCoreException { return getPrivateUUID(getPeer()); }

    // - Compaction

    public void compact() throws LiteCoreException { compact(getPeer()); }

    // - Transactions

    public void beginTransaction() throws LiteCoreException { beginTransaction(getPeer()); }

    public void endTransaction(boolean commit) throws LiteCoreException { endTransaction(getPeer(), commit); }

    // - RawDocs Raw Documents

    @VisibleForTesting
    public C4RawDocument rawGet(String storeName, String docID) throws LiteCoreException {
        return new C4RawDocument(rawGet(getPeer(), storeName, docID));
    }

    @VisibleForTesting
    public void rawPut(String storeName, String key, String meta, byte[] body) throws LiteCoreException {
        rawPut(getPeer(), storeName, key, meta, body);
    }

    // c4Document+Fleece.h

    // - Fleece-related
    // !!! This needs to hold both the document and the database locks
    public FLEncoder getSharedFleeceEncoder() { return new FLEncoder(getSharedFleeceEncoder(getPeer()), true); }

    // NOTE: Should param be String instead of byte[]?
    @VisibleForTesting
    public FLSliceResult encodeJSON(byte[] jsonData) throws LiteCoreException {
        return new FLSliceResult(encodeJSON(getPeer(), jsonData));
    }

    public final FLSharedKeys getFLSharedKeys() { return new FLSharedKeys(getFLSharedKeys(getPeer())); }

    ////////////////////////////////
    // C4DocEnumerator
    ////////////////////////////////

    public C4DocEnumerator enumerateChanges(long since, int flags) throws LiteCoreException {
        return new C4DocEnumerator(getPeer(), since, flags);
    }

    public C4DocEnumerator enumerateAllDocs(int flags) throws LiteCoreException {
        return new C4DocEnumerator(getPeer(), flags);
    }

    ////////////////////////////////
    // C4Document
    ////////////////////////////////

    public C4Document get(String docID, boolean mustExist) throws LiteCoreException {
        return new C4Document(getPeer(), docID, mustExist);
    }

    @VisibleForTesting
    public C4Document getBySequence(long sequence) throws LiteCoreException {
        return new C4Document(getPeer(), sequence);
    }

    // - Purging and Expiration

    public void setExpiration(String docID, long timestamp) throws LiteCoreException {
        C4Document.setExpiration(getPeer(), docID, timestamp);
    }

    public long getExpiration(String docID) throws LiteCoreException {
        return C4Document.getExpiration(getPeer(), docID);
    }

    // - Creating and Updating Documents

    public C4Document put(
        byte[] body,
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return new C4Document(C4Document.put(
            getPeer(),
            body,
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID));
    }

    @VisibleForTesting
    public C4Document put(
        FLSliceResult body, // C4Slice*
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return new C4Document(C4Document.put2(
            getPeer(),
            body.getHandle(),
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID));
    }

    @VisibleForTesting
    @NonNull
    public C4Document create(String docID, byte[] body, int revisionFlags) throws LiteCoreException {
        return new C4Document(C4Document.create(getPeer(), docID, body, revisionFlags));
    }

    @NonNull
    public C4Document create(String docID, FLSliceResult body, int flags) throws LiteCoreException {
        return new C4Document(C4Document.create2(getPeer(), docID, body != null ? body.getHandle() : 0, flags));
    }

    ////////////////////////////////////////////////////////////////
    // C4DatabaseObserver/C4DocumentObserver
    ////////////////////////////////////////////////////////////////

    @NonNull
    public C4DatabaseObserver createDatabaseObserver(C4DatabaseObserverListener listener, Object context) {
        return C4DatabaseObserver.newObserver(getPeer(), listener, context);
    }

    @NonNull
    public C4DocumentObserver createDocumentObserver(
        String docID,
        C4DocumentObserverListener listener,
        Object context) {
        return C4DocumentObserver.newObserver(getPeer(), docID, listener, context);
    }

    ////////////////////////////////
    // C4BlobStore
    ////////////////////////////////

    @NonNull
    public C4BlobStore getBlobStore() throws LiteCoreException { return new C4BlobStore(getPeer()); }

    ////////////////////////////////
    // C4Query
    ////////////////////////////////

    public C4Query createQuery(String expression) throws LiteCoreException {
        return new C4Query(getPeer(), expression);
    }

    public boolean createIndex(
        String name, String expressionsJSON, int indexType, String language,
        boolean ignoreDiacritics) throws LiteCoreException {
        return C4Query.createIndex(getPeer(), name, expressionsJSON, indexType, language, ignoreDiacritics);
    }

    public void deleteIndex(String name) throws LiteCoreException { C4Query.deleteIndex(getPeer(), name); }

    public FLValue getIndexes() throws LiteCoreException { return new FLValue(C4Query.getIndexes(getPeer())); }

    ////////////////////////////////
    // C4Replicator
    ////////////////////////////////

    public C4Replicator createReplicator(
        String schema, String host, int port, String path,
        String remoteDatabaseName,
        int push, int pull,
        byte[] options,
        C4ReplicatorListener listener,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        AbstractReplicator replicatorContext,
        SocketFactory socketFactoryContext,
        int framing)
        throws LiteCoreException {
        return C4Replicator.createReplicator(
            getPeer(),
            schema,
            host,
            port,
            path,
            remoteDatabaseName,
            push, pull,
            options,
            listener,
            pushFilter,
            pullFilter,
            replicatorContext,
            socketFactoryContext,
            framing);
    }

    public C4Replicator createReplicator(
        C4Database otherLocalDB,
        int push, int pull,
        byte[] options,
        C4ReplicatorListener listener,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        AbstractReplicator replicatorContext,
        int framing)
        throws LiteCoreException {
        return C4Replicator.createReplicator(
            getPeer(),
            otherLocalDB,
            push,
            pull,
            options,
            listener,
            pushFilter,
            pullFilter,
            replicatorContext,
            framing);
    }

    public C4Replicator createReplicator(
        C4Socket openSocket,
        int push, int pull,
        byte[] options,
        C4ReplicatorListener listener,
        Object replicatorContext)
        throws LiteCoreException {
        return C4Replicator.createReplicator(
            getPeer(),
            openSocket,
            push,
            pull,
            options,
            listener,
            replicatorContext);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        if (shouldRetain) { return; }

        final long handle = getPeerAndClear();
        if (handle == 0L) { return; }

        free(handle);

        super.finalize();
    }

    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    // !!!  Exposes the peer handle
    long getHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    // - Lifecycle
    private static native long open(
        String path, int flags,
        String storageEngine, int versioning,
        int algorithm, byte[] encryptionKey)
        throws LiteCoreException;

    private static native void copy(
        String sourcePath, String destinationPath,
        int flags,
        String storageEngine,
        int versioning,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    private static native void free(long db);

    private static native void close(long db) throws LiteCoreException;

    private static native void delete(long db) throws LiteCoreException;

    private static native void deleteAtPath(String path) throws LiteCoreException;

    private static native void rekey(long db, int keyType, byte[] newKey) throws LiteCoreException;

    // - Accessors

    private static native String getPath(long db);

    private static native long getDocumentCount(long db);

    private static native long getLastSequence(long db);

    private static native long nextDocExpiration(long db);

    private static native int purgeExpiredDocs(long db);

    private static native void purgeDoc(long db, String id) throws LiteCoreException;

    private static native int getMaxRevTreeDepth(long db);

    private static native void setMaxRevTreeDepth(long db, int maxRevTreeDepth);

    private static native byte[] getPublicUUID(long db) throws LiteCoreException;

    private static native byte[] getPrivateUUID(long db) throws LiteCoreException;

    // - Compaction

    private static native void compact(long db) throws LiteCoreException;

    // - Transactions

    private static native void beginTransaction(long db) throws LiteCoreException;

    private static native void endTransaction(long db, boolean commit) throws LiteCoreException;

    // - Raw Documents (i.e. info or _local)

    private static native void rawFree(long rawDoc) throws LiteCoreException;

    private static native long rawGet(long db, String storeName, String docID) throws LiteCoreException;

    private static native void rawPut(
        long db,
        String storeName,
        String key,
        String meta,
        byte[] body)
        throws LiteCoreException;


    ////////////////////////////////
    // c4Document+Fleece.h
    ////////////////////////////////

    // - Fleece-related

    private static native long getSharedFleeceEncoder(long db);

    private static native long encodeJSON(long db, byte[] jsonData) throws LiteCoreException;

    private static native long getFLSharedKeys(long db);
}
