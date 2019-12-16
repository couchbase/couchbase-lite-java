//
// C4Document.java
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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.utils.Fn;


@SuppressWarnings({"PMD.GodClass", "PMD.TooManyMethods"})
public class C4Document {
    public static boolean dictContainsBlobs(FLSliceResult dict, FLSharedKeys sk) {
        return dictContainsBlobs(dict.getHandle(), sk.getHandle());
    }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final Object lock = new Object(); // lock for thread-safety

    @GuardedBy("lock")
    private long handle; // hold pointer to C4Document

    private Exception freedAt;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    C4Document(long db, String docID, boolean mustExist) throws LiteCoreException { this(get(db, docID, mustExist)); }

    C4Document(long db, long sequence) throws LiteCoreException { this(getBySequence(db, sequence)); }

    C4Document(long handle) {
        Preconditions.assertNotZero(handle, "handle");
        this.handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // - C4Document

    public int getFlags() { return withHandle(C4Document::getFlags, 0); }

    public String getDocID() { return withHandle(C4Document::getDocID, null); }

    public String getRevID() { return withHandle(C4Document::getRevID, null); }

    public long getSequence() { return withHandle(C4Document::getSequence, 0L); }

    // - C4Revision

    public String getSelectedRevID() { return withHandle(C4Document::getSelectedRevID, null); }

    public long getSelectedSequence() { return withHandle(C4Document::getSelectedSequence, 0L); }

    public FLDict getSelectedBody2() {
        final long value = withHandle(C4Document::getSelectedBody2, null);
        return value == 0 ? null : new FLDict(value);
    }

    // - Lifecycle

    public int getSelectedFlags() { return withHandle(C4Document::getSelectedFlags, 0); }

    public void save(int maxRevTreeDepth) throws LiteCoreException { withHandleVoid(h -> save(h, maxRevTreeDepth)); }

    // - Revisions

    public boolean selectNextRevision() { return withHandle(C4Document::selectNextRevision, false); }

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody) throws LiteCoreException {
        withHandleVoid(h -> selectNextLeafRevision(h, includeDeleted, withBody));
    }

    // - Purging and Expiration

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
        throws LiteCoreException {
        withHandleVoid(h -> resolveConflict(h, winningRevID, losingRevID, mergeBody, mergedFlags));
    }

    // - Creating and Updating Documents

