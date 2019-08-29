//
// C4Replicator.java
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
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


/**
 * There are three bits of state to protect in the class:
 * <ol>
 * <li/> Messages sent to it from native code:  This object proxies those messages out to
 * various listeners.  Until a replicator object is remove from the REVERSE_LOOKUP_TABLE
 * forwarding such a message should always work (there is no dependence on the other two states)
 * <li/> Calls to the native object:  These should work as long as the `handle` is non-zero.
 * This object must be careful never to forward a call to a native object once it has been freed.
 * <li/> Running state: if the underlying native replicator is running there is no need to start it again.
 * Likewise, if it is stopped, there is no need to start it again.  Running state affects only
 * these two calls: it has no direct affect on either of the other two states.
 * </ol>
 * <p>
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
public class C4Replicator {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    public static final String C4_REPLICATOR_SCHEME_2 = "blip";
    public static final String C4_REPLICATOR_TLS_SCHEME_2 = "blips";

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // This lock protects both of the maps below and the corresponding vector in the JNI code
    private static final Object CLASS_LOCK = new Object();

    // Long: handle of C4Replicator native address
    // C4Replicator: Java class holds handle
    @NonNull
    @GuardedBy("CLASS_LOCK")
    private static final Map<Long, C4Replicator> REVERSE_LOOKUP_TABLE = new HashMap<>();
    @NonNull
    @GuardedBy("CLASS_LOCK")
    private static final Map<Object, C4Replicator> CONTEXT_TO_C4_REPLICATOR_MAP = new HashMap<>();

    //-------------------------------------------------------------------------
    // Public static methods
    //-------------------------------------------------------------------------

    public static boolean mayBeTransient(@NonNull C4Error err) {
        return mayBeTransient(err.getDomain(), err.getCode(), err.getInternalInfo());
    }

    public static boolean mayBeNetworkDependent(@NonNull C4Error err) {
        return mayBeNetworkDependent(err.getDomain(), err.getCode(), err.getInternalInfo());
    }

    //-------------------------------------------------------------------------
    // Native callback methods
    //-------------------------------------------------------------------------

    static void statusChangedCallback(long handle, @Nullable C4ReplicatorStatus status) {
        final C4Replicator repl = getReplicatorForHandle(handle);
        Log.d(LogDomain.REPLICATOR, "statusChangedCallback() handle: " + handle + ", status: " + status);
        if (repl == null) { return; }

        final C4ReplicatorListener listener = repl.listener;
        if (listener != null) { listener.statusChanged(repl, status, repl.replicatorContext); }
    }

    static void documentEndedCallback(long handle, boolean pushing, @Nullable C4DocumentEnded[] documentsEnded) {
        final C4Replicator repl = getReplicatorForHandle(handle);
        Log.d(LogDomain.REPLICATOR, "documentEndedCallback() handle: " + handle + ", pushing: " + pushing);
        if (repl == null) { return; }

        final C4ReplicatorListener listener = repl.listener;
        if (listener != null) { listener.documentEnded(repl, pushing, documentsEnded, repl.replicatorContext); }
    }

    static boolean validationFunction(String docID, int flags, long dict, boolean isPush, @Nullable Object context) {
        final C4Replicator repl = getReplicatorForContext(context);
        if (repl == null) { return true; }

        final C4ReplicationFilter filter = (isPush) ? repl.pushFilter : repl.pullFilter;

        return (filter == null) || filter.validationFunction(docID, flags, dict, isPush, repl.replicatorContext);
    }

    //-------------------------------------------------------------------------
    // Private static methods
    //-------------------------------------------------------------------------

    @Nullable
    private static C4Replicator getReplicatorForHandle(long handle) {
        synchronized (CLASS_LOCK) { return REVERSE_LOOKUP_TABLE.get(handle); }
    }

    @Nullable
    private static C4Replicator getReplicatorForContext(@Nullable Object context) {
        synchronized (CLASS_LOCK) { return CONTEXT_TO_C4_REPLICATOR_MAP.get(context); }
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    @NonNull
    private final Object replicatorContext;
    @Nullable
    private final Object socketFactoryContext;

    @Nullable
    private final C4ReplicatorListener listener;

    @Nullable
    private final C4ReplicationFilter pushFilter;
    @Nullable
    private final C4ReplicationFilter pullFilter;

    @NonNull
    private final AtomicBoolean running = new AtomicBoolean(false);

    @NonNull
    private final Object lock = new Object();
    @GuardedBy("lock")
    private long handle; // pointer to native C4Replicator

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    C4Replicator(
        long db,
        String schema,
        String host,
        int port,
        String path,
        String remoteDatabaseName,
        long otherLocalDB,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull Object replicatorContext,
        @Nullable Object socketFactoryContext,
        int framing)
        throws LiteCoreException {

        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.socketFactoryContext = socketFactoryContext;
        this.pushFilter = pushFilter;
        this.pullFilter = pullFilter;

        synchronized (CLASS_LOCK) {
            handle = create(
                db,
                schema,
                host,
                port,
                path,
                remoteDatabaseName,
                otherLocalDB,
                push, pull,
                socketFactoryContext,
                framing,
                replicatorContext,
                pushFilter,
                pullFilter,
                options);

            REVERSE_LOOKUP_TABLE.put(handle, this);
            CONTEXT_TO_C4_REPLICATOR_MAP.put(replicatorContext, this);
        }
    }

    C4Replicator(
        long db,
        long openSocket,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @NonNull Object replicatorContext)
        throws LiteCoreException {

        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.socketFactoryContext = null;
        this.pushFilter = null;
        this.pullFilter = null;

        synchronized (CLASS_LOCK) {
            handle = createWithSocket(db, openSocket, push, pull, replicatorContext, options);

            REVERSE_LOOKUP_TABLE.put(handle, this);
        }
    }

    public void start() {
        if (running.getAndSet(true)) { return; }
        synchronized (lock) {
            if (handle != 0) { start(handle); }
        }
    }

    public void stop() {
        if (!running.getAndSet(false)) { return; }
        synchronized (lock) {
            if (handle != 0) { stop(handle); }
        }
    }

    @Nullable
    public C4ReplicatorStatus getStatus() {
        synchronized (lock) {
            return (handle == 0) ? null : getStatus(handle);
        }
    }

    @Nullable
    public byte[] getResponseHeaders() {
        synchronized (lock) {
            return (handle == 0) ? null : getResponseHeaders(handle);
        }
    }

    // There was a bug here: JNI DETECTED ERROR IN APPLICATION: use of deleted global reference
    // https://github.com/couchbase/couchbase-lite-android/issues/1912
    public void free() {
        final long handle;
        synchronized (lock) {
            handle = this.handle;
            this.handle = 0;
        }

        synchronized (CLASS_LOCK) {
            free(handle, replicatorContext, socketFactoryContext);

            REVERSE_LOOKUP_TABLE.remove(handle);
            CONTEXT_TO_C4_REPLICATOR_MAP.remove(this.replicatorContext);
        }
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Creates a new replicator.
     */
    static native long create(
        long db,
        String schema,
        String host,
        int port,
        String path,
        String remoteDatabaseName,
        long otherLocalDB,
        int push,
        int pull,
        Object socketFactoryContext,
        int framing,
        Object replicatorContext,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        byte[] options) throws LiteCoreException;

    /**
     * Creates a new replicator from an already-open C4Socket. This is for use by listeners
     * that accept incoming connections.  Wrap them by calling `c4socket_fromNative()`, then
     * start a passive replication to service them.
     *
     * @param db                The local database.
     * @param openSocket        An already-created C4Socket.
     * @param push              boolean: push replication
     * @param pull              boolean: pull replication
     * @param replicatorContext context object
     * @param options           flags
     * @return The pointer of the newly created replicator
     */
    static native long createWithSocket(
        long db,
        long openSocket,
        int push,
        int pull,
        Object replicatorContext,
        byte[] options)
        throws LiteCoreException;

    /**
     * Frees a replicator reference. If the replicator is running it will stop.
     */
    static native void free(long replicator, Object replicatorContext, Object socketFactoryContext);

    /**
     * Tells a replicator to start.
     */
    static native void start(long replicator);

    /**
     * Tells a replicator to stop.
     */
    static native void stop(long replicator);

    /**
     * Returns the current state of a replicator.
     */
    static native C4ReplicatorStatus getStatus(long replicator);

    /**
     * Returns the HTTP response headers as a Fleece-encoded dictionary.
     */
    static native byte[] getResponseHeaders(long replicator);

    /**
     * Returns true if this is a network error that may be transient,
     * i.e. the client should retry after a delay.
     */
    static native boolean mayBeTransient(int domain, int code, int info);

    /**
     * Returns true if this error might go away when the network environment changes,
     * i.e. the client should retry after notification of a network status change.
     */
    static native boolean mayBeNetworkDependent(int domain, int code, int info);
}
