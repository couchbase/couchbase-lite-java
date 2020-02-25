//
// NotificationTest.java
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

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NotificationTest extends BaseDbTest {
    @Test
    public void testDatabaseChange()
            throws InterruptedException, CouchbaseLiteException {
        final CountDownLatch latch = new CountDownLatch(1);
        baseTestDb.addChangeListener(testSerialExecutor, new DatabaseChangeListener() {
            @Override
            public void changed(@NotNull DatabaseChange change) {
                assertNotNull(change);
                assertNotNull(change.getDocumentIDs());
                assertEquals(10, change.getDocumentIDs().size());
                assertEquals(baseTestDb, change.getDatabase());
                latch.countDown();
            }
        });
        baseTestDb.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                    doc.setValue("type", "demo");
                    try {
                        saveDocInBaseTestDb(doc);
                    } catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testDocumentChange()
            throws InterruptedException, CouchbaseLiteException {
        MutableDocument mDocA = new MutableDocument("A");
        MutableDocument mDocB = new MutableDocument("B");

        // save doc
        final CountDownLatch latch1 = new CountDownLatch(1);
        DocumentChangeListener listener1 = new DocumentChangeListener() {
            @Override
            public void changed(@NotNull DocumentChange change) {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch1.getCount());
                latch1.countDown();
            }
        };
        ListenerToken token = baseTestDb.addDocumentChangeListener("A", listener1);
        mDocB.setValue("thewronganswer", 18);
        Document docB = saveDocInBaseTestDb(mDocB);
        mDocA.setValue("theanswer", 18);
        Document docA = saveDocInBaseTestDb(mDocA);
        assertTrue(latch1.await(10, TimeUnit.SECONDS));
        baseTestDb.removeChangeListener(token);

        // update doc
        final CountDownLatch latch2 = new CountDownLatch(1);
        DocumentChangeListener listener2 = new DocumentChangeListener() {
            @Override
            public void changed(@NotNull DocumentChange change) {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch2.getCount());
                latch2.countDown();
            }
        };
        token = baseTestDb.addDocumentChangeListener("A", listener2);
        mDocA = docA.toMutable();
        mDocA.setValue("thewronganswer", 18);
        docA = saveDocInBaseTestDb(mDocA);
        assertTrue(latch2.await(5, TimeUnit.SECONDS));
        baseTestDb.removeChangeListener(token);

        // delete doc
        final CountDownLatch latch3 = new CountDownLatch(1);
        DocumentChangeListener listener3 = new DocumentChangeListener() {
            @Override
            public void changed(@NotNull DocumentChange change) {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch3.getCount());
                latch3.countDown();
            }
        };
        token = baseTestDb.addDocumentChangeListener("A", listener3);
        baseTestDb.delete(docA);
        assertTrue(latch3.await(5, TimeUnit.SECONDS));
        baseTestDb.removeChangeListener(token);

    }

    @Test
    public void testExternalChanges()
            throws InterruptedException, CouchbaseLiteException {
        final Database db2 = baseTestDb.copy();
        assertNotNull(db2);
        try {
            final CountDownLatch latchDB = new CountDownLatch(1);
            db2.addChangeListener(testSerialExecutor, new DatabaseChangeListener() {
                @Override
                public void changed(@NotNull DatabaseChange change) {
                    assertNotNull(change);
                    assertEquals(10, change.getDocumentIDs().size());
                    assertEquals(1, latchDB.getCount());
                    latchDB.countDown();
                }
            });

            final CountDownLatch latchDoc = new CountDownLatch(1);
            db2.addDocumentChangeListener("doc-6", testSerialExecutor, new DocumentChangeListener() {
                @Override
                public void changed(@NotNull DocumentChange change) {
                    assertNotNull(change);
                    assertEquals("doc-6", change.getDocumentID());
                    Document doc = db2.getDocument(change.getDocumentID());
                    assertEquals("demo", doc.getString("type"));
                    assertEquals(1, latchDoc.getCount());
                    latchDoc.countDown();
                }
            });

            baseTestDb.inBatch(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                        doc.setValue("type", "demo");
                        try {
                            saveDocInBaseTestDb(doc);
                        } catch (CouchbaseLiteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            assertTrue(latchDB.await(5, TimeUnit.SECONDS));
            assertTrue(latchDoc.await(5, TimeUnit.SECONDS));
        } finally {
            db2.close();
        }
    }

    @Test
    public void testAddSameChangeListeners()
            throws InterruptedException, CouchbaseLiteException {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        Document savedDoc1 = saveDocInBaseTestDb(doc1);

        final CountDownLatch latch = new CountDownLatch(5);
        // Add change listeners:
        DocumentChangeListener listener = new DocumentChangeListener() {
            @Override
            public void changed(@NotNull DocumentChange change) {
                if (change.getDocumentID().equals("doc1"))
                    latch.countDown();
            }
        };
        ListenerToken token1 = baseTestDb.addDocumentChangeListener("doc1", listener);
        ListenerToken token2 = baseTestDb.addDocumentChangeListener("doc1", listener);
        ListenerToken token3 = baseTestDb.addDocumentChangeListener("doc1", listener);
        ListenerToken token4 = baseTestDb.addDocumentChangeListener("doc1", listener);
        ListenerToken token5 = baseTestDb.addDocumentChangeListener("doc1", listener);

        try {
            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scott Tiger");
            saveDocInBaseTestDb(doc1);

            // Let's wait for 0.5 seconds:
            assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        } finally {
            baseTestDb.removeChangeListener(token1);
            baseTestDb.removeChangeListener(token2);
            baseTestDb.removeChangeListener(token3);
            baseTestDb.removeChangeListener(token4);
            baseTestDb.removeChangeListener(token5);
        }
    }

    @Test
    public void testRemoveDocumentChangeListener()
            throws InterruptedException, CouchbaseLiteException {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        Document savedDoc1 = saveDocInBaseTestDb(doc1);

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);
        // Add change listeners:
        DocumentChangeListener listener = new DocumentChangeListener() {
            @Override
            public void changed(@NotNull DocumentChange change) {
                if (change.getDocumentID().equals("doc1")) {
                    latch1.countDown();
                    latch2.countDown();
                }
            }
        };
        ListenerToken token = baseTestDb.addDocumentChangeListener("doc1", listener);

        // Update doc1:
        doc1 = savedDoc1.toMutable();
        doc1.setValue("name", "Scott Tiger");
        savedDoc1 = saveDocInBaseTestDb(doc1);

        // Let's wait for 0.5 seconds:
        assertTrue(latch1.await(500, TimeUnit.MILLISECONDS));

        // Remove change listener:
        baseTestDb.removeChangeListener(token);

        // Update doc1:
        doc1 = savedDoc1.toMutable();
        doc1.setValue("name", "Scotty");
        savedDoc1 = saveDocInBaseTestDb(doc1);

        // Let's wait for 0.5 seconds:
        assertFalse(latch2.await(500, TimeUnit.MILLISECONDS));
        assertEquals(1, latch2.getCount());

        // Remove again:
        baseTestDb.removeChangeListener(token);
    }
}
