//
// C4DocumentObserver.java
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


public class C4DocumentObserver extends C4NativePeer {
    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // Long: handle of C4DatabaseObserver native address
    // C4DocumentObserver: Java class holds handle
    private static final Map<Long, C4DocumentObserver> REVERSE_LOOKUP_TABLE
        = Collections.synchronizedMap(new HashMap<>());

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    static C4DocumentObserver newObserver(long db, String docID, C4DocumentObserverListener listener, Object context) {
        final C4DocumentObserver observer = new C4DocumentObserver(db, docID, listener, context);
        REVERSE_LOOKUP_TABLE.put(observer.getPeer(), observer);
        return observer;
    }

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    /**
     * Callback invoked by a database observer.
     * <p>
     * NOTE: Two parameters, observer and context, which are defined for iOS:
     * observer -> this instance
     * context ->  maintained in java layer
     */
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private static void callback(long handle, String docID, long sequence) {
        final C4DocumentObserver obs = REVERSE_LOOKUP_TABLE.get(handle);
        if (obs == null) { return; }

        final C4DocumentObserverListener listener = obs.listener;
        if (listener == null) { return; }

        listener.callback(obs, docID, sequence, obs.context);
    }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private final C4DocumentObserverListener listener;
    private final Object context;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private C4DocumentObserver(long db, String docID, C4DocumentObserverListener listener, Object context) {
        super(create(db, docID));
        this.listener = listener;
        this.context = context;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public void free() {
        final long handle = getPeerAndClear();
        if (handle == 0L) { return; }

        REVERSE_LOOKUP_TABLE.remove(handle);

        free(handle);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long db, String docID);

    /**
     * Free C4DocumentObserver* instance
     *
     * @param c4observer (C4DocumentObserver*)
     */
    private static native void free(long c4observer);
}
