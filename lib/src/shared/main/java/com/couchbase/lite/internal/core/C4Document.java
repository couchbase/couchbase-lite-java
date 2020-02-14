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

import android.support.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;


@SuppressWarnings({"PMD.GodClass", "PMD.TooManyMethods"})
public class C4Document extends C4NativePeer {
    public static boolean dictContainsBlobs(FLSliceResult dict, FLSharedKeys sk) {
        return dictContainsBlobs(dict.getHandle(), sk.getHandle());
    }

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    C4Document(long db, String docID, boolean mustExist) throws LiteCoreException { this(get(db, docID, mustExist)); }

    C4Document(long db, long sequence) throws LiteCoreException { this(getBySequence(db, sequence)); }

    C4Document(long handle) { super(handle); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // - C4Document

    public int getFlags() { return withPeer(0, C4Document::getFlags); }

    public String getDocID() { return withPeer(null, C4Document::getDocID); }

    public String getRevID() { return withPeer(null, C4Document::getRevID); }

    public long getSequence() { return withPeer(0L, C4Document::getSequence); }

    // - C4Revision

    public String getSelectedRevID() { return withPeer(null, C4Document::getSelectedRevID); }

    public long getSelectedSequence() { return withPeer(0L, C4Document::getSelectedSequence); }

    public FLDict getSelectedBody2() {
        final long value = withPeer(null, C4Document::getSelectedBody2);
        return value == 0 ? null : new FLDict(value);
    }

    // - Lifecycle

    public int getSelectedFlags() { return withPeer(0, C4Document::getSelectedFlags); }

    public void save(int maxRevTreeDepth) throws LiteCoreException {
        withPeerVoidThrows(h -> save(h, maxRevTreeDepth));
    }

    // - Revisions

    public boolean selectNextRevision() { return withPeer(false, C4Document::selectNextRevision); }

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody) throws LiteCoreException {
        withPeerVoidThrows(h -> selectNextLeafRevision(h, includeDeleted, withBody));
    }

    // - Purging and Expiration

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
        throws LiteCoreException {
        withPeerVoidThrows(h -> resolveConflict(h, winningRevID, losingRevID, mergeBody, mergedFlags));
    }

    // - Creating and Updating Documents

    public C4Document update(FLSliceResult body, int flags) throws LiteCoreException {
        final long bodyHandle = (body != null) ? body.getHandle() : 0;
        final long newDoc = withPeerThrows(0L, h -> update2(h, bodyHandle, flags));
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    @VisibleForTesting
    public C4Document update(byte[] body, int flags) throws LiteCoreException {
        final long newDoc = withPeerThrows(0L, h -> update(h, body, flags));
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    // - Helper methods

    // helper methods for Document
    public boolean deleted() { return isSelectedRevFlags(C4Constants.RevisionFlags.DELETED); }

    public boolean exists() { return isFlags(C4Constants.DocumentFlags.EXISTS); }

    public boolean isSelectedRevFlags(int flag) { return (getSelectedFlags() & flag) == flag; }

    // - Fleece

    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return withPeerThrows(null, h -> bodyAsJSON(h, canonical));
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
    // package protected methods
    //-------------------------------------------------------------------------

    // - Lifecycle

    byte[] getSelectedBody() { return withPeer(null, C4Document::getSelectedBody); }

    @VisibleForTesting
    void free() {
        final long handle = getPeerAndClear();
        if (handle == 0L) { return; }

        free(handle);
    }

    // - Revisions

    @VisibleForTesting
    boolean selectCurrentRevision() { return withPeer(false, C4Document::selectCurrentRevision); }

    @VisibleForTesting
    void loadRevisionBody() throws LiteCoreException { withPeerVoidThrows(C4Document::loadRevisionBody); }

    @VisibleForTesting
    boolean hasRevisionBody() { return withPeer(false, C4Document::hasRevisionBody); }

    @VisibleForTesting
    boolean selectParentRevision() { return withPeer(false, C4Document::selectParentRevision); }

    @VisibleForTesting
    boolean selectFirstPossibleAncestorOf(String revID) {
        return withPeer(false, h -> selectFirstPossibleAncestorOf(h, revID));
    }

    @VisibleForTesting
    boolean selectNextPossibleAncestorOf(String revID) {
        return withPeer(false, h -> selectNextPossibleAncestorOf(h, revID));
    }

    @VisibleForTesting
    boolean selectCommonAncestorRevision(String revID1, String revID2) {
        return withPeer(false, h -> selectCommonAncestorRevision(h, revID1, revID2));
    }

    // - Purging and Expiration

    @VisibleForTesting
    int purgeRevision(String revID) throws LiteCoreException {
        return withPeerThrows(0, h -> purgeRevision(h, revID));
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private boolean isFlags(int flag) { return (getFlags() & flag) == flag; }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean conflicted() { return isFlags(C4Constants.DocumentFlags.CONFLICTED); }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean accessRemoved() { return isSelectedRevFlags(C4Constants.RevisionFlags.PURGED); }

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
