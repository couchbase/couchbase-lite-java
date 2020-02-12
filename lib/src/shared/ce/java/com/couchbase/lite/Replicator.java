package com.couchbase.lite;

import android.support.annotation.NonNull;

import com.couchbase.lite.internal.core.C4Replicator;


public final class Replicator extends AbstractReplicator {
    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config The Replicator configuration object
     */
    public Replicator(@NonNull ReplicatorConfiguration config) { super(config); }

    @Override
    protected C4Replicator getC4ReplicatorLocked() throws LiteCoreException {
        final Endpoint target = config.getTarget();

        if (target instanceof URLEndpoint) { return getRemoteC4ReplicatorLocked(((URLEndpoint) target).getURL()); }

        throw new IllegalStateException("unrecognized endpoint type: " + target);
    }
}
