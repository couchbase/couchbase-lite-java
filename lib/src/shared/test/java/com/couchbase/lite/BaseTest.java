//
// BaseTest.java
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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.utils.FileUtils;
import com.couchbase.lite.utils.Fn;
import com.couchbase.lite.utils.Report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public abstract class BaseTest extends PlatformBaseTest {
    public static final String TEST_DB = "testdb";

    private static final int BUSY_WAIT_MS = 100;
    private static final int BUSY_RETRIES = 5;

    @FunctionalInterface
    public interface DocValidator extends Fn.ConsumerThrows<Document, CouchbaseLiteException> {}

    protected Database db;
    protected ExecutionService.CloseableExecutor executor;

    private AtomicReference<AssertionError> testFailure;
    private File dbDir;

    @Before
    public void setUp() throws CouchbaseLiteException {
        initCouchbaseLite();

        Database.log.getConsole().setLevel(LogLevel.DEBUG);
        //setupFileLogging(); // if needed

        executor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

        testFailure = new AtomicReference<>();

        dbDir = new File(getDatabaseDirectoryPath());
        assertNotNull(dbDir);

        // if database exist, delete it
        deleteDb(TEST_DB, dbDir);

        // clean dbDir
        FileUtils.deleteContents(dbDir);

        db = new Database(TEST_DB);
    }

    @After
    public void tearDown() {
//        try { eraseDatabase(db); }
//        catch (CouchbaseLiteException e) {
//            throw new RuntimeException("Failed closing database: " + TEST_DB, e);
//        }
//        finally {
//            try {
//                if (dbDir != null) { FileUtils.eraseFileOrDir(dbDir); }
//            }
//            finally {
//                stopExecutor();
//            }
//        }
    }

    protected void reopenDB() throws CouchbaseLiteException {
        closeDb(db);
        db = new Database(TEST_DB);
    }

    protected void recreateDB() throws CouchbaseLiteException {
        if (db != null) { db.delete(); }
        db = new Database(TEST_DB);
    }

    protected void deleteDatabase(Database db) throws CouchbaseLiteException {
        deleteDatabase(db.getName(), db.getFilePath().getParentFile());
    }

    protected void deleteDatabase(String dbName) throws CouchbaseLiteException {
        deleteDatabase(dbName, dbDir);
    }

    protected boolean deleteDatabase(String dbName, File dbDir) throws CouchbaseLiteException {
        return deleteDb(dbName, dbDir);
    }

    protected void eraseDatabase(Database db) throws CouchbaseLiteException {
        if (db == null) { return; }

        final String dbName = db.getName();
        final File dbDir = db.getFilePath();

        try {
            if (!closeDb(db)) {
                Report.log(LogLevel.ERROR, "Failed to close db: " + dbName + " @" + dbDir);
            }
        }
        finally {
            if (dbDir != null) {
                try {
                    if (!deleteDb(dbName, dbDir.getParentFile())) {
                        Report.log(LogLevel.ERROR, "Failed to delete db: " + dbName + " @" + dbDir);
                    }
                }
                finally {
                    FileUtils.eraseFileOrDir(dbDir);
                }
            }
        }
    }

    protected void loadJSONResource(String name) throws Exception {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(getAsset(name)))) {
            int n = 1;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) { continue; }

                MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%03d", n++));
                doc.setData(JsonUtils.fromJson(new JSONObject(line)));

                save(doc);
            }
        }
    }

    protected Document generateDocument(String docID) throws CouchbaseLiteException {
        long n = db.getCount();

        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("key", 1);
        save(doc);
        assertTrue((n + 1) == db.getCount());

        Document savedDoc = db.getDocument(docID);
        assertEquals(1, savedDoc.getSequence());

        return savedDoc;
    }

    protected Document save(MutableDocument doc, DocValidator validator) throws CouchbaseLiteException {
        validator.accept(doc);

        Document savedDoc = save(doc);
        validator.accept(doc);
        validator.accept(savedDoc);

        return savedDoc;
    }

    protected Document save(MutableDocument doc) throws CouchbaseLiteException {
        db.save(doc);

        Document savedDoc = db.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(doc.getId(), savedDoc.getId());

        return savedDoc;
    }

    protected void runSafely(Runnable test) {
        try { test.run(); }
        catch (AssertionError failure) {
            Report.log(LogLevel.DEBUG, "Test failed", failure);
            testFailure.compareAndSet(null, failure);
        }
    }

    protected void runSafelyInThread(CountDownLatch latch, Runnable test) {
        new Thread(() -> {
            try { test.run(); }
            catch (AssertionError failure) {
                Report.log(LogLevel.DEBUG, "Test failed", failure);
                testFailure.compareAndSet(null, failure);
            }
            finally { latch.countDown(); }
        }).start();
    }

    protected void checkForFailure() {
        AssertionError failure = testFailure.get();
        if (failure != null) { throw new AssertionError(failure); }
    }

    protected static void expectError(String domain, int code, Fn.TaskThrows<CouchbaseLiteException> task) {
        try {
            task.run();
            fail();
        }
        catch (CouchbaseLiteException e) {
            assertEquals(domain, e.getDomain());
            assertEquals(code, e.getCode());
        }
    }

    private boolean closeDb(Database db) throws CouchbaseLiteException {
        if ((db == null) || (!db.isOpen())) { return true; }

        final String dbName = db.getName();

        int i = 0;
        while (true) {
            try {
                db.close();
                Report.log(LogLevel.VERBOSE, dbName + " was deleted successfully.");
                return true;
            }
            catch (CouchbaseLiteException ex) {
                if (ex.getCode() != CBLError.Code.BUSY) { throw ex; }

                if (i++ >= BUSY_RETRIES) { return false; }

                Report.log(LogLevel.WARNING, dbName + " cannot be closed because it is busy...");
                try { Thread.sleep(BUSY_WAIT_MS); }
                catch (InterruptedException ignore) { return false; }
            }
        }
    }

    private boolean deleteDb(String dbName, File dbDir) throws CouchbaseLiteException {
        // database exist, delete it
        if ((dbDir == null) || !dbDir.exists() || !Database.exists(dbName, dbDir)) { return true; }

        // If a test involves a replicator or a live query,
        // it may take a while for the db to close
        int i = 0;
        while (true) {
            try {
                Database.delete(dbName, dbDir);
                Report.log(LogLevel.VERBOSE, dbName + " was deleted successfully.");
                return true;
            }
            catch (CouchbaseLiteException ex) {
                if (ex.getCode() != CBLError.Code.BUSY) { throw ex; }

                if (i++ >= BUSY_RETRIES) { return false; }

                Report.log(LogLevel.WARNING, dbName + " cannot be deleted because it is busy...");
                try { Thread.sleep(BUSY_WAIT_MS); }
                catch (InterruptedException e) { return false; }
            }
        }
    }

    private void stopExecutor() {
        ExecutionService.CloseableExecutor exec = executor;
        executor = null;
        if (exec != null) { exec.stop(60, TimeUnit.SECONDS); }
    }
}
