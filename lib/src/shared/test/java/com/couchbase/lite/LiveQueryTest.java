//
// LiveQueryTest.java
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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class LiveQueryTest extends BaseTest {
    private static final String KEY = "number";

    private volatile Query globalQuery;
    private volatile CountDownLatch globalLatch;
    private volatile ListenerToken globalToken;

    // Null query is illegal
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentException() { new LiveQuery(null); }

    // Creating a document that a query can see should cause an update
    @Test
    public void testBasicLiveQuery() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(1);

        ListenerToken token = query.addChangeListener(executor, change -> latch.countDown());

        createDocNumbered(10);

        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        finally { query.removeChangeListener(token); }
    }

    // All listeners should hear an update
    @Test
    public void testLiveQueryWith2Listeners() throws CouchbaseLiteException, InterruptedException {
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(2);

        ListenerToken token1 = query.addChangeListener(executor, change -> latch.countDown());
        ListenerToken token2 = query.addChangeListener(executor, change -> latch.countDown());

        createDocNumbered(11);

        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        finally {
            query.removeChangeListener(token1);
            query.removeChangeListener(token2);
        }
    }

    @Test
    public void testLiveQueryDelay() throws CouchbaseLiteException, InterruptedException {
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        // There should be two callbacks:
        //  - immediately on registration
        //  - after LIVE_QUERY_UPDATE_INTERVAL_MS when the change gets noticed.
        final long[] times = new long[] {1, System.currentTimeMillis(), 0, 0};
        ListenerToken token = query.addChangeListener(
            executor,
            change -> {
                int n = (int) ++times[0];
                if (n >= times.length) { return; }
                times[n] = System.currentTimeMillis();
            });

        // give it a few ms to deliver the first notification
        Thread.sleep(50);

        createDocNumbered(12);
        createDocNumbered(13);
        createDocNumbered(14);
        createDocNumbered(15);
        createDocNumbered(16);

        try {
            Thread.sleep(4 * LiveQuery.LIVE_QUERY_UPDATE_INTERVAL_MS);

            assertEquals(3, times[0]);
            assertTrue(times[2] - times[1] < 200);
            assertTrue(times[3] - times[1] > 200);
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // Changing query parameters should cause an update.
    @Test
    public void testChangeParameters() throws CouchbaseLiteException, InterruptedException {
        createDocNumbered(1);
        createDocNumbered(2);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.parameter("VALUE")))
            .orderBy(Ordering.property(KEY).ascending());

        globalLatch = new CountDownLatch(1);

        Parameters params = new Parameters();
        params.setInt("VALUE", 2);
        query.setParameters(params);

        ListenerToken token = query.addChangeListener(executor, change -> globalLatch.countDown());
        try {
            assertTrue(globalLatch.await(10, TimeUnit.SECONDS));

            globalLatch = new CountDownLatch(1);

            params = new Parameters();
            params.setInt("VALUE", 1);
            query.setParameters(params);

            assertTrue(globalLatch.await(10, TimeUnit.SECONDS));
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1606
    @Test
    public void testRemovingLiveQuery() throws CouchbaseLiteException, InterruptedException {
        int n = 1;
        newQuery(n);
        try {
            // creates doc1 -> first query match
            createDocNumbered(n++);
            assertTrue(globalLatch.await(10, TimeUnit.SECONDS));

            // create doc2 -> update query match
            createDocNumbered(n++);
            assertTrue(globalLatch.await(10, TimeUnit.SECONDS));

            // create doc3 -> update query match
            createDocNumbered(n);
            assertTrue(globalLatch.await(10, TimeUnit.SECONDS));
        }
        finally {
            globalQuery.removeChangeListener(globalToken);
        }
    }

    // create test docs
    private void createDocNumbered(int i) throws CouchbaseLiteException {
        String docID = "doc-" + i;
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue(KEY, i);
        save(doc);
    }

    private void nextQuery(int n, QueryChange change) {
        ResultSet rs = change.getResults();
        List<Result> results = rs.allResults();
        if (results.size() <= 0) { return; }

        globalQuery.removeChangeListener(globalToken);

        CountDownLatch latch = globalLatch;

        if (n < 3) { newQuery(n); }

        latch.countDown();
    }

    private void newQuery(int n) {
        globalQuery = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(n)))
            .orderBy(Ordering.property(KEY).ascending());

        globalLatch = new CountDownLatch(1);

        globalToken = globalQuery.addChangeListener(executor, ch -> nextQuery(n + 1, ch));
    }
}
