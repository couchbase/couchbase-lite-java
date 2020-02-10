//
// ReplicatorChangeListenerToken.java
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
package com.couchbase.lite;

import java.util.concurrent.Executor;

import com.couchbase.lite.internal.CouchbaseLiteInternal;


final class ReplicatorChangeListenerToken implements ListenerToken {
    private final ReplicatorChangeListener listener;
    private final Executor executor;

    ReplicatorChangeListenerToken(Executor executor, ReplicatorChangeListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener may not be null"); }
        this.executor = executor;
        this.listener = listener;
    }

    void notify(final ReplicatorChange change) { getExecutor().execute(() -> listener.changed(change)); }

    Executor getExecutor() {
        return (executor != null) ? executor : CouchbaseLiteInternal.getExecutionService().getMainExecutor();
    }
}

final class DocumentReplicationListenerToken implements ListenerToken {
    private final DocumentReplicationListener listener;
    private final Executor executor;

    DocumentReplicationListenerToken(Executor executor, DocumentReplicationListener listener) {
        if (listener == null) { throw new IllegalArgumentException("a listener parameter is null"); }
        this.executor = executor;
        this.listener = listener;
    }

    void notify(final DocumentReplication update) { getExecutor().execute(() -> listener.replication(update)); }

    Executor getExecutor() {
        return (executor != null) ? executor : CouchbaseLiteInternal.getExecutionService().getMainExecutor();
    }
}
