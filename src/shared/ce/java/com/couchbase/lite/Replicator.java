package com.couchbase.lite;

import android.support.annotation.NonNull;

import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.replicator.CBLWebSocket;


public final class Replicator extends AbstractReplicator {
    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config The Replicator configuration object
     */
    public Replicator(@NonNull ReplicatorConfiguration config) {
        super(config);
    }

    @Override
    protected Class<?> getSocketFactory() { return CBLWebSocket.class; }

    @Override
    protected int framing() { return C4Socket.NO_FRAMING; }

    @Override
    protected String schema() { return null; }
}
