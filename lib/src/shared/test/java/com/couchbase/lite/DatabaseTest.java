//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.utils.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


// The rules in this test are:
// baseTestDb is managed by the superclass
// If a test opens a new database it guarantee that it is deleted.
// If a test opens a copy of the baseTestDb, it must close (but NOT delete)
public class DatabaseTest extends BaseDbTest {
    private final static String DATABASE_TEST_BLOB = "i'm blob";

    //---------------------------------------------
    //  Get Document
    //---------------------------------------------
    @Test
    public void testGetNonExistingDocWithID() {
        assertNull(baseTestDb.getDocument("non-exist"));
    }

    @Test
    public void testGetExistingDocWithID() throws CouchbaseLiteException {
        final String docID = "doc1";
        createDocInBaseTestDb(docID);
        verifyGetDocument(docID);
    }

    @Test
    public void testGetExistingDocWithIDFromDifferentDBInstance() throws CouchbaseLiteException {
        // store doc
        String docID = "doc1";
        createDocInBaseTestDb(docID);

        // open db with same db name and default option
        Database otherDb = openBaseTestDbCopy(0);
        try {
            assertNotSame(baseTestDb, otherDb);

            // get doc from other DB.
            assertEquals(1, otherDb.getCount());

            verifyGetDocument(otherDb, docID);
        }
        finally {
            closeDb(otherDb);
        }
    }

    @Test
    public void testGetExistingDocWithIDInBatch() throws CouchbaseLiteException {
        final int n = 10;

        // Save 10 docs:
        createDocsInBaseTestDb(n);

        baseTestDb.inBatch(() -> verifyDocuments(n));
    }

    @Test(expected = IllegalStateException.class)
    public void testGetDocFromClosedDB() throws CouchbaseLiteException {
        // Store doc:
        createDocInBaseTestDb("doc1");

        // Close db:
        baseTestDb.close();

        // should fail
        baseTestDb.getDocument("doc1");
    }

    @Test(expected = IllegalStateException.class)
    public void testGetDocFromDeletedDB() throws CouchbaseLiteException {
        // Store doc:
        createDocInBaseTestDb("doc1");

        // Delete db:
        baseTestDb.delete();

        // should fail
        baseTestDb.getDocument("doc1");
    }

    //---------------------------------------------
    //  Save Document
    //---------------------------------------------

    // base test method
    private void testSaveNewDocWithID(String docID) throws CouchbaseLiteException {
        // store doc
        createDocInBaseTestDb(docID);

        assertEquals(1, baseTestDb.getCount());

        // validate document by getDocument
        verifyGetDocument(docID);
    }

    @Test
    public void testSaveNewDocWithID() throws CouchbaseLiteException {
        testSaveNewDocWithID("doc1");
    }

    @Test
    public void testSaveNewDocWithSpecialCharactersDocID() throws CouchbaseLiteException {
        testSaveNewDocWithID("`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'");
    }

    @Test
    public void testSaveAndGetMultipleDocs() throws CouchbaseLiteException {
        final int nDocs = 10; //1000;
        for (int i = 0; i < nDocs; i++) {
            MutableDocument doc = new MutableDocument(String.format(Locale.US, "doc_%03d", i));
            doc.setValue("key", i);
            saveDocInBaseTestDb(doc);
        }
        assertEquals(nDocs, baseTestDb.getCount());
        verifyDocuments(nDocs);
    }

    @Test
    public void testSaveDoc() throws CouchbaseLiteException {
        // store doc
        String docID = "doc1";
        MutableDocument doc = createDocInBaseTestDb(docID).toMutable();

        // update doc
        doc.setValue("key", 2);
        saveDocInBaseTestDb(doc);

        assertEquals(1, baseTestDb.getCount());

        // validate document by getDocument
        verifyGetDocument(docID, 2);
    }

