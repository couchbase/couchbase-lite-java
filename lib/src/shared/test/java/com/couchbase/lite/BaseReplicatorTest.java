//
// BaseReplicatorTest.java
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

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.utils.Report;

import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PULL;
import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PUSH;
import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public abstract class BaseReplicatorTest extends BaseDbTest {
    protected Replicator baseTestReplicator;

    protected Database otherDB;

    @Before
    @Override
    public void setUp() throws CouchbaseLiteException {
        super.setUp();

        otherDB = createDb();

        assertNotNull(otherDB);
        assertTrue(otherDB.isOpen());
    }

    @After
    @Override
    public void tearDown() {
        try {
            if ((otherDB != null) && !otherDB.isOpen()) { deleteDb(otherDB); }
            else { Report.log(LogLevel.INFO, "expected otherDB to be open"); }
        }
        finally { super.tearDown(); }
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Endpoint target) {
        return makeConfig(push, pull, continuous, baseTestDb, target);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Database source,
        Endpoint target) {
        return makeConfig(push, pull, continuous, source, target, null);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Database source,
        Endpoint target,
        ConflictResolver resolver) {
        ReplicatorConfiguration config = new ReplicatorConfiguration(source, target);
        config.setReplicatorType(push && pull ? PUSH_AND_PULL : (push ? PUSH : PULL));
        config.setContinuous(continuous);
        if (resolver != null) { config.setConflictResolver(resolver); }
        return config;
    }
}
