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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.MaintenanceType;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;


public class C4Database {
    /* NOTE: Enum values must match the ones in DataFile::MaintenanceType */
    private static final Map<MaintenanceType, Integer> MAINTENANCE_TYPE_MAP;

    static {
        Map<MaintenanceType, Integer> m = new HashMap<>();
        m.put(MaintenanceType.REINDEX, 0);
        MAINTENANCE_TYPE_MAP = Collections.unmodifiableMap(m);
    }

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
    private long handle; // hold pointer to C4Database
    private boolean shouldRetain; // true -> not release native object, false -> release by free()

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    public C4Database(
        String path,
        int flags, String storageEngine, int versioning,
        int algorithm, byte[] encryptionKey)
        throws LiteCoreException {
        this.handle = open(path, flags, storageEngine, versioning, algorithm, encryptionKey);
    }

    public C4Database(long handle) { this.handle = handle; }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // - Lifecycle

    public void free() {
        if (shouldRetain) { return; }

        free(handle);
        handle = 0L;
    }

    public C4Database retain() {
        shouldRetain = true;
        return this;
    }

    public void close() throws LiteCoreException { close(handle); }

    public void delete() throws LiteCoreException { delete(handle); }

    public void rekey(int keyType, byte[] newKey) throws LiteCoreException { rekey(handle, keyType, newKey); }

    // - Accessors

    public String getPath() { return getPath(handle); }

    public long getDocumentCount() { return getDocumentCount(handle); }

    public long getLastSequence() { return getLastSequence(handle); }

    public long nextDocExpiration() { return nextDocExpiration(handle); }

    public int purgeExpiredDocs() { return purgeExpiredDocs(handle); }

    public void purgeDoc(String docID) throws LiteCoreException { purgeDoc(handle, docID); }

    public int getMaxRevTreeDepth() { return getMaxRevTreeDepth(handle); }

    public void setMaxRevTreeDepth(int maxRevTreeDepth) { setMaxRevTreeDepth(handle, maxRevTreeDepth); }

    public byte[] getPublicUUID() throws LiteCoreException { return getPublicUUID(handle); }

    public byte[] getPrivateUUID() throws LiteCoreException { return getPrivateUUID(handle); }

    // - Compaction

    public void compact() throws LiteCoreException { compact(handle); }

    // - Transactions

    public void beginTransaction() throws LiteCoreException { beginTransaction(handle); }

    public void endTransaction(boolean commit) throws LiteCoreException { endTransaction(handle, commit); }

    // - RawDocs Raw Documents

    public C4RawDocument rawGet(String storeName, String docID) throws LiteCoreException {
        return new C4RawDocument(rawGet(handle, storeName, docID));
    }

    public void rawPut(String storeName, String key, String meta, byte[] body) throws LiteCoreException {
        rawPut(handle, storeName, key, meta, body);
    }

    // c4Document+Fleece.h

    // - Fleece-related

    public FLEncoder getSharedFleeceEncoder() { return new FLEncoder(getSharedFleeceEncoder(handle)).managed(); }

    // NOTE: Should param be String instead of byte[]?
    public FLSliceResult encodeJSON(byte[] jsonData) throws LiteCoreException {
        return new FLSliceResult(encodeJSON(handle, jsonData));
    }

    public final FLSharedKeys getFLSharedKeys() { return new FLSharedKeys(getFLSharedKeys(handle)); }

    ////////////////////////////////
    // C4DocEnumerator
    ////////////////////////////////

    public C4DocEnumerator enumerateChanges(long since, int flags) throws LiteCoreException {
        return new C4DocEnumerator(handle, since, flags);
    }

    public C4DocEnumerator enumerateAllDocs(int flags) throws LiteCoreException {
        return new C4DocEnumerator(handle, flags);
    }

    ////////////////////////////////
    // C4Document
    ////////////////////////////////

    public C4Document get(String docID, boolean mustExist) throws LiteCoreException {
        return new C4Document(handle, docID, mustExist);
    }

    public C4Document getBySequence(long sequence) throws LiteCoreException {
        return new C4Document(handle, sequence);
    }

    // - Purging and Expiration

    public void setExpiration(String docID, long timestamp) throws LiteCoreException {
        C4Document.setExpiration(handle, docID, timestamp);
    }

    public long getExpiration(String docID) throws LiteCoreException {
        return C4Document.getExpiration(handle, docID);
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
            handle,
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
            handle,
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

    @NonNull
    public C4Document create(String docID, byte[] body, int revisionFlags) throws LiteCoreException {
        return new C4Document(C4Document.create(handle, docID, body, revisionFlags));
    }

    @NonNull
    public C4Document create(String docID, FLSliceResult body, int flags) throws LiteCoreException {
        return new C4Document(C4Document.create2(handle, docID, body != null ? body.getHandle() : 0, flags));
    }

    ////////////////////////////////////////////////////////////////
    // C4DatabaseObserver/C4DocumentObserver
    ////////////////////////////////////////////////////////////////

    @NonNull
    public C4DatabaseObserver createDatabaseObserver(C4DatabaseObserverListener listener, Object context) {
        return new C4DatabaseObserver(handle, listener, context);
    }

    @NonNull
    public C4DocumentObserver createDocumentObserver(
        String docID,
        C4DocumentObserverListener listener,
        Object context) {
        return new C4DocumentObserver(handle, docID, listener, context);
    }

    ////////////////////////////////
    // C4BlobStore
    ////////////////////////////////

    @NonNull
    public C4BlobStore getBlobStore() throws LiteCoreException { return new C4BlobStore(handle); }

    ////////////////////////////////
    // C4Query
    ////////////////////////////////

    public C4Query createQuery(String expression) throws LiteCoreException { return new C4Query(handle, expression); }

    public boolean createIndex(
        String name, String expressionsJSON, int indexType, String language,
        boolean ignoreDiacritics) throws LiteCoreException {
        return C4Query.createIndex(handle, name, expressionsJSON, indexType, language, ignoreDiacritics);
    }

    public boolean performMaintenance(MaintenanceType type) throws LiteCoreException {
        Integer iType = MAINTENANCE_TYPE_MAP.get(type);
        if (iType == null) { throw new IllegalArgumentException("Unrecognized maintenance type: " + type); }
        return maintenance(handle, iType);
    }

    public void deleteIndex(String name) throws LiteCoreException { C4Query.deleteIndex(handle, name); }

    public FLValue getIndexes() throws LiteCoreException { return new FLValue(C4Query.getIndexes(handle)); }

    ////////////////////////////////
    // C4Replicator
    ////////////////////////////////

    public C4Replicator createReplicator(
        String schema, String host, int port, String path,
        String remoteDatabaseName,
        C4Database otherLocalDB,
        int push, int pull,
        byte[] options,
        C4ReplicatorListener listener,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        AbstractReplicator replicatorContext,
        SocketFactory socketFactoryContext,
        int framing)
        throws LiteCoreException {
        return new C4Replicator(
            handle,
            schema,
            host,
            port,
            path,
            remoteDatabaseName,
            otherLocalDB != null ? otherLocalDB.getHandle() : 0,
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
        C4Socket openSocket,
        int push, int pull,
        byte[] options,
        C4ReplicatorListener listener,
        Object replicatorContext)
        throws LiteCoreException {
        return new C4Replicator(
            handle,
            openSocket.getHandle(),
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
        free();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    @VisibleForTesting
    long getHandle() { return handle; }

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

    private static native boolean maintenance(long db, int type);
}
