//
// QueryChangeTest.java
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

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QueryChangeTest  extends BaseQueryTest{

    @Test
    public void testQueryChangeTest() {
        QueryChange change = new QueryChange(null, null, null);
        assertNull(change.getQuery());
        assertNull(change.getResults());
        assertNull(change.getError());
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1615
    // ??? note that this test does not actually verify that the listener has been removed
    @Test
    public void testRemoveQueryChangeListenerInCallback() throws Exception {
        loadNumberedDocs(10);

        final Query query = QueryBuilder
                .select(SelectResult.expression(Meta.id))
                .from(DataSource.database(baseTestDb))
                .where(Expression.property("number1").lessThan(Expression.intValue(5)));

        final ListenerToken[] token = new ListenerToken[1];

        // Removing a listener inside the listener itself needs be done carefully.
        // The change handler might get called from the executor thread before query.addChangeListener() returns.
        final CountDownLatch latch = new CountDownLatch(1);
        QueryChangeListener listener = change -> {
            ResultSet rs = change.getResults();
            if ((rs != null) && (rs.next() != null)) { // ??? WAT?
                synchronized (this) {
                    query.removeChangeListener(token[0]);
                    token[0] = null;
                }
            }
            latch.countDown();
        };

        synchronized (this) { token[0] = query.addChangeListener(testSerialExecutor, listener); }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
}
