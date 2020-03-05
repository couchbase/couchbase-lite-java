//
// QueryChange.java
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;


/**
 * QueryChange contains the information about the query result changes reported
 * by a query object.
 */
public final class QueryChange {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final Query query;
    @Nullable
    private final ResultSet rs;
    @Nullable
    private final Throwable error;

    //---------------------------------------------
    // constructors
    //---------------------------------------------
    QueryChange(@NonNull Query query, @Nullable ResultSet rs, @Nullable Throwable error) {
        this.query = query;
        this.rs = rs;
        this.error = error;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Return the source live query object.
     */
    @NonNull
    public Query getQuery() { return query; }

    /**
     * Return the new query result.
     */
    @Nullable
    public ResultSet getResults() { return rs; }

    /**
     * Return the error occurred when running the query.
     */
    @Nullable
    public Throwable getError() { return error; }
}