    public C4Document update(FLSliceResult body, int flags) throws LiteCoreException {
        final long bodyHandle = (body != null) ? body.getHandle() : 0;
        final long newDoc = withHandleThrows(h -> update2(h, bodyHandle, flags), 0L);
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    @VisibleForTesting
    public C4Document update(byte[] body, int flags) throws LiteCoreException {
        final long newDoc = withHandleThrows(h -> update(h, body, flags), 0L);
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    // - Helper methods

    // helper methods for Document
    public boolean deleted() { return isSelectedRevFlags(C4Constants.RevisionFlags.DELETED); }

    public boolean exists() { return isFlags(C4Constants.DocumentFlags.EXISTS); }

    public boolean isSelectedRevFlags(int flag) { return (getSelectedFlags() & flag) == flag; }

    // - Fleece

    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return withHandleThrows(h -> bodyAsJSON(h, canonical), null);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        internalFree(handle);
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    // - Lifecycle

    byte[] getSelectedBody() { return withHandle(C4Document::getSelectedBody, null); }

    @VisibleForTesting
    void free() {
        final long hdl;
        synchronized (lock) {
            hdl = handle;
            handle = 0;
        }

        internalFree(hdl);

        if (CouchbaseLiteInternal.isDebugging()) { freedAt = new Exception(); }
    }

    // - Revisions

    @VisibleForTesting
    boolean selectCurrentRevision() { return withHandle(C4Document::selectCurrentRevision, false); }

    @VisibleForTesting
    void loadRevisionBody() throws LiteCoreException { withHandleVoid(C4Document::loadRevisionBody); }

    @VisibleForTesting
    boolean hasRevisionBody() { return withHandle(C4Document::hasRevisionBody, false); }

    @VisibleForTesting
    boolean selectParentRevision() { return withHandle(C4Document::selectParentRevision, false); }

    @VisibleForTesting
    boolean selectFirstPossibleAncestorOf(String revID) {
        return withHandle(h -> selectFirstPossibleAncestorOf(h, revID), false);
    }

    @VisibleForTesting
    boolean selectNextPossibleAncestorOf(String revID) {
        return withHandle(h -> selectNextPossibleAncestorOf(h, revID), false);
    }

    @VisibleForTesting
    boolean selectCommonAncestorRevision(String revID1, String revID2) {
        return withHandle(h -> selectCommonAncestorRevision(h, revID1, revID2), false);
    }

    // - Purging and Expiration

    @VisibleForTesting
    int purgeRevision(String revID) throws LiteCoreException {
        return withHandleThrows(h -> purgeRevision(h, revID), 0);
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private boolean isFlags(int flag) { return (getFlags() & flag) == flag; }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean conflicted() { return isFlags(C4Constants.DocumentFlags.CONFLICTED); }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean accessRemoved() { return isSelectedRevFlags(C4Constants.RevisionFlags.PURGED); }

    private <T> T withHandle(Fn.Function<Long, T> fn, T def) {
        synchronized (lock) {
            if (handle != 0) { return fn.apply(handle); }
            logBadCall();
            return def;
        }
    }

    private <T> T withHandleThrows(Fn.FunctionThrows<Long, T, LiteCoreException> fn, T def) throws LiteCoreException {
        synchronized (lock) {
            if (handle != 0) { return fn.apply(handle); }
            logBadCall();
            return def;
        }
    }

    private void withHandleVoid(Fn.ConsumerThrows<Long, LiteCoreException> fn) throws LiteCoreException {
        synchronized (lock) {
            if (handle != 0) {
                fn.accept(handle);
                return;
            }
            logBadCall();
        }
    }

    private void logBadCall() {
        Log.w(LogDomain.DATABASE, "Bad method call: ", new Exception());
        if (freedAt != null) { Log.w(LogDomain.DATABASE, "... on C4Document freed at: ", freedAt); }
    }

    private void internalFree(long hdl) {
        if (hdl == 0L) { return; }
        free(hdl);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    // - Purging and Expiration

    static native void setExpiration(long db, String docID, long timestamp) throws LiteCoreException;

    static native long getExpiration(long db, String docID) throws LiteCoreException;

    // - Creating and Updating Documents

    static native long create(long db, String docID, byte[] body, int flags) throws LiteCoreException;

    static native long create2(long db, String docID, long body, int flags) throws LiteCoreException;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    static native long put(
        long db,
        byte[] body,
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    static native long put2(
        long db,
        long body, // C4Slice*
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException;

    // - C4Document

    private static native int getFlags(long doc);

    private static native String getDocID(long doc);

    private static native String getRevID(long doc);

    private static native long getSequence(long doc);

    // - C4Revision

    private static native String getSelectedRevID(long doc);

    private static native int getSelectedFlags(long doc);

    private static native long getSelectedSequence(long doc);

    private static native byte[] getSelectedBody(long doc);

    // return pointer to FLValue
    private static native long getSelectedBody2(long doc);

    // - Lifecycle

    private static native long get(long db, String docID, boolean mustExist) throws LiteCoreException;

    private static native long getBySequence(long db, long sequence) throws LiteCoreException;

    private static native void save(long doc, int maxRevTreeDepth) throws LiteCoreException;

    private static native void free(long doc);

    // - Revisions

    private static native boolean selectCurrentRevision(long doc);

    private static native void loadRevisionBody(long doc) throws LiteCoreException;

    private static native boolean hasRevisionBody(long doc);

    private static native boolean selectParentRevision(long doc);

    private static native boolean selectNextRevision(long doc);

    private static native void selectNextLeafRevision(
        long doc,
        boolean includeDeleted,
        boolean withBody)
        throws LiteCoreException;

    private static native boolean selectFirstPossibleAncestorOf(long doc, String revID);

    private static native boolean selectNextPossibleAncestorOf(long doc, String revID);

    private static native boolean selectCommonAncestorRevision(long doc, String revID1, String revID2);

    // - Purging and Expiration

    private static native int purgeRevision(long doc, String revID) throws LiteCoreException;

    private static native void resolveConflict(
        long doc,
        String winningRevID,
        String losingRevID,
        byte[] mergeBody,
        int mergedFlags)
        throws LiteCoreException;

    // - Creating and Updating Documents

    private static native long update(long doc, byte[] body, int flags) throws LiteCoreException;

    private static native long update2(long doc, long body, int flags) throws LiteCoreException;

    // - Fleece-related

    // doc -> pointer to C4Document
    private static native String bodyAsJSON(long doc, boolean canonical) throws LiteCoreException;

    private static native boolean dictContainsBlobs(long dict, long sk); // dict -> FLSliceResult
}
