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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.couchbase.lite.utils.Fn;

import static org.junit.Assert.assertEquals;


public abstract class BaseQueryTest extends BaseTest {
    interface QueryResult {
        void check(int n, Result result) throws Exception;
    }

    protected static class SafeTest implements Runnable {
        private final Fn.TaskThrows<CouchbaseLiteException> test;
        private AssertionError fail;
        private CouchbaseLiteException err;

        public void checkFail() throws AssertionError {
            if (fail != null) { throw fail; }
        }

        public void checkErr() throws CouchbaseLiteException {
            if (err != null) { throw err; }
        }

        SafeTest(Fn.TaskThrows<CouchbaseLiteException> test) { this.test = test; }

        @Override
        public void run() {
            try { test.run(); }
            catch (AssertionError e) { fail = e; }
            catch (CouchbaseLiteException e) { err = e; }
        }
    }

    protected String createDocNumbered(int i, int num) throws CouchbaseLiteException {
        String docID = String.format(Locale.ENGLISH, "doc%d", i);
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("number1", i);
        doc.setValue("number2", num - i);
        save(doc);
        return docID;
    }

    protected List<Map<String, Object>> loadNumberedDocs(final int num) throws Exception {
        return loadNumberedDocs(1, num);
    }

    protected List<Map<String, Object>> loadNumberedDocs(final int from, final int to) throws CouchbaseLiteException {
        final List<Map<String, Object>> numbers = new ArrayList<>();

        SafeTest test = new SafeTest(() -> {
            for (int i = from; i <= to; i++) { numbers.add(db.getDocument(createDocNumbered(i, to)).toMap()); }
        });
        db.inBatch(test);

        test.checkFail();
        test.checkErr();

        return numbers;
    }

    protected int verifyQuery(Query query, boolean runBoth, QueryResult result) throws Exception {
        int counter1 = verifyQueryWithEnumerator(query, result);
        if (runBoth) {
            int counter2 = verifyQueryWithIterable(query, result);
            assertEquals(counter1, counter2);
        }
        return counter1;
    }

    protected int verifyQuery(Query query, QueryResult result) throws Exception {
        return verifyQuery(query, true, result);
    }

    private int verifyQueryWithEnumerator(Query query, QueryResult queryResult) throws Exception {
        int n = 0;
        ResultSet rs = query.execute();
        Result result;
        while ((result = rs.next()) != null) {
            n += 1;
            queryResult.check(n, result);
        }
        return n;
    }

    private int verifyQueryWithIterable(Query query, QueryResult queryResult) throws Exception {
        int n = 0;
        ResultSet rs = query.execute();
        for (Result result : rs) {
            n += 1;
            queryResult.check(n, result);
        }
        return n;
    }
}
