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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.utils.FileUtils;
import com.couchbase.lite.utils.Fn;
import com.couchbase.lite.utils.Report;
import com.couchbase.lite.utils.TestUtils;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public abstract class BaseTest extends PlatformBaseTest {
    protected static final String TEST_DATE = "2019-02-21T05:37:22.014Z";
    protected static final String BLOB_CONTENT = "Knox on fox in socks in box. Socks on Knox and Knox in box.";


    private static final int BUSY_WAIT_MS = 100;
    private static final int BUSY_RETRIES = 3;

    private final AtomicReference<AssertionError> testFailure = new AtomicReference<>();

    protected ExecutionService.CloseableExecutor testSerialExecutor;

    @AfterClass
    public static void classTearDown() {
        FileUtils.deleteContents(new File(CouchbaseLiteInternal.getDbDirectoryPath()));
        FileUtils.deleteContents(new File(CouchbaseLiteInternal.getTmpDirectoryPath()));
    }

    @Before
    @Override
    public void setUp() throws CouchbaseLiteException {
        super.setUp();

        Database.log.getConsole().setLevel(LogLevel.DEBUG);
        //setupFileLogging(); // if needed

        // reset the directories
        CouchbaseLiteInternal.setupDirectories(null);

        testFailure.set(null);

        testSerialExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();
    }

    @After
    @Override
    public void tearDown() {
        try {
            if (testSerialExecutor != null) { testSerialExecutor.stop(2, TimeUnit.SECONDS); }
        }
        finally { super.tearDown(); }
    }

    protected final String getUniqueName() { return getUniqueName("cbl-test"); }

    protected final String getUniqueName(@NonNull String prefix) { return prefix + '-' + TestUtils.randomString(24); }

    // Prefer this method to any other way of creating a new database
    protected final Database createDb() throws CouchbaseLiteException { return createDb(null); }

    // Prefer this method to any other way of creating a new database
    protected final Database createDb(@Nullable DatabaseConfiguration config) throws CouchbaseLiteException {
        if (config == null) { config = new DatabaseConfiguration(); }
        final String dbName = getUniqueName();
        final File dbDir = new File(config.getDirectory(), dbName + DB_EXTENSION);
        assertFalse(dbDir.exists());
        final Database db = new Database(dbName, config);
        assertTrue(dbDir.exists());
        return db;
    }

    protected final Database duplicateDb(@NonNull Database db) throws CouchbaseLiteException {
        return duplicateDb(db, new DatabaseConfiguration());
    }

    protected final Database duplicateDb(@NonNull Database db, @Nullable DatabaseConfiguration config)
        throws CouchbaseLiteException {
        return new Database(db.getName(), config);
    }

    protected final Database reopenDb(@NonNull Database db) throws CouchbaseLiteException {
        return reopenDb(db, null);
    }

    protected final Database reopenDb(@NonNull Database db, @Nullable DatabaseConfiguration config)
        throws CouchbaseLiteException {
        final String dbName = db.getName();
        assertTrue(closeDb(db));
        return new Database(dbName, (config != null) ? config : new DatabaseConfiguration());
    }

    protected final Database recreateDb(@NonNull Database db) throws CouchbaseLiteException {
        return recreateDb(db, null);
    }

    protected final Database recreateDb(@NonNull Database db, @Nullable DatabaseConfiguration config)
        throws CouchbaseLiteException {
        final String dbName = db.getName();
        assertTrue(deleteDb(db));
        return new Database(dbName, (config != null) ? config : new DatabaseConfiguration());
    }

    protected final boolean closeDb(@Nullable Database db) {
        if ((db == null) || (!db.isOpen())) { return true; }
        return retryWhileBusy("Close db " + db.getName(), db::close);
    }

    protected final boolean deleteDb(@Nullable Database db) {
        if (db == null) { return true; }
        return (db.isOpen())
            ? retryWhileBusy("Delete db " + db.getName(), db::delete)
            : FileUtils.eraseFileOrDir(db.getDbFile());
    }

    protected final void runSafely(Runnable test) {
        try { test.run(); }
        catch (AssertionError failure) {
            Report.log(LogLevel.DEBUG, "Test failed", failure);
            testFailure.compareAndSet(null, failure);
        }
    }

    protected final void runSafelyInThread(CountDownLatch latch, Runnable test) {
        new Thread(() -> {
            try { test.run(); }
            catch (AssertionError failure) {
                Report.log(LogLevel.DEBUG, "Test failed", failure);
                testFailure.compareAndSet(null, failure);
            }
            finally { latch.countDown(); }
        }).start();
    }

    protected final void checkForFailure() {
        AssertionError failure = testFailure.get();
        if (failure != null) { throw new AssertionError(failure); }
    }

    private boolean retryWhileBusy(@NonNull String taskDesc, @NonNull Fn.TaskThrows<CouchbaseLiteException> task) {
        int i = 0;
        while (true) {
            try {
                task.run();
                Report.log(LogLevel.DEBUG, "Succeeded: " + taskDesc);
                return true;
            }
            catch (CouchbaseLiteException ex) {
                if (ex.getCode() != CBLError.Code.BUSY) {
                    Report.log(LogLevel.WARNING, "Failed: " + taskDesc, ex);
                    return false;
                }

                if (i++ >= BUSY_RETRIES) { return false; }

                Report.log(LogLevel.WARNING, "Failed (busy): " + taskDesc);
                try { Thread.sleep(BUSY_WAIT_MS); }
                catch (InterruptedException e) { return false; }
            }
        }
    }
}