    @Test
    public void testSaveDocInDifferentDBInstance() throws CouchbaseLiteException {
        // Store doc
        String docID = "doc1";
        MutableDocument doc = createDocInBaseTestDb(docID).toMutable();

        // Create db with default
        Database otherDb = openBaseTestDbCopy();
        try {
            assertNotSame(otherDb, baseTestDb);
            assertEquals(1, otherDb.getCount());

            // Update doc & store it into different instance
            doc.setValue("key", 2);
            TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER, () -> otherDb.save(doc));
        }
        finally {
            closeDb(otherDb);
        }
    }

    @Test
    public void testSaveDocInDifferentDB() throws CouchbaseLiteException {
        // Store doc
        String docID = "doc1";
        MutableDocument doc = createDocInBaseTestDb(docID).toMutable();

        // Create db with default
        Database otherDb = openDatabase();
        try {
            assertNotSame(otherDb, baseTestDb);
            assertEquals(0, otherDb.getCount());

            // Update doc & store it into different instance
            doc.setValue("key", 2);

            TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER, () -> otherDb.save(doc));
        }

        finally {
            // delete otherDb
            deleteDb(otherDb);
        }
    }


    @Test
    public void testSaveSameDocTwice() throws CouchbaseLiteException {
        String docID = "doc1";
        MutableDocument doc = createDocInBaseTestDb(docID).toMutable();
        saveDocInBaseTestDb(doc);
        assertEquals(docID, doc.getId());
        assertEquals(1, baseTestDb.getCount());
    }

    @Test
    public void testSaveInBatch() throws CouchbaseLiteException {
        final int nDocs = 10;

        baseTestDb.inBatch(() -> {
            try { createDocsInBaseTestDb(nDocs); }
            catch (CouchbaseLiteException e) { throw new IllegalStateException("Unexpected exception", e); }
        });
        assertEquals(nDocs, baseTestDb.getCount());
        verifyDocuments(nDocs);
    }

    @Test(expected = IllegalStateException.class)
    public void testSaveDocToClosedDB() throws CouchbaseLiteException {
        baseTestDb.close();

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("key", 1);

        saveDocInBaseTestDb(doc);
    }

    @Test(expected = IllegalStateException.class)
    public void testSaveDocToDeletedDB() throws CouchbaseLiteException {
        // Delete db:
        baseTestDb.delete();

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("key", 1);

        saveDocInBaseTestDb(doc);
    }

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------
    @Test
    public void testDeletePreSaveDoc() {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("key", 1);
        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND, () -> baseTestDb.delete(doc));
    }

    @Test
    public void testDeleteDoc() throws CouchbaseLiteException {
        String docID = "doc1";
        Document doc = createDocInBaseTestDb(docID);
        assertEquals(1, baseTestDb.getCount());
        baseTestDb.delete(doc);
        assertEquals(0, baseTestDb.getCount());
        assertNull(baseTestDb.getDocument(docID));
    }

    @Test
    public void testDeleteDocInDifferentDBInstance() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = createDocInBaseTestDb(docID);

        // Create db with same name:
        // Create db with default
        Database otherDb = openBaseTestDbCopy();
        try {
            assertNotSame(otherDb, baseTestDb);
            assertEquals(1, otherDb.getCount());

            // Delete from the different db instance:
            TestUtils.assertThrowsCBL(
                CBLError.Domain.CBLITE,
                CBLError.Code.INVALID_PARAMETER,
                () -> otherDb.delete(doc));
        }
        finally {
            closeDb(otherDb);
        }
    }

    @Test
    public void testDeleteDocInDifferentDB() throws CouchbaseLiteException {
        // Store doc
        String docID = "doc1";
        Document doc = createDocInBaseTestDb(docID);

        // Create db with default
        Database otherDb = openDatabase();
        try {
            assertNotSame(otherDb, baseTestDb);

            // Delete from the different db:
            TestUtils.assertThrowsCBL(
                CBLError.Domain.CBLITE,
                CBLError.Code.INVALID_PARAMETER,
                () -> otherDb.delete(doc));
        }
        finally {
            deleteDb(otherDb);
        }
    }

    @Test
    public void testDeleteDocInBatch() throws CouchbaseLiteException {
        final int nDocs = 10;

        // Save 10 docs:
        createDocsInBaseTestDb(nDocs);

        baseTestDb.inBatch(() -> {
            for (int i = 0; i < nDocs; i++) {
                String docID = String.format(Locale.US, "doc_%03d", i);
                Document doc = baseTestDb.getDocument(docID);
                try { baseTestDb.delete(doc); }
                catch (CouchbaseLiteException e) { throw new IllegalStateException("Unexpected exception", e); }
                assertNull(baseTestDb.getDocument(docID));
                assertEquals((9 - i), baseTestDb.getCount());
            }
        });

        assertEquals(0, baseTestDb.getCount());
    }

    @Test(expected = IllegalStateException.class)
    public void testDeleteDocOnClosedDB() throws CouchbaseLiteException {
        // Store doc:
        Document doc = createDocInBaseTestDb("doc1");

        // Close db:
        baseTestDb.close();

        // Delete doc from db:
        baseTestDb.delete(doc);
    }

    @Test(expected = IllegalStateException.class)
    public void testDeleteDocOnDeletedDB() throws CouchbaseLiteException {
        // Store doc:
        Document doc = createDocInBaseTestDb("doc1");

        baseTestDb.delete();

        // Delete doc from db:
        baseTestDb.delete(doc);
    }

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------
    @Test
    public void testPurgePreSaveDoc() {
        MutableDocument doc = new MutableDocument("doc1");
        assertEquals(1, baseTestDb.getCount());
        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND, () -> baseTestDb.purge(doc));
        assertEquals(0, baseTestDb.getCount());
    }

    @Test
    public void testPurgeDoc() throws CouchbaseLiteException {
        String docID = "doc1";
        Document doc = createDocInBaseTestDb(docID);

        // Purge Doc
        purgeDocAndVerify(doc);
        assertEquals(0, baseTestDb.getCount());
    }

    @Test
    public void testPurgeDocInDifferentDBInstance() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = createDocInBaseTestDb(docID);

        // Create db with default:
        Database otherDb = openBaseTestDbCopy();
        try {
            assertNotSame(otherDb, baseTestDb);
            assertEquals(1, otherDb.getCount());

            // purge document against other db instance:
            TestUtils.assertThrowsCBL(
                CBLError.Domain.CBLITE,
                CBLError.Code.INVALID_PARAMETER,
                () -> otherDb.purge(doc));
        }
        finally {
            closeDb(otherDb);
        }
    }

    @Test
    public void testPurgeDocInDifferentDB() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = createDocInBaseTestDb(docID);

        // Create db with default:
        Database otherDb = openDatabase();
        try {
            assertNotSame(otherDb, baseTestDb);
            assertEquals(0, otherDb.getCount());

            // Purge document against other db:
            TestUtils.assertThrowsCBL(
                CBLError.Domain.CBLITE,
                CBLError.Code.INVALID_PARAMETER,
                () -> otherDb.purge(doc));
        }
        finally {
            deleteDb(otherDb);
        }
    }

    @Test
    public void testPurgeSameDocTwice() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = createDocInBaseTestDb(docID);

        // Get the document for the second purge:
        Document doc1 = baseTestDb.getDocument(docID);

        // Purge the document first time:
        purgeDocAndVerify(doc);
        assertEquals(0, baseTestDb.getCount());

        // Purge the document second time:
        purgeDocAndVerify(doc1);
        assertEquals(0, baseTestDb.getCount());
    }

    @Test
    public void testPurgeDocInBatch() throws CouchbaseLiteException {
        final int nDocs = 10;
        // Save 10 docs:
        createDocsInBaseTestDb(nDocs);

        baseTestDb.inBatch(() -> {
            for (int i = 0; i < nDocs; i++) {
                String docID = String.format(Locale.US, "doc_%03d", i);
                Document doc = baseTestDb.getDocument(docID);
                try { purgeDocAndVerify(doc); }
                catch (CouchbaseLiteException e) { throw new IllegalStateException("Unexpected exception", e); }
                assertEquals((9 - i), baseTestDb.getCount());
            }
        });

        assertEquals(0, baseTestDb.getCount());
    }

    @Test(expected = IllegalStateException.class)
    public void testPurgeDocOnClosedDB() throws CouchbaseLiteException {
        // Store doc:
        Document doc = createDocInBaseTestDb("doc1");

        // Close db:
        baseTestDb.close();

        // Purge doc:
        baseTestDb.purge(doc);
    }

    @Test(expected = IllegalStateException.class)
    public void testPurgeDocOnDeletedDB() throws CouchbaseLiteException {
        // Store doc:
        Document doc = createDocInBaseTestDb("doc1");

        // Close db:
        baseTestDb.close();

        // Purge doc:
        baseTestDb.purge(doc);
    }

    //---------------------------------------------
    //  Close Database
    //---------------------------------------------
    @Test
    public void testClose() throws CouchbaseLiteException {
        baseTestDb.close();
    }

    @Test
    public void testCloseTwice() throws CouchbaseLiteException {
        baseTestDb.close();
        baseTestDb.close();
    }

    @Test
    public void testCloseThenAccessDoc() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        MutableDocument mDoc = new MutableDocument(docID);
        mDoc.setInt("key", 1);

        MutableDictionary mDict = new MutableDictionary(); // nested dictionary
        mDict.setString("hello", "world");
        mDoc.setDictionary("dict", mDict);
        Document doc = saveDocInBaseTestDb(mDoc);

        // Close db:
        baseTestDb.close();

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.getId());
        assertEquals(1, ((Number) doc.getValue("key")).intValue());

        Dictionary dict = doc.getDictionary("dict");
        assertNotNull(dict);
        assertEquals("world", dict.getString("hello"));

        MutableDocument updateDoc = doc.toMutable();
        updateDoc.setValue("key", 2);
        updateDoc.setValue("key1", "value");
        assertEquals(2, updateDoc.getInt("key"));
        assertEquals("value", updateDoc.getString("key1"));
    }

    @Test(expected = IllegalStateException.class)
    public void testCloseThenAccessBlob() throws CouchbaseLiteException {
        // Store doc with blob:
        MutableDocument mDoc = createDocInBaseTestDb("doc1").toMutable();
        mDoc.setValue("blob", new Blob("text/plain", DATABASE_TEST_BLOB.getBytes()));
        Document doc = saveDocInBaseTestDb(mDoc);

        // Close db:
        baseTestDb.close();

        // content should be accessible & modifiable without error
        assertTrue(doc.getValue("blob") instanceof Blob);
        Blob blob = doc.getBlob("blob");
        assertEquals(8, blob.length());

        // trying to get the content, however, should fail
        blob.getContent();
    }

    @Test
    public void testCloseThenGetDatabaseName() throws CouchbaseLiteException {
        final String dbName = baseTestDb.getName();
        baseTestDb.close();
        assertEquals(dbName, baseTestDb.getName());
    }

    @Test
    public void testCloseThenGetDatabasePath() throws CouchbaseLiteException {
        baseTestDb.close();
        assertNull(baseTestDb.getPath());
    }

    @Test(expected = IllegalStateException.class)
    public void testCloseThenCallInBatch() throws CouchbaseLiteException {
        baseTestDb.close();
        baseTestDb.inBatch(Assert::fail);
    }

    @Test
    public void testCloseInInBatch() throws CouchbaseLiteException {
        baseTestDb.inBatch(() -> {
            // delete db
            TestUtils.assertThrowsCBL(
                CBLError.Domain.CBLITE,
                CBLError.Code.TRANSACTION_NOT_CLOSED,
                () -> baseTestDb.close());
        });
    }

    @Test(expected = IllegalStateException.class)
    public void testCloseThenDeleteDatabase() throws CouchbaseLiteException {
        baseTestDb.close();
        baseTestDb.delete();
    }

    //---------------------------------------------
    //  Delete Database
    //---------------------------------------------
    @Test
    public void testDelete() throws CouchbaseLiteException {
        baseTestDb.delete();
    }

    @Test(expected = IllegalStateException.class)
    public void testDeleteTwice() throws CouchbaseLiteException {
        // delete db twice
        File path = new File(baseTestDb.getPath());
        assertTrue(path.exists());

        baseTestDb.delete();
        assertFalse(path.exists());

        // second delete should fail
        baseTestDb.delete();
    }

    @Test
    public void testDeleteThenAccessDoc() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        MutableDocument doc = createDocInBaseTestDb(docID).toMutable();

        // Delete db:
        baseTestDb.delete();

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.getId());
        assertEquals(1, ((Number) doc.getValue("key")).intValue());
        doc.setValue("key", 2);
        doc.setValue("key1", "value");
    }

    @Test
    public void testDeleteThenAccessBlob() throws CouchbaseLiteException {
        // Store doc with blob:
        String docID = "doc1";
        MutableDocument doc = createDocInBaseTestDb(docID).toMutable();
        doc.setValue("blob", new Blob("text/plain", DATABASE_TEST_BLOB.getBytes()));
        saveDocInBaseTestDb(doc);

        // Delete db:
        baseTestDb.delete();

        // content should be accessible & modifiable without error
        Object obj = doc.getValue("blob");
        assertNotNull(obj);

        assertTrue(obj instanceof Blob);
        Blob blob = (Blob) obj;
        assertEquals(8, blob.length());

        // NOTE content still exists in memory for this case.
        assertNotNull(blob.getContent());
    }

    @Test
    public void testDeleteThenGetDatabaseName() throws CouchbaseLiteException {
        final String dbName = baseTestDb.getName();

        // delete db
        baseTestDb.delete();

        assertEquals(dbName, baseTestDb.getName());
    }

    @Test
    public void testDeleteThenGetDatabasePath() throws CouchbaseLiteException {
        // delete db
        baseTestDb.delete();

        assertNull(baseTestDb.getPath());
    }

    @Test(expected = IllegalStateException.class)
    public void testDeleteThenCallInBatch() throws CouchbaseLiteException {
        baseTestDb.delete();
        baseTestDb.inBatch(Assert::fail);
    }

    @Test
    public void testDeleteInInBatch() throws CouchbaseLiteException {
        baseTestDb.inBatch(() -> {
            // delete db
            TestUtils.assertThrowsCBL(
                CBLError.Domain.CBLITE,
                CBLError.Code.TRANSACTION_NOT_CLOSED,
                () -> baseTestDb.close());
        });
    }

    @Test
    public void testDeleteDBOpenedByOtherInstance() throws CouchbaseLiteException {
        Database otherDb = openBaseTestDbCopy(0);
        try {
            assertNotSame(baseTestDb, otherDb);
            // delete db
            TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.BUSY, () -> baseTestDb.delete());
        }
        finally {
            closeDb(otherDb);
        }
    }

    //---------------------------------------------
    //  Delete Database (static)
    //---------------------------------------------

    @Test
    public void testDeleteWithDefaultDirDB() throws CouchbaseLiteException {
        final String dbName = baseTestDb.getName();

        final File dbDir = new File(baseTestDb.getPath());
        try {
            assertNotNull(dbDir);
            assertTrue(dbDir.exists());
            // close db before delete
            baseTestDb.close();

            // Java/Android should not allow null as directory parameter
            try {
                Database.delete(dbName, null);
                fail();
            }
            catch (IllegalArgumentException expected) { }
            assertTrue(dbDir.exists());
        }
        finally {
            Database.delete(dbName, dbDir.getParentFile());
            assertFalse(dbDir.exists());
        }
    }

    @Test
    public void testDeleteOpenDbWithDefaultDir() throws CouchbaseLiteException {
        File path = new File(baseTestDb.getPath());
        assertNotNull(path);
        assertTrue(path.exists());

        Database.delete(baseTestDb.getName(), null);
        assertFalse(path.exists());
    }

    @Test
    public void testStaticDeleteDb() throws CouchbaseLiteException {
        final String uniqueName = getUniqueName();

        final String dbDirPath = getScratchDirectoryPath(uniqueName);
        final File dbDirFile = new File(dbDirPath);

        // create db in a custom directory
        final DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDirectory(dbDirPath);


        final Database db = new Database(uniqueName, config);
        try {
            final File path = new File(db.getPath());
            assertNotNull(path);
            assertTrue(path.exists());

            // close db before delete
            db.close();

            Database.delete(uniqueName, dbDirFile);
            assertFalse(path.exists());
        }
        finally {
            deleteDb(db);
        }
    }

    @Test
    public void testDeleteOpeningDBByStaticMethod() throws CouchbaseLiteException {
        Database db = openBaseTestDbCopy();
        final String dbName = db.getName();
        final File dbDir = db.getFilePath().getParentFile();

        try {
            TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.BUSY, () -> Database.delete(dbName, dbDir));
        }
        finally {
            closeDb(db);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteNonExistingDBWithDefaultDir() throws CouchbaseLiteException {
        Database.delete("notexistdb", baseTestDb.getFilePath());
    }

    @Test
    public void testDeleteNonExistingDB() throws CouchbaseLiteException {
        TestUtils.assertThrowsCBL(
            CBLError.Domain.CBLITE,
            CBLError.Code.NOT_FOUND,
            () -> Database.delete(baseTestDb.getName(), new File(getScratchDirectoryPath("nowhere"))));
    }

    // NOTE: Android/Java does not allow to use null as directory parameters
    @Test(expected = IllegalArgumentException.class)
    public void testDatabaseExistsWithDefaultDir() {
        Database.exists(baseTestDb.getName(), null);
    }

    //---------------------------------------------
    //  Database Existing
    //---------------------------------------------

    @Test
    public void testDatabaseExistsWithDir() throws CouchbaseLiteException {
        final String uniqueName = getUniqueName();

        final String dbDirPath = getScratchDirectoryPath(uniqueName);
        final File dbDir = new File(dbDirPath);
        assertTrue(dbDir.mkdirs());
        assertTrue(dbDir.exists());

        assertFalse(Database.exists(uniqueName, dbDir));

        // create db with custom directory
        final Database db = new Database(uniqueName, new DatabaseConfiguration().setDirectory(dbDirPath));
        final File dbPath = db.getFilePath();
        try {
            assertTrue(Database.exists(uniqueName, dbDir));

            db.close();
            assertTrue(Database.exists(uniqueName, dbDir));

            Database.delete(uniqueName, dbDir);
            assertFalse(Database.exists(uniqueName, dbDir));

            assertFalse(dbPath.exists());
        }
        finally {
            deleteDb(db);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDatabaseExistsAgainstNonExistDBWithDefaultDir() {
        Database.exists("notexistdb", baseTestDb.getFilePath());
    }

    @Test
    public void testDatabaseExistsAgainstNonExistDB() {
        assertFalse(Database.exists(baseTestDb.getName(), new File(getScratchDirectoryPath("nowhere"))));
    }

    @Test
    public void testCompact() throws CouchbaseLiteException {
        final int nDocs = 20;
        final int nUpdates = 25;

        final List<String> docIDs = createDocsInBaseTestDb(nDocs);

        // Update each doc 25 times:
        baseTestDb.inBatch(() -> {
            for (String docID : docIDs) {
                Document savedDoc = baseTestDb.getDocument(docID);
                for (int i = 0; i < nUpdates; i++) {
                    MutableDocument doc = savedDoc.toMutable();
                    doc.setValue("number", i);
                    try { savedDoc = saveDocInBaseTestDb(doc); }
                    catch (CouchbaseLiteException e) { throw new IllegalStateException("Unexpected exception", e); }
                }
            }
        });

        // Add each doc with a blob object:
        for (String docID : docIDs) {
            MutableDocument doc = baseTestDb.getDocument(docID).toMutable();
            doc.setValue("blob", new Blob("text/plain", doc.getId().getBytes()));
            saveDocInBaseTestDb(doc);
        }

        assertEquals(nDocs, baseTestDb.getCount());

        File attsDir = new File(baseTestDb.getPath(), "Attachments");
        assertTrue(attsDir.exists());
        assertTrue(attsDir.isDirectory());
        assertEquals(nDocs, attsDir.listFiles().length);

        // Compact:
        baseTestDb.compact();
        assertEquals(nDocs, attsDir.listFiles().length);

        // Delete all docs:
        for (String docID : docIDs) {
            Document savedDoc = baseTestDb.getDocument(docID);
            baseTestDb.delete(savedDoc);
            assertNull(baseTestDb.getDocument(docID));
        }

        // Compact:
        baseTestDb.compact();
        assertEquals(0, attsDir.listFiles().length);
    }

    // REF: https://github.com/couchbase/couchbase-lite-android/issues/1231
    @Test
    public void testOverwriteDocWithNewDocInstgance() throws CouchbaseLiteException {
        MutableDocument mDoc1 = new MutableDocument("abc");
        mDoc1.setValue("someKey", "someVar");
        Document doc1 = saveDocInBaseTestDb(mDoc1);

        // This cause conflict, DefaultConflictResolver should be applied.
        MutableDocument mDoc2 = new MutableDocument("abc");
        mDoc2.setValue("someKey", "newVar");
        Document doc2 = saveDocInBaseTestDb(mDoc2);

        // NOTE: Both doc1 and doc2 are generation 1. Higher revision one should win
        assertEquals(1, baseTestDb.getCount());
        Document doc = baseTestDb.getDocument("abc");
        assertNotNull(doc);
        // NOTE doc1 -> theirs, doc2 -> mine
        if (doc2.getRevisionID().compareTo(doc1.getRevisionID()) > 0) {
            // mine -> doc 2 win
            assertEquals("newVar", doc.getString("someKey"));
        }
        else {
            // their -> doc 1 win
            assertEquals("someVar", doc.getString("someKey"));
        }
    }

    @Test
    public void testCopy() throws CouchbaseLiteException {
        final int nDocs = 10;
        for (int i = 0; i < nDocs; i++) {
            String docID = "doc_" + i;
            MutableDocument doc = new MutableDocument(docID);
            doc.setValue("name", docID);
            byte[] data = docID.getBytes();
            Blob blob = new Blob("text/plain", data);
            doc.setValue("data", blob);
            saveDocInBaseTestDb(doc);
        }

        final DatabaseConfiguration config = baseTestDb.getConfig();

        String dbName = getUniqueName();

        // Copy:
        Database.copy(new File(baseTestDb.getPath()), dbName, config);

        // Verify:
        assertTrue(Database.exists(dbName, new File(config.getDirectory())));

        Database nudb = new Database(dbName, config);
        try {
            assertNotNull(nudb);
            assertEquals(nDocs, nudb.getCount());

            ResultSet rs = QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(nudb))
                .execute();

            for (Result r : rs) {
                String docID = r.getString(0);
                assertNotNull(docID);

                Document doc = nudb.getDocument(docID);
                assertNotNull(doc);
                assertEquals(docID, doc.getString("name"));

                Blob blob = doc.getBlob("data");
                assertNotNull(blob);

                assertEquals(docID, new String(blob.getContent()));
            }
        }
        finally {
            closeDb(nudb);
        }
    }

    @Test
    public void testCreateIndex() throws CouchbaseLiteException {
        assertEquals(0, baseTestDb.getIndexes().size());

        baseTestDb.createIndex(
            "index1",
            IndexBuilder.valueIndex(
                ValueIndexItem.property("firstName"),
                ValueIndexItem.property("lastName")));
        assertEquals(1, baseTestDb.getIndexes().size());

        // Create FTS index:
        baseTestDb.createIndex("index2", IndexBuilder.fullTextIndex(FullTextIndexItem.property("detail")));
        assertEquals(2, baseTestDb.getIndexes().size());

        baseTestDb.createIndex(
            "index3",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("es-detail")).ignoreAccents(true).setLanguage("es"));
        assertEquals(3, baseTestDb.getIndexes().size());

        // Create value index with expression() instead of property()
        baseTestDb.createIndex(
            "index4",
            IndexBuilder.valueIndex(
                ValueIndexItem.expression(Expression.property("firstName")),
                ValueIndexItem.expression(Expression.property("lastName"))));
        assertEquals(4, baseTestDb.getIndexes().size());

        assertEquals(Arrays.asList("index1", "index2", "index3", "index4"), baseTestDb.getIndexes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexBuilderEmptyArg1() { IndexBuilder.fullTextIndex((FullTextIndexItem[]) null); }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexBuilderEmptyArg2() { IndexBuilder.valueIndex((ValueIndexItem[]) null); }

    @Test
    public void testCreateSameIndexTwice() throws CouchbaseLiteException {
        // Create index with first name:
        ValueIndexItem indexItem = ValueIndexItem.property("firstName");
        Index index = IndexBuilder.valueIndex(indexItem);
        baseTestDb.createIndex("myindex", index);

        // Call create index again:
        baseTestDb.createIndex("myindex", index);

        assertEquals(1, baseTestDb.getIndexes().size());
        assertEquals(Arrays.asList("myindex"), baseTestDb.getIndexes());
    }

    @Test
    public void testCreateSameNameIndexes() throws CouchbaseLiteException {
        ValueIndexItem fNameItem = ValueIndexItem.property("firstName");
        ValueIndexItem lNameItem = ValueIndexItem.property("lastName");
        FullTextIndexItem detailItem = FullTextIndexItem.property("detail");

        // Create value index with first name:
        Index fNameindex = IndexBuilder.valueIndex(fNameItem);
        baseTestDb.createIndex("myindex", fNameindex);

        // Create value index with last name:
        ValueIndex lNameindex = IndexBuilder.valueIndex(lNameItem);
        baseTestDb.createIndex("myindex", lNameindex);

        // Check:
        assertEquals(1, baseTestDb.getIndexes().size());
        assertEquals(Arrays.asList("myindex"), baseTestDb.getIndexes());

        // Create FTS index:
        Index detailIndex = IndexBuilder.fullTextIndex(detailItem);
        baseTestDb.createIndex("myindex", detailIndex);

        // Check:
        assertEquals(1, baseTestDb.getIndexes().size());
        assertEquals(Arrays.asList("myindex"), baseTestDb.getIndexes());
    }

    @Test
    public void testDeleteIndex() throws CouchbaseLiteException {
        testCreateIndex();

        // Delete indexes:

        baseTestDb.deleteIndex("index4");
        assertEquals(3, baseTestDb.getIndexes().size());
        assertEquals(Arrays.asList("index1", "index2", "index3"), baseTestDb.getIndexes());

        baseTestDb.deleteIndex("index1");
        assertEquals(2, baseTestDb.getIndexes().size());
        assertEquals(Arrays.asList("index2", "index3"), baseTestDb.getIndexes());

        baseTestDb.deleteIndex("index2");
        assertEquals(1, baseTestDb.getIndexes().size());
        assertEquals(Arrays.asList("index3"), baseTestDb.getIndexes());

        baseTestDb.deleteIndex("index3");
        assertEquals(0, baseTestDb.getIndexes().size());
        assertTrue(baseTestDb.getIndexes().isEmpty());

        // Delete non existing index:
        baseTestDb.deleteIndex("dummy");

        // Delete deleted indexes:
        baseTestDb.deleteIndex("index1");
        baseTestDb.deleteIndex("index2");
        baseTestDb.deleteIndex("index3");
        baseTestDb.deleteIndex("index4");
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1416
    @Test
    public void testDeleteAndOpenDB() throws CouchbaseLiteException {
        final String dbName = getUniqueName();
        final DatabaseConfiguration config = new DatabaseConfiguration();

        Database database1 = null;
        Database database2 = null;
        try {
            // open a database
            database1 = new Database(dbName, config);

            // delete it
            database1.delete();

            // open it again
            database2 = new Database(dbName, config);

            // inserting documents
            final Database db = database2;
            database2.inBatch(() -> {
                // just create 100 documents
                for (int i = 0; i < 100; i++) {
                    MutableDocument doc = new MutableDocument();

                    // each doc has 10 items
                    doc.setInt("index", i);
                    for (int j = 0; j < 10; j++) { doc.setInt("item_" + j, j); }

                    try { db.save(doc); }
                    catch (CouchbaseLiteException e) { throw new IllegalStateException("Unexpected exception", e); }
                }
            });

            // close db again
            database2.close();
        }
        finally {
            deleteDb(database1);
            deleteDb(database2);
        }
    }

    @Test
    public void testSaveAndUpdateMutableDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        baseTestDb.save(doc);

        // Update:
        doc.setString("lastName", "Tiger");
        baseTestDb.save(doc);

        // Update:
        doc.setLong("age", 20L); // Int vs Long assertEquals can not ignore diff.
        baseTestDb.save(doc);
        assertEquals(3, doc.getSequence());

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Daniel");
        expected.put("lastName", "Tiger");
        expected.put("age", 20L);
        assertEquals(expected, doc.toMap());

        Document savedDoc = baseTestDb.getDocument(doc.getId());
        assertEquals(expected, savedDoc.toMap());
        assertEquals(3, savedDoc.getSequence());
    }

    @Test
    public void testSaveDocWithConflict() throws CouchbaseLiteException {
        testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    @Test
    public void testDeleteDocWithConflict() throws CouchbaseLiteException {
        testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    @Test
    public void testSaveDocWithNoParentConflict() throws CouchbaseLiteException {
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    @Test
    public void testSaveDocWithDeletedConflict() throws CouchbaseLiteException {
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    @Test
    public void testDeleteAndUpdateDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        baseTestDb.save(doc);

        baseTestDb.delete(doc);
        assertEquals(2, doc.getSequence());
        assertNull(baseTestDb.getDocument(doc.getId()));

        doc.setString("firstName", "Scott");
        baseTestDb.save(doc);
        assertEquals(3, doc.getSequence());

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Scott");
        expected.put("lastName", "Tiger");
        assertEquals(expected, doc.toMap());

        Document savedDoc = baseTestDb.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(expected, savedDoc.toMap());
    }

    @Test
    public void testDeleteAlreadyDeletedDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        baseTestDb.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        Document doc1a = baseTestDb.getDocument("doc1");
        MutableDocument doc1b = baseTestDb.getDocument("doc1").toMutable();

        // Delete doc1a:
        baseTestDb.delete(doc1a);
        assertEquals(2, doc1a.getSequence());
        assertNull(baseTestDb.getDocument(doc.getId()));

        // Delete doc1b:
        baseTestDb.delete(doc1b);
        assertEquals(2, doc1b.getSequence());
        assertNull(baseTestDb.getDocument(doc.getId()));
    }

    ///////////////////////////////  H E R E   ! ! ! !

    @Test
    public void testDeleteNonExistingDoc() throws CouchbaseLiteException {
        Document doc1a = createDocInBaseTestDb("doc1");
        Document doc1b = baseTestDb.getDocument("doc1");

        // purge doc
        baseTestDb.purge(doc1a);
        assertEquals(0, baseTestDb.getCount());
        assertNull(baseTestDb.getDocument(doc1a.getId()));

        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND, () -> baseTestDb.delete(doc1a));

        baseTestDb.delete(doc1b);
        assertEquals(0, baseTestDb.getCount());
        assertNull(baseTestDb.getDocument(doc1b.getId()));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1652
    @Test
    public void testDeleteWithOldDocInstance() throws CouchbaseLiteException {
        // 1. save
        MutableDocument mdoc = new MutableDocument("doc");
        mdoc.setBoolean("updated", false);
        baseTestDb.save(mdoc);

        Document doc = baseTestDb.getDocument("doc");

        // 2. update
        mdoc = doc.toMutable();
        mdoc.setBoolean("updated", true);
        baseTestDb.save(mdoc);

        // 3. delete by previously retrieved document
        baseTestDb.delete(doc);
        assertNull(baseTestDb.getDocument("doc"));
    }

    private Database openDatabase() throws CouchbaseLiteException { return verifyDb(createDb()); }

    // helper method to open database
    private Database openBaseTestDbCopy() throws CouchbaseLiteException {
        return openBaseTestDbCopy(-1);
    }

    private Database openBaseTestDbCopy(int count) throws CouchbaseLiteException {
        return reopenDatabase(baseTestDb.getName(), count);
    }

    // This method opens a *named* database.  Use with extreme caution.
    private Database reopenDatabase(String dbName, int count) throws CouchbaseLiteException {
        Database db = verifyDb(new Database(dbName));

        final long actualCount = db.getCount();
        if (count != actualCount) {
            deleteDb(db);
            fail("Unexpected database count: " + count + " <> " + actualCount);
        }

        return db;
    }

    private Database verifyDb(Database db) throws CouchbaseLiteException {
        final String dbName = db.getName();
        try {
            assertNotNull(db);
            assertTrue(new File(db.getPath()).getCanonicalPath().endsWith(".cblite2"));

            return db;
        }
        catch (IOException e) {
            deleteDb(db);
            throw new AssertionError("Unable to get db path", e);
        }
        catch (AssertionError e) {
            deleteDb(db);
            throw e;
        }
    }

    // helper method to save n number of docs
    private List<String> createDocsInBaseTestDb(int n) throws CouchbaseLiteException {
        List<String> docs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            MutableDocument doc = new MutableDocument(String.format(Locale.US, "doc_%03d", i));
            doc.setValue("key", i);
            Document savedDoc = saveDocInBaseTestDb(doc);
            docs.add(savedDoc.getId());
        }
        assertEquals(n, baseTestDb.getCount());
        return docs;
    }

    // helper method to verify n number of docs
    private void verifyDocuments(int n) {
        for (int i = 0; i < n; i++) { verifyGetDocument(String.format(Locale.US, "doc_%03d", i), i); }
    }

    // helper methods to verify getDoc
    private void verifyGetDocument(String docID) { verifyGetDocument(docID, 1); }

    // helper methods to verify getDoc
    private void verifyGetDocument(String docID, int value) { verifyGetDocument(baseTestDb, docID, value); }

    // helper methods to verify getDoc
    private void verifyGetDocument(Database db, String docID) { verifyGetDocument(db, docID, 1); }

    // helper methods to verify getDoc
    private void verifyGetDocument(Database db, String docID, int value) {
        Document doc = db.getDocument(docID);
        assertNotNull(doc);
        assertEquals(docID, doc.getId());
        assertEquals(value, ((Number) doc.getValue("key")).intValue());
    }

    // helper method to purge doc and verify doc.
    private void purgeDocAndVerify(Document doc) throws CouchbaseLiteException {
        String docID = doc.getId();
        baseTestDb.purge(doc);
        assertNull(baseTestDb.getDocument(docID));
    }

    private void testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl cc) throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        baseTestDb.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        MutableDocument doc1a = baseTestDb.getDocument("doc1").toMutable();
        MutableDocument doc1b = baseTestDb.getDocument("doc1").toMutable();

        // Modify doc1a:
        doc1a.setString("firstName", "Scott");
        baseTestDb.save(doc1a);
        doc1a.setString("nickName", "Scotty");
        baseTestDb.save(doc1a);

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Scott");
        expected.put("lastName", "Tiger");
        expected.put("nickName", "Scotty");
        assertEquals(expected, doc1a.toMap());
        assertEquals(3, doc1a.getSequence());

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(baseTestDb.save(doc1b, cc));
            Document savedDoc = baseTestDb.getDocument(doc.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(4, savedDoc.getSequence());
        }
        else {
            assertFalse(baseTestDb.save(doc1b, cc));
            Document savedDoc = baseTestDb.getDocument(doc.getId());
            assertEquals(expected, savedDoc.toMap());
            assertEquals(3, savedDoc.getSequence());
        }

        recreateDB();
    }

    private void testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl cc)
        throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        baseTestDb.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        Document doc1a = baseTestDb.getDocument("doc1");
        MutableDocument doc1b = baseTestDb.getDocument("doc1").toMutable();

        // Delete doc1a:
        baseTestDb.delete(doc1a);
        assertEquals(2, doc1a.getSequence());
        assertNull(baseTestDb.getDocument(doc.getId()));

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(baseTestDb.save(doc1b, cc));
            Document savedDoc = baseTestDb.getDocument(doc.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(3, savedDoc.getSequence());
        }
        else {
            assertFalse(baseTestDb.save(doc1b, cc));
            assertNull(baseTestDb.getDocument(doc.getId()));
        }

        recreateDB();
    }

    private void testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl cc)
        throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setString("firstName", "Daniel");
        doc1a.setString("lastName", "Tiger");
        baseTestDb.save(doc1a);

        Document savedDoc = baseTestDb.getDocument(doc1a.getId());
        assertEquals(doc1a.toMap(), savedDoc.toMap());
        assertEquals(1, savedDoc.getSequence());

        MutableDocument doc1b = new MutableDocument("doc1");
        doc1b.setString("firstName", "Scott");
        doc1b.setString("lastName", "Tiger");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(baseTestDb.save(doc1b, cc));
            savedDoc = baseTestDb.getDocument(doc1b.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(2, savedDoc.getSequence());
        }
        else {
            assertFalse(baseTestDb.save(doc1b, cc));
            savedDoc = baseTestDb.getDocument(doc1b.getId());
            assertEquals(doc1a.toMap(), savedDoc.toMap());
            assertEquals(1, savedDoc.getSequence());
        }

        recreateDB();
    }

    private void testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl cc) throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        baseTestDb.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        MutableDocument doc1a = baseTestDb.getDocument("doc1").toMutable();
        MutableDocument doc1b = baseTestDb.getDocument("doc1").toMutable();

        // Modify doc1a:
        doc1a.setString("firstName", "Scott");
        baseTestDb.save(doc1a);

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Scott");
        expected.put("lastName", "Tiger");
        assertEquals(expected, doc1a.toMap());
        assertEquals(2, doc1a.getSequence());

        // Modify doc1b and delete, result to conflict when delete:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(baseTestDb.delete(doc1b, cc));
            assertEquals(3, doc1b.getSequence());
            assertNull(baseTestDb.getDocument(doc1b.getId()));
        }
        else {
            assertFalse(baseTestDb.delete(doc1b, cc));
            Document savedDoc = baseTestDb.getDocument(doc.getId());
            assertEquals(expected, savedDoc.toMap());
            assertEquals(2, savedDoc.getSequence());
        }

        recreateDB();
    }

}
