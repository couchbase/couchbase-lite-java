package com.couchbase.lite;

import android.support.annotation.NonNull;

import com.couchbase.lite.internal.core.C4Socket;


public final class Replicator extends AbstractReplicator {
    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config The Replicator configuration object
     */
    public Replicator(@NonNull ReplicatorConfiguration config) { super(config); }

    @Override
    protected int framing() { return C4Socket.NO_FRAMING; }

    @Override
    protected String schema() { return null; }

    @Override
    protected C4Socket createCustomSocket(long h, String s, String n, int p, String f, byte[] o) { return null; }
}
