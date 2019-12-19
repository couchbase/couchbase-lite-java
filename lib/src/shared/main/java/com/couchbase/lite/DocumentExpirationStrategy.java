//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;

import java.util.concurrent.Executor;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService.Cancellable;
import com.couchbase.lite.internal.support.Log;


/**
 * Expire documents at a regular interval, once started.
 */
class DocumentExpirationStrategy {
    private final AbstractDatabase db;
    private final Executor expirationExecutor;
    private final long expirationInterval;

    private boolean expirationCancelled;
    private Cancellable expirationTask;

    DocumentExpirationStrategy(
        @NonNull AbstractDatabase db,
        long expirationInterval,
        @NonNull Executor expirationExecutor) {
        this.db = db;
        this.expirationExecutor = expirationExecutor;
        this.expirationInterval = expirationInterval;
    }

    void schedulePurge(long minDelayMs) {
        final long nextExpiration = db.getC4Database().nextDocExpiration();
        if (nextExpiration <= 0) {
            Log.v(LogDomain.DATABASE, "No pending doc expirations");
            return;
        }

        final long delayMs = Math.max(nextExpiration - System.currentTimeMillis(), minDelayMs);
        synchronized (this) {
            if (expirationCancelled || (expirationTask != null)) { return; }
            expirationTask = CouchbaseLiteInternal.getExecutionService()
                .postDelayedOnExecutor(delayMs, expirationExecutor, this::purgeExpiredDocuments);
        }

        Log.v(LogDomain.DATABASE, "Scheduled next doc expiration for %s in %d ms", db.getName(), delayMs);
    }

    void cancelPurges() {
        final Cancellable task;
        synchronized (this) {
            expirationCancelled = true;
            task = expirationTask;
        }

        if (task == null) { return; }

        CouchbaseLiteInternal.getExecutionService().cancelDelayedTask(task);
    }

    // Aligning with database/document change notification to avoid race condition
    // between ending transaction and handling change notification when the documents
    // are purged
    private void purgeExpiredDocuments() {
        synchronized (this) {
            if (expirationCancelled) { return; }
            expirationTask = null;
        }

        if (!db.isOpen()) { return; }

        final int purged = db.getC4Database().purgeExpiredDocs();
        Log.v(LogDomain.DATABASE, "Purged %d expired documents", purged);

        schedulePurge(expirationInterval);
    }
}
