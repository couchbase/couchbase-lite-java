//
// C4DatabaseObserver.java
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


public class C4DatabaseObserver extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // Long: handle of C4DatabaseObserver native address
    // C4DatabaseObserver: Java class holds handle
    private static final Map<Long, C4DatabaseObserver> REVERSE_LOOKUP_TABLE
        = Collections.synchronizedMap(new HashMap<>());

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private static void callback(long handle) {
        final C4DatabaseObserver obs = REVERSE_LOOKUP_TABLE.get(handle);
        if (obs == null) { return; }

        final C4DatabaseObserverListener listener = obs.listener;
        if (listener == null) { return; }

        listener.callback(obs, obs.context);
    }

    //-------------------------------------------------------------------------
    // Static factory methods
    //-------------------------------------------------------------------------

    static C4DatabaseObserver newObserver(long db, C4DatabaseObserverListener listener, Object context) {
        final C4DatabaseObserver observer = new C4DatabaseObserver(db, listener, context);
        REVERSE_LOOKUP_TABLE.put(observer.getPeer(), observer);
        return observer;
    }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private final C4DatabaseObserverListener listener;
    private final Object context;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private C4DatabaseObserver(long db, C4DatabaseObserverListener listener, Object context) {
        super(create(db));
        this.listener = listener;
        this.context = context;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public C4DatabaseChange[] getChanges(int maxChanges) { return getChanges(getPeer(), maxChanges); }

    public void free() {
        final long handle = getPeerAndClear();
        if (handle == 0L) { return; }

        REVERSE_LOOKUP_TABLE.remove(handle);

        free(handle);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long db);

    private static native C4DatabaseChange[] getChanges(long observer, int maxChanges);

    private static native void free(long c4observer);
}


