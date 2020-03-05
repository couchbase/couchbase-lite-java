//
// MigrationTest.java
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.utils.ZipUtils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class MigrationTest extends BaseTest {
    private static final String DB_NAME = "android-sqlite";


    private File dbDir;
    private Database migrationTestDb;

    @Before
    @Override
    public void setUp() throws CouchbaseLiteException {
        super.setUp();
        dbDir = new File(getDatabaseDirectoryPath(), getUniqueName());
    }

    @After
    @Override
    public void tearDown() {
        try { deleteDb(migrationTestDb); }
        finally { super.tearDown(); }
    }

    // TODO: 1.x DB's attachment is not automatically detected as blob
    // https://github.com/couchbase/couchbase-lite-android/issues/1237
    @Test
    public void testOpenExistingDBv1x() throws Exception {
        ZipUtils.unzip(getAsset("replacedb/android140-sqlite.cblite2.zip"), dbDir);

        migrationTestDb = openDatabase();
        assertEquals(2, migrationTestDb.getCount());
        for (int i = 1; i <= 2; i++) {
            Document doc = migrationTestDb.getDocument("doc" + i);
            assertNotNull(doc);
            assertEquals(String.valueOf(i), doc.getString("key"));

            Dictionary attachments = doc.getDictionary("_attachments");
            assertNotNull(attachments);
            String key = "attach" + i;

            Blob blob = attachments.getBlob(key);
            assertNotNull(blob);
            byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(attach, blob.getContent());
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1237
    @Test
    public void testOpenExistingDBv1xNoAttachment() throws Exception {
        ZipUtils.unzip(getAsset("replacedb/android140-sqlite-noattachment.cblite2.zip"), dbDir);

        migrationTestDb = openDatabase();
        assertEquals(2, migrationTestDb.getCount());
        for (int i = 1; i <= 2; i++) {
            Document doc = migrationTestDb.getDocument("doc" + i);
            assertNotNull(doc);
            assertEquals(String.valueOf(i), doc.getString("key"));
        }
    }

    @Test
    public void testOpenExistingDB() throws Exception {
        ZipUtils.unzip(getAsset("replacedb/android200-sqlite.cblite2.zip"), dbDir);

        migrationTestDb = openDatabase();
        assertEquals(2, migrationTestDb.getCount());
        for (int i = 1; i <= 2; i++) {
            Document doc = migrationTestDb.getDocument("doc" + i);
            assertNotNull(doc);
            assertEquals(String.valueOf(i), doc.getString("key"));
            Blob blob = doc.getBlob("attach" + i);
            assertNotNull(blob);
            byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(attach, blob.getContent());
        }
    }


    private Database openDatabase() throws IOException, CouchbaseLiteException {
        final DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDirectory(dbDir.getCanonicalPath());
        return new Database(DB_NAME, config);

    }
}
