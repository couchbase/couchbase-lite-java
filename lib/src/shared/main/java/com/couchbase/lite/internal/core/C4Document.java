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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.utils.Fn;


public class C4Document extends RefCounted {
    public static boolean dictContainsBlobs(FLSliceResult dict, FLSharedKeys sk) {
        return dictContainsBlobs(dict.getHandle(), sk.getHandle());
    }

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    // - C4Document
    static native int getFlags(long doc);

    static native String getDocID(long doc);

    static native String getRevID(long doc);

    static native long getSequence(long doc);

    static native String getSelectedRevID(long doc);

    static native int getSelectedFlags(long doc);

    static native long getSelectedSequence(long doc);

    static native byte[] getSelectedBody(long doc);

    // - C4Revision

    // return pointer to FLValue
    static native long getSelectedBody2(long doc);

    static native long get(long db, String docID, boolean mustExist)
        throws LiteCoreException;

    static native long getBySequence(long db, long sequence) throws LiteCoreException;

    static native void save(long doc, int maxRevTreeDepth) throws LiteCoreException;

    static native void free(long doc);

    // - Lifecycle

    static native boolean selectCurrentRevision(long doc);

    // - Revisions

    static native void loadRevisionBody(long doc) throws LiteCoreException;

    static native boolean hasRevisionBody(long doc);

    static native boolean selectParentRevision(long doc);

    static native boolean selectNextRevision(long doc);

    static native void selectNextLeafRevision(
        long doc, boolean includeDeleted,
        boolean withBody)
        throws LiteCoreException;

    static native boolean selectFirstPossibleAncestorOf(long doc, String revID);

    static native boolean selectNextPossibleAncestorOf(long doc, String revID);

    static native boolean selectCommonAncestorRevision(
        long doc,
        String revID1, String revID2);

    static native int purgeRevision(long doc, String revID) throws LiteCoreException;

    static native void resolveConflict(
        long doc,
        String winningRevID, String losingRevID,
        byte[] mergeBody, int mergedFlags)
        throws LiteCoreException;

    static native void setExpiration(long db, String docID, long timestamp)
        throws LiteCoreException;

    // - Creating and Updating Documents

    static native long getExpiration(long db, String docID) throws LiteCoreException;

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

    //-------------------------------------------------------------------------
    // helper methods
    //-------------------------------------------------------------------------

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

    static native long create(long db, String docID, byte[] body, int flags) throws LiteCoreException;

    static native long create2(long db, String docID, long body, int flags) throws LiteCoreException;

    static native long update(long doc, byte[] body, int flags) throws LiteCoreException;

    static native long update2(long doc, long body, int flags) throws LiteCoreException;

    static native boolean dictContainsBlobs(long dict, long sk); // dict -> FLSliceResult

    //-------------------------------------------------------------------------
    // Fleece-related
    //-------------------------------------------------------------------------

    static native String bodyAsJSON(long doc, boolean canonical) throws LiteCoreException;

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    @NonNull
    private final Object lock = new Object(); // lock for thread-safety

    @GuardedBy("lock")
    private long handle; // hold pointer to C4Document

