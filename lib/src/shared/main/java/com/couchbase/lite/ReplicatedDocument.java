//
// ReplicatedDocument.java
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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

import java.util.EnumSet;

import com.couchbase.lite.internal.core.C4Constants;


public final class ReplicatedDocument {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final String id;
    private final EnumSet<DocumentFlag> documentFlags;
    private final CouchbaseLiteException error;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Document replicated update of a replicator.
     */
    ReplicatedDocument(String id, int flags, CouchbaseLiteException error, boolean errorIsTransient) {
        this.id = id;
        this.error = error;

        documentFlags = EnumSet.noneOf(DocumentFlag.class);
        if ((flags & C4Constants.RevisionFlags.DELETED) == C4Constants.RevisionFlags.DELETED) {
            documentFlags.add(DocumentFlag.DocumentFlagsDeleted);
        }

        if ((flags & C4Constants.RevisionFlags.PURGED) == C4Constants.RevisionFlags.PURGED) {
            documentFlags.add(DocumentFlag.DocumentFlagsAccessRemoved);
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * The current document id.
     */
    @NonNull
    public String getID() { return id; }

    /**
     * The current status flag of the document. eg. deleted, access removed
     */
    @NonNull
    public EnumSet<DocumentFlag> flags() { return documentFlags; }

    /**
     * The current document replication error.
     */
    public CouchbaseLiteException getError() { return error; }

    @NonNull
    @Override
    public String toString() { return "ReplicatedDocument{" + id + ",err=" + error + "}"; }
}

