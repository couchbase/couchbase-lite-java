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


/**
 * Custom conflict resolution stratagies implement this interface.
 */
public interface ConflictResolver {
    /**
     * The default conflict resolution strategy.
     */
    ConflictResolver DEFAULT = new DefaultConflictResolver();

    /**
     * Callback: called when there are conflicting changes in the local
     * and remote versions of a document during replication.
     *
     * @param conflict Description of the conflicting documents.
     * @return the resolved doc.
     */
    Document resolve(Conflict conflict);
}

class DefaultConflictResolver implements ConflictResolver {
    @Override
    public Document resolve(@NonNull Conflict conflict) {
        // deletion always wins.
        final Document localDoc = conflict.getLocalDocument();
        final Document remoteDoc = conflict.getRemoteDocument();
        if ((localDoc == null) || (remoteDoc == null)) { return null; }

        // if one of the docs is newer, return it
        final long localGen = localDoc.generation();
        final long remoteGen = remoteDoc.generation();
        if (localGen > remoteGen) { return localDoc; }
        else if (localGen < remoteGen) { return remoteDoc; }

        // otherwise, choose one randomly, but deterministically.
        final String localRevId = localDoc.getRevisionID();
        return ((localRevId != null) && (localRevId.compareTo(remoteDoc.getRevisionID()) > 0)) ? localDoc : remoteDoc;
    }
}
