//
// C4ObserverTest.java
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
package com.couchbase.lite.internal.core;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class C4ObserverTest extends C4BaseTest {
    private C4DatabaseObserver dbObserver;
    private C4DocumentObserver docObserver;
    private AtomicInteger dbCallbackCalls;

    @Before
    @Override
    public void setUp() throws CouchbaseLiteException {
        super.setUp();

        dbCallbackCalls = new AtomicInteger(0);
    }

    @After
    @Override
    public void tearDown() {
        try {
            if (dbObserver != null) { dbObserver.free(); }
            if (docObserver != null) { docObserver.free(); }
        }
        finally { super.tearDown(); }
    }

    // - DB Observer
    @Test
    public void testDBObserver() throws LiteCoreException {
        dbObserver = this.c4Database.createDatabaseObserver((observer, context) -> {
            assertEquals(C4ObserverTest.this, context);
            dbCallbackCalls.incrementAndGet();
        }, this);
        assertEquals(0, dbCallbackCalls.get());

        createRev("A", "1-aa", fleeceBody);
        assertEquals(1, dbCallbackCalls.get());
        createRev("B", "1-bb", fleeceBody);
        assertEquals(1, dbCallbackCalls.get());

        checkChanges(Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

        createRev("B", "2-bbbb", fleeceBody);
        assertEquals(2, dbCallbackCalls.get());
        createRev("C", "1-cc", fleeceBody);
        assertEquals(2, dbCallbackCalls.get());

        checkChanges(Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);

        dbObserver.free();
        dbObserver = null;

        createRev("A", "2-aaaa", fleeceBody);
        assertEquals(2, dbCallbackCalls.get());
    }

    // - Doc Observer
    @Test
    public void testDocObserver() throws LiteCoreException {
        createRev("A", "1-aa", fleeceBody);

        docObserver = this.c4Database.createDocumentObserver("A", (observer, docID, sequence, context) -> {
            assertEquals(C4ObserverTest.this, context);
            assertEquals("A", docID);
            assertTrue(sequence > 0);
            dbCallbackCalls.incrementAndGet();
        }, this);
        assertEquals(0, dbCallbackCalls.get());

        createRev("A", "2-bb", fleeceBody);
        createRev("B", "1-bb", fleeceBody);
        assertEquals(1, dbCallbackCalls.get());
    }

    // - Multi-DBs Observer
    @Test
    public void testMultiDBsObserver() throws LiteCoreException {
        dbObserver = this.c4Database.createDatabaseObserver(
            (observer, context) -> {
                assertEquals(C4ObserverTest.this, context);
                dbCallbackCalls.incrementAndGet();
            },
            this);
        assertEquals(0, dbCallbackCalls.get());

        createRev("A", "1-aa", fleeceBody);
        assertEquals(1, dbCallbackCalls.get());
        createRev("B", "1-bb", fleeceBody);
        assertEquals(1, dbCallbackCalls.get());

        checkChanges(Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

        C4Database otherdb = new C4Database(
            dbDir.getPath(),
            getFlags(),
            null,
            getVersioning(),
            encryptionAlgorithm(),
            encryptionKey());
        assertNotNull(otherdb);
        {
            boolean commit = false;
            otherdb.beginTransaction();
            try {
                createRev(otherdb, "c", "1-cc", fleeceBody);
                createRev(otherdb, "d", "1-dd", fleeceBody);
                createRev(otherdb, "e", "1-ee", fleeceBody);
                commit = true;
            }
            finally {
                otherdb.endTransaction(commit);
            }
        }

        assertEquals(2, dbCallbackCalls.get());
        checkChanges(Arrays.asList("c", "d", "e"), Arrays.asList("1-cc", "1-dd", "1-ee"), true);

        dbObserver.free();
        dbObserver = null;

        createRev("A", "2-aaaa", fleeceBody);
        assertEquals(2, dbCallbackCalls.get());

        otherdb.close();
        otherdb.free();
    }

    // - Multi-DBObservers
    @Test
    public void testMultiDBObservers() throws LiteCoreException {
        dbObserver = this.c4Database.createDatabaseObserver((observer, context) -> {
            assertEquals(C4ObserverTest.this, context);
            dbCallbackCalls.incrementAndGet();
        }, this);
        assertEquals(0, dbCallbackCalls.get());

        final AtomicInteger dbCallbackCalls1 = new AtomicInteger(0);
        C4DatabaseObserver dbObserver1 = this.c4Database.createDatabaseObserver((observer, context) -> {
            assertEquals(C4ObserverTest.this, context);
            dbCallbackCalls1.incrementAndGet();
        }, this);
        assertEquals(0, dbCallbackCalls1.get());


        createRev("A", "1-aa", fleeceBody);
        assertEquals(1, dbCallbackCalls.get());
        assertEquals(1, dbCallbackCalls1.get());
        createRev("B", "1-bb", fleeceBody);
        assertEquals(1, dbCallbackCalls.get());
        assertEquals(1, dbCallbackCalls1.get());

        checkChanges(dbObserver, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);
        checkChanges(dbObserver1, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

        createRev("B", "2-bbbb", fleeceBody);
        assertEquals(2, dbCallbackCalls.get());
        assertEquals(2, dbCallbackCalls1.get());
        createRev("C", "1-cc", fleeceBody);
        assertEquals(2, dbCallbackCalls.get());
        assertEquals(2, dbCallbackCalls1.get());

        checkChanges(dbObserver, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);
        checkChanges(dbObserver1, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);


        dbObserver.free();
        dbObserver = null;

        dbObserver1.free();

        createRev("A", "2-aaaa", fleeceBody);
        assertEquals(2, dbCallbackCalls.get());
        assertEquals(2, dbCallbackCalls1.get());
    }

    private void checkChanges(
        List<String> expectedDocIDs,
        List<String> expectedRevIDs,
        boolean expectedExternal) {
        checkChanges(dbObserver, expectedDocIDs, expectedRevIDs, expectedExternal);
    }

    private void checkChanges(
        C4DatabaseObserver observer,
        List<String> expectedDocIDs,
        List<String> expectedRevIDs,
        boolean expectedExternal) {
        C4DatabaseChange[] changes = observer.getChanges(100);
        assertNotNull(changes);
        assertEquals(expectedDocIDs.size(), changes.length);
        for (int i = 0; i < changes.length; i++) {
            assertEquals(expectedDocIDs.get(i), changes[i].getDocID());
            assertEquals(expectedRevIDs.get(i), changes[i].getRevID());
            assertEquals(expectedExternal, changes[i].isExternal());
        }
    }
}