    C4Document(long db, String docID, boolean mustExist) throws LiteCoreException {
        this(get(db, docID, mustExist));
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    C4Document(long db, long sequence) throws LiteCoreException {
        this(getBySequence(db, sequence));
    }

    C4Document(long handle) {
        if (handle == 0) { throw new IllegalArgumentException("handle is 0"); }
        this.handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Override
    void free() {
        final long hdl;
        synchronized (lock) {
            hdl = handle;
            handle = 0;
        }

        if (hdl != 0L) { free(hdl); }
    }

    // - C4Document
    public int getFlags() {
        return withHandle(C4Document::getFlags, 0);
    }

    // - C4Revision

    public String getDocID() {
        return withHandle(C4Document::getDocID, null);
    }

    public String getRevID() {
        return withHandle(C4Document::getRevID, null);
    }

    public long getSequence() {
        return withHandle(C4Document::getSequence, 0L);
    }

    public String getSelectedRevID() {
        return withHandle(C4Document::getSelectedRevID, null);
    }

    public int getSelectedFlags() {
        return withHandle(C4Document::getSelectedFlags, 0);
    }

    // - Lifecycle

    public long getSelectedSequence() {
        return withHandle(C4Document::getSelectedSequence, 0L);
    }

    public byte[] getSelectedBody() {
        return withHandle(C4Document::getSelectedBody, null);
    }

    public FLDict getSelectedBody2() {
        final long value = withHandle(C4Document::getSelectedBody2, null);
        return value == 0 ? null : new FLDict(value);
    }

    public void save(int maxRevTreeDepth) throws LiteCoreException {
        withHandleVoid(h -> save(h, maxRevTreeDepth));
    }

    // - Revisions

    public boolean selectCurrentRevision() {
        return withHandle(C4Document::selectCurrentRevision, false);
    }

    public void loadRevisionBody() throws LiteCoreException {
        withHandleVoid(C4Document::loadRevisionBody);
    }

    public boolean hasRevisionBody() {
        return withHandle(C4Document::hasRevisionBody, false);
    }

    public boolean selectParentRevision() {
        return withHandle(C4Document::selectParentRevision, false);
    }

    public boolean selectNextRevision() {
        return withHandle(C4Document::selectNextRevision, false);
    }

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody)
        throws LiteCoreException {
        withHandleVoid(h -> selectNextLeafRevision(h, includeDeleted, withBody));
    }

    public boolean selectFirstPossibleAncestorOf(String revID) throws LiteCoreException {
        return withHandle(h -> selectFirstPossibleAncestorOf(h, revID), false);
    }

    public boolean selectNextPossibleAncestorOf(String revID) {
        return withHandle(h -> selectNextPossibleAncestorOf(h, revID), false);
    }

    public boolean selectCommonAncestorRevision(String revID1, String revID2) {
        return withHandle(h -> selectCommonAncestorRevision(h, revID1, revID2), false);
    }

    public int purgeRevision(String revID) throws LiteCoreException {
        return withHandleThrows(h -> purgeRevision(h, revID), 0);
    }

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
        throws LiteCoreException {
        withHandleVoid(h -> resolveConflict(h, winningRevID, losingRevID, mergeBody, mergedFlags));
    }

    // - Purging and Expiration

    public C4Document update(byte[] body, int flags) throws LiteCoreException {
        final long newDoc = withHandleThrows(h -> update(h, body, flags), 0L);
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    public C4Document update(FLSliceResult body, int flags) throws LiteCoreException {
        final long bodyHandle = (body != null) ? body.getHandle() : 0;
        final long newDoc = withHandleThrows(h -> update2(h, bodyHandle, flags), 0L);
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    // - Creating and Updating Documents

    // helper methods for Document
    public boolean deleted() {
        return isSelectedRevFlags(C4Constants.RevisionFlags.DELETED);
    }

    public boolean accessRemoved() {
        return isSelectedRevFlags(C4Constants.RevisionFlags.PURGED);
    }

    public boolean conflicted() {
        return isFlags(C4Constants.DocumentFlags.CONFLICTED);
    }

    public boolean exists() {
        return isFlags(C4Constants.DocumentFlags.EXISTS);
    }

    private boolean isFlags(int flag) {
        return (getFlags() & flag) == flag;
    }

    public boolean isSelectedRevFlags(int flag) {
        return (getSelectedFlags() & flag) == flag;
    }

    ////////////////////////////////
    // c4Document+Fleece.h
    ////////////////////////////////

    // -- Fleece-related

    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return withHandleThrows(h -> bodyAsJSON(h, canonical), null);
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
    // doc -> pointer to C4Document


    private <T> T withHandle(Fn.Function<Long, T> fn, T def) {
        synchronized (lock) {
            if (handle != 0) { return fn.apply(handle); }
            Log.w(LogDomain.DATABASE, "Function called on freed C4Document");
            return def;
        }
    }

    private <T> T withHandleThrows(Fn.FunctionThrows<Long, T, LiteCoreException> fn, T def) throws LiteCoreException {
        synchronized (lock) {
            if (handle != 0) { return fn.apply(handle); }
            Log.w(LogDomain.DATABASE, "Function called on freed C4Document");
            return def;
        }
    }


    private void withHandleVoid(Fn.ConsumerThrows<Long, LiteCoreException> fn) throws LiteCoreException {
        synchronized (lock) {
            if (handle != 0) {
                fn.accept(handle);
                return;
            }
            Log.w(LogDomain.DATABASE, "Function called on freed C4Document");
        }
    }
}
