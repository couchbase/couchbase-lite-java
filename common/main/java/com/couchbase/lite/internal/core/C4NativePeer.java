//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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

import java.util.concurrent.atomic.AtomicLong;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.utils.Fn;


/**
 * This approach exposes the native handle, because anybody can call `get`.
 */
public abstract class C4NativePeer extends AtomicLong {
    private Exception cleared;

    protected C4NativePeer() {}

    protected C4NativePeer(long handle) { setPeerHandle(handle); }

    protected final void setPeer(long handle) { setPeerHandle(handle); }

    // This doesn't entirely solve the peer management problem:
    // It is still entirely possible that the peer whose handle is
    // returned by this method, will be freed while the client is still using it.
    protected final long getPeer() {
        final long handle = get();
        if (handle == 0) {
            logBadCall();
            throw new IllegalStateException("Operation on closed native peer");
        }

        return handle;
    }

    protected long getPeerAndClear() {
        if (CouchbaseLiteInternal.isDebugging()) { cleared = new Exception(); }
        return getAndSet(0L);
    }

    protected final long getPeerHandleUnchecked() { return get(); }

    protected void withPeerVoid(Fn.Consumer<Long> fn) {
        final long handle = get();
        if (handle == 0) {
            logBadCall();
            return;
        }

        fn.accept(handle);
    }

    protected void withPeerVoidThrows(Fn.ConsumerThrows<Long, LiteCoreException> fn) throws LiteCoreException {
        final long handle = get();
        if (handle == 0) {
            logBadCall();
            return;
        }

        fn.accept(handle);
    }

    protected <T> T withPeer(T def, Fn.Function<Long, T> fn) {
        final long handle = get();
        if (handle == 0) {
            logBadCall();
            return def;
        }

        return fn.apply(handle);
    }

    protected <T> T withPeerThrows(T def, Fn.FunctionThrows<Long, T, LiteCoreException> fn) throws LiteCoreException {
        final long handle = get();
        if (handle == 0) {
            logBadCall();
            return def;
        }

        return fn.apply(handle);
    }

    private void setPeerHandle(long handle) { set(Preconditions.assertNotZero(handle, "peer handle")); }

    private void logBadCall() {
        Log.w(LogDomain.DATABASE, "Operation on closed native peer", new Exception());
        Log.w(LogDomain.DATABASE, "Closed at", cleared);
    }
}
