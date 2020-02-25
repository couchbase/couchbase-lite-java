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


public abstract class BaseTest extends PlatformBaseTest {
    private static final int BUSY_WAIT_MS = 100;
    private static final int BUSY_RETRIES = 5;

    private final AtomicReference<AssertionError> testFailure = new AtomicReference<>();

    protected ExecutionService.CloseableExecutor testSerialExecutor;

    @BeforeClass
    public static void classSetUp() {
        android.util.Log.d("###", "Before class");
        FileUtils.deleteContents(new File(CouchbaseLiteInternal.getDbDirectoryPath()));
        FileUtils.deleteContents(new File(CouchbaseLiteInternal.getTmpDirectoryPath()));
    }

    @AfterClass
    public static void classTearDown() {
        FileUtils.deleteContents(new File(CouchbaseLiteInternal.getDbDirectoryPath()));
        FileUtils.deleteContents(new File(CouchbaseLiteInternal.getTmpDirectoryPath()));
        android.util.Log.d("###", "After class");
    }

    @Before
    public void setUp() throws CouchbaseLiteException {
        Database.log.getConsole().setLevel(LogLevel.DEBUG);
        //setupFileLogging(); // if needed

        testFailure.set(null);

        testSerialExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();
    }

    @After
    public void tearDown() {
        if (testSerialExecutor != null) { testSerialExecutor.stop(60, TimeUnit.SECONDS); }
    }

    protected final String getUniqueName() { return getUniqueName("cbl-test"); }

    protected final String getUniqueName(@NonNull String prefix) { return prefix + '-' + TestUtils.randomString(12); }

    protected final Database createDb() throws CouchbaseLiteException { return createDb(null); }

    protected final Database createDb(DatabaseConfiguration config) throws CouchbaseLiteException {
        final String dbName = getUniqueName();
        return (config == null) ? new Database(dbName) : new Database(dbName, config);
    }

    protected boolean closeDb(@Nullable Database db) throws CouchbaseLiteException {
        android.util.Log.d("###", "closing db: " + db);
        if ((db == null) || (!db.isOpen())) { return true; }
        boolean ok = retryWhileBusy("Close db " + db.getName(), db::close);
        android.util.Log.d("###", "closed db: " + db);
        return ok;
    }

    protected boolean deleteDb(@Nullable Database db) throws CouchbaseLiteException {
        android.util.Log.d("###", "deleting db: " + db);
        if (db == null) { return true; }
        boolean ok = (db.isOpen())
            ? retryWhileBusy("Delete db " + db.getName(), db::delete)
            : FileUtils.eraseFileOrDir(db.getDbFile());
        android.util.Log.d("###", "deleted db: " + db);
        return ok;
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

    private boolean retryWhileBusy(
        @NonNull String taskDesc,
        @NonNull Fn.TaskThrows<CouchbaseLiteException> task)
        throws CouchbaseLiteException {
        int i = 0;
        while (true) {
            try {
                task.run();
                Report.log(LogLevel.DEBUG, "Succeeded: " + taskDesc);
                return true;
            }
            catch (CouchbaseLiteException ex) {
                if (ex.getCode() != CBLError.Code.BUSY) { throw ex; }

                if (i++ >= BUSY_RETRIES) { return false; }

                Report.log(LogLevel.WARNING, "Failed (busy): " + taskDesc);
                try { Thread.sleep(BUSY_WAIT_MS); }
                catch (InterruptedException e) { return false; }
            }
        }
    }
}
