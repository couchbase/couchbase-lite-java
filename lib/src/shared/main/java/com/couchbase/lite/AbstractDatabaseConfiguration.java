//
// AbstractDatabaseConfiguration.java
//
// Copyright (c) 2018 Couchbase, Inc.  All rights reserved.
//
// Licensed under the Couchbase License Agreement (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// https://info.couchbase.com/rs/302-GJY-034/images/2017-10-30_License_Agreement.pdf
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

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.Preconditions;


abstract class AbstractDatabaseConfiguration {

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private final boolean readOnly;

    private String rootDirectory;
    private String dbDirectory;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    protected AbstractDatabaseConfiguration(@Nullable AbstractDatabaseConfiguration config, boolean readOnly) {
        CouchbaseLiteInternal.requireInit("Cannot create database configuration");
        this.readOnly = readOnly;
        setRootDirectory((config == null) ? null : config.rootDirectory);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns the path to the directory that contains the database.
     * If this path has not been set explicitly (see: <code>setDirectory</code> below),
     * then it is the system default.  If it has been set explicitly, then it also contains
     * the temporary directory that Couchbase uses as a scratch pad.
     *
     * @return the database directory
     */
    @NonNull
    public String getDirectory() { return dbDirectory; }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    protected abstract DatabaseConfiguration getDatabaseConfiguration();

    protected boolean isReadOnly() { return readOnly; }

    protected AbstractDatabaseConfiguration setDirectory(@NonNull String dir) {
        Preconditions.checkArgNotNull(dir, "directory");
        if (readOnly) { throw new IllegalStateException("DatabaseConfiguration is readonly mode."); }

        setRootDirectory(dir);

        return this;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    DatabaseConfiguration readOnlyCopy() { return new DatabaseConfiguration(getDatabaseConfiguration(), true); }

    String getRootDirectory() { return rootDirectory; }

    private void setRootDirectory(@Nullable String dir) {
        dbDirectory = CouchbaseLiteInternal.makeDbPath(dir);
        rootDirectory = dir;
    }
}
